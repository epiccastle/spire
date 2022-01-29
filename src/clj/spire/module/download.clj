(ns spire.module.download
  (:require [spire.output.core :as output]
            [spire.scp :as scp]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [spire.local :as local]
            [spire.state :as state]
            [spire.nio :as nio]
            [spire.remote :as remote]
            [spire.compare :as compare]
            [spire.context :as context]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def debug false)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [{:keys [src dest recurse preserve dir-mode mode] :as opts}]
  (or
   (facts/check-bins-present #{:scp})
   (cond
     (not src)
     (assoc failed-result
            :exit 3
            :err ":src must be provided")

     (not dest)
     (assoc failed-result
            :exit 3
            :err ":dest must be provided")

     (and preserve (or mode dir-mode))
     (assoc failed-result
            :exit 3
            :err "when providing :preverse you cannot also specify :mode or :dir-mode"))))

(defn process-result [opts copy-result attr-result]
  (let [result {:result :failed
                :attr-result attr-result
                :copy-result copy-result}]
    (cond
      (= :failed (:result copy-result))
      result

      (= :failed (:result attr-result))
      result

      (or (= :changed (:result copy-result))
          (= :changed (:result attr-result)))
      (assoc result :result :changed)

      (and (= :ok (:result copy-result))
           (= :ok (:result attr-result)))
      (assoc result :result :ok)

      :else
      (dissoc result :result))))

(defmacro scp-result [& body]
  `(try
     (let [res# (do ~@body)]
       (if res#
         {:result :changed}
         {:result :ok}))
     (catch Exception e#
       {:result :failed :exception e#})))

(utils/defmodule download* [source-code-file form form-meta
                            {:keys [src dest recurse preserve
                                    dir-mode mode owner group attrs force] :as opts}]
  [host-config session {:keys [exec exec-fn sudo] :as shell-context}]
  (or
   (preflight opts)
   (let [run (fn [command]
               (let [{:keys [out err exit]}
                     (exec-fn session "bash" command "UTF-8" {})]
                 (when debug
                   (println "-------")
                   (prn 'shell "bash")
                   (prn 'stdin command)
                   (prn 'exit exit)
                   (prn 'out out)
                   (prn 'err err))
                 (when (zero? exit)
                   (string/trim out))))

         ;; analyse local and remote paths
         local-file? (local/is-file? dest)
         local-dir? (local/is-dir? dest)
         local-exists? (.exists (io/file dest))
         dest-ends-with-slash? (string/ends-with? dest "/")
         local-writable? (if (not (string/ends-with? dest "/"))
                           (local/is-writable? (utils/containing-folder dest))
                           (local/is-writable? dest))
         remote-readable? (remote/is-readable? run src)
         remote-dir? (remote/is-dir? run src)
         src-name (.getName (io/file src))]
     (cond
       (not remote-readable?)
       {:result :failed
        :err ":src path is unreadable on remote. Check path exists and is readable by user."
        :exit 1
        :out ""
        }

       (and src
            remote-dir?
            (not recurse))
       (assoc failed-result
              :exit 3
              :err ":recurse must be true when :src specifies a directory.")

       (and
        remote-dir?
        (not local-exists?))
       {:result :failed
        :err ":dest path does not exist."
        :exit 1
        :out ""
        }

       (and
        (not force)
        ;; src
        remote-readable?
        (not remote-dir?)

        ;; dest
        local-dir?
        (not dest-ends-with-slash?)
        )
       {:result :failed
        :err ":src is a single file while :dest is a folder. Append '/' to dest to write into directory or set :force to true to delete destination folder and write as file."
        :exit 1
        :out ""
        }

       (and recurse
            remote-dir?
            local-file?
            (not force))
       {:result :failed
        :err "Cannot copy :src directory over :dest. Destination is a file. Use :force to delete destination file and replace."
        :exit 1
        :out ""}

       (and
        (not local-writable?))
       {:result :failed
        :err "destination path :dest is unwritable"
        :exit 1
        :out ""}

       (and recurse local-file? (not force))
       {:result :failed :err "Cannot copy remote `src` over local `dest`: destination is a file. Use :force to delete destination file and replace."}

       :else
       (let [remote-file? (remote/is-file? run src)
             _ (when (and local-file? force)
                 (.delete (io/file dest)))

             destination (if dest-ends-with-slash?
                           (io/file dest src-name)
                           (io/file dest))
             {:keys [remote-to-local identical-content local remote] :as comparison}
             (compare/compare-full-info (.getPath destination) run src)

             ;; files to transfer
             {:keys [sizes total]} (compare/remote-to-local comparison)

             ;; dirs to transfer (preserve empties)
             dirs-structure-remote (compare/remote-to-local-dirs comparison)
             dirs-structure-local (->> comparison
                                       :local
                                       (filter (fn [[_ {:keys [type]}]] (= :dir type)))
                                       (map first)
                                       (into #{}))

             max-filename-length (->> remote-to-local
                                      (map (comp count #(.getName %) io/file :filename remote))
                                      (apply max 0))

             all-files-total total

             progress-fn (fn [file bytes total frac context]
                           (output/print-progress
                            (context/deref* state/output-module)
                            source-code-file form form-meta
                            host-config
                            (utils/progress-stats
                             file bytes total frac
                             all-files-total
                             max-filename-length
                             context)))


             copy-result
             (if recurse
               (cond
                 dest-ends-with-slash?
                 (do
                   (doseq [[path {:keys [type mode-string mode last-access last-modified]}]
                           dirs-structure-remote]
                     (.mkdirs (io/file destination path)))
                   (scp-result
                    (or (when (not=
                               (count identical-content)
                               (count (filter #(= :file (:type (second %))) remote)))
                          (scp/scp-from session src (str dest)
                                        :progress-fn progress-fn
                                        :preserve preserve
                                        :dir-mode (or dir-mode 0755)
                                        :mode (or mode 0644)
                                        :recurse true
                                        :skip-files identical-content
                                        :exec exec
                                        :exec-fn exec-fn
                                        :sudo sudo))
                        (not (empty?
                              (filter #(not (dirs-structure-local (first %))) dirs-structure-remote))))))

                 (not dest-ends-with-slash?)
                 ;; copy remote folder locally _as_ the destination name
                 ;; ie. the contents of remote folder become the contents of
                 ;; the directory listed in dest.
                 (do
                   (doseq [[path {:keys [type mode-string mode last-access last-modified]}]
                           dirs-structure-remote]
                     (.mkdirs (io/file destination path)))
                   (let [{:keys [remote-to-local]} comparison
                         base-dir-copy-set
                         (->> remote-to-local
                              (map #(str src "/" (first (string/split % #"/"))))
                              (into #{})
                              (into []))]
                     (scp-result
                      (or (when (not (empty? base-dir-copy-set))
                            (scp/scp-from session base-dir-copy-set (str destination)
                                          :progress-fn progress-fn
                                          :preserve preserve
                                          :dir-mode (or dir-mode 0755)
                                          :mode (or mode 0644)
                                          :recurse true
                                          :skip-files identical-content
                                          :exec exec
                                          :exec-fn exec-fn
                                          :sudo sudo))
                          (not (empty?
                                (filter #(not (dirs-structure-local (first %))) dirs-structure-remote))))))))

               ;; non recursive
               (let [local-md5sum (get-in local ["" :md5sum])
                     remote-md5sum (get-in remote ["" :md5sum])]
                 (scp-result
                  (when (not= local-md5sum remote-md5sum)
                    (scp/scp-from session src (str destination)
                                  :progress-fn progress-fn
                                  :preserve preserve
                                  :dir-mode (or dir-mode 0755)
                                  :mode (or mode 0644)
                                  :exec exec
                                  :exec-fn exec-fn
                                  :sudo sudo)))))

             passed-attrs? (or owner group dir-mode mode attrs)

             path (if (or
                       ;; recursive dir copy
                       (and recurse (not local-file?))

                       ;;single file into dir
                       (and remote-file? dest-ends-with-slash?))
                    (io/file destination)
                    (io/file destination (.getName (io/file src))))

             attrs? (cond
                      ;; generally we assume that if a copy happened, all attributes
                      ;; and modes are correctly setup.
                      (and (= :ok (:result copy-result)) passed-attrs?)
                      (nio/set-attrs
                       {:path path
                        :owner owner
                        :group group
                        :mode mode
                        :dir-mode dir-mode
                        :attrs attrs
                        :recurse recurse})

                      preserve
                      (nio/set-attrs-preserve
                       remote
                       (if remote-file? destination path)))]

         (process-result
          opts
          copy-result
          {:result (if attrs? :changed :ok)}))))))

(defmacro download
  "Transfer files and directories from the remote machines to the local
  client.
  (download options)

  given:

  `options`: A hashmap of options

  `:src` A path to a file or directory on the remote hosts

  `:dest` A local path to copy the files into

  `:recurse` If the remote path is a directory, recurse through all
  the file and directories

  `:preserve` Preserve the remote files' modification flags when
  copying them to the local filesystem

  `:dir-mode` If `:preserve` is `false`, this specifies the modification
  parameters created directories will have.

  `:mode` If `:preserve` is `false`, this specifies the modification
  parameters of copied files.

  `:owner` If specified the local files and directories will be owned
  by this user.

  `:group` If specified the local files and directories will be owned
  by this group.
  "
  [& args]
  `(utils/wrap-report ~&form (download* ~(utils/current-file) (quote ~&form) ~(meta &form) ~@args)))

(def documentation
  {
   :module "download"
   :blurb "Transfer files and directories from the remote machines to the local client."
   :description
   [
    "This module downloads files from the remote machines"]
   :form "(download options)"
   :args
   [{:arg "options"
     :desc "A hashmap of options. All available keys and their values are described below"}]

   :opts
   [
    [:src
     {:description ["A path to a file or directory on the remote hosts"]
      :type :string
      :required true}]

    [:dest
     {:description ["A local path to copy the files into."
                    "If :recurse is true and :dest path ends in a slash; indicates that destination is a directory and the contents of :src are to be copied into the directory."
                    "If :recurse is true and :dest path does not end in a slash; indicates that the :src directory is to be copies as the specified path."]
      :type :string
      :required true}]

    [:recurse
     {:description ["If the remote path is a directory, recurse through all the files and directories"]
      :type :boolean
      :required false}]

    [:preserve
     {:description ["Preserve the remote files' modification flags when copying them to the local filesystem"]
      :type :boolean
      :required false}]

    [:dir-mode
     {:description ["If `:preserve` is false, this parameter specifies the file modification mode that created directories on the client will have"]
      :type :integer
      :required false}]

    [:mode
     {:description ["If `:preserve` is false, this parameter specifies the file modification mode that copied files on the client will have"]
      :type :integer
      :required false}]

    [:owner
     {:description ["If specified the local files and directories will be owned by this user"]
      :type :string
      :required false}]

    [:group
     {:description ["If specified the local files and directories will be owned by this group"]
      :type :string
      :required false}]

    #_
    [:attrs
     {:description ["If specified the local files and directories will have the specified attrs."]
      :type :string
      :required false
      }]
    ]})
