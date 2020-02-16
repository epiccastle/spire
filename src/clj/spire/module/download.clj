(ns spire.module.download
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [spire.local :as local]
            [spire.nio :as nio]
            [spire.remote :as remote]
            [spire.module.attrs :as attrs]
            [spire.compare :as compare]
            [digest :as digest]
            [puget.printer :as puget]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [{:keys [src dest recurse preserve flat dir-mode mode] :as opts}]
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
            :err "when providing :preverse you cannot also specify :mode or :dir-mode")

     (and src (utils/content-recursive? (io/file src)) (not recurse))
     (assoc failed-result
            :exit 3
            :err ":recurse must be true when :src specifies a directory."))))

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
      (dissoc result :result)
      )))

(defmacro scp-result [& body]
  `(try
     (let [res# (do ~@body)]
       (if res#
         {:result :changed}
         {:result :ok}))
     (catch Exception e#
       {:result :failed :exception e#})))

(utils/defmodule download* [{:keys [src dest recurse preserve flat
                                   dir-mode mode owner group attrs] :as opts}]
  [host-config session]
  (or
   (preflight opts)
   (let [run (fn [command]
               (let [{:keys [out exit]}
                     (ssh/ssh-exec session command "" "UTF-8" {})]
                 (when (zero? exit)
                   (string/trim out))))

         ;; analyse local and remote paths
         local-file? (local/is-file? dest)
         remote-file? (remote/is-file? run src)

         destination (if flat (io/file dest) (io/file dest (name (:key host-config))))

         {:keys [remote-to-local identical-content local remote] :as comparison}
         (compare/compare-full-info
          (if remote-file? destination (io/file destination (.getName (io/file src))))
          run src)

         {:keys [sizes total]} (compare/remote-to-local comparison)

         max-filename-length (->> remote-to-local
                                  (map count)
                                  (apply max 0))

         all-files-total total

         progress-fn (fn [file bytes total frac context]
                       (output/print-progress
                        host-config
                        (utils/progress-stats
                         file bytes total frac
                         all-files-total
                         max-filename-length
                         context)
                        ))

         copy-result
         (if recurse
           (cond
             (and local-file? (not force))
             {:result :failed :err "Cannot copy remote `src` over local `dest`: destination is a file. Use :force to delete destination file and replace."}

             (and local-file? force)
             (do
               (.delete (io/file dest))
               (.mkdirs destination)
               (scp-result
                (scp/scp-from session src (str destination)
                              :progress-fn progress-fn
                              :preserve preserve
                              :dir-mode (or dir-mode 0755)
                              :mode (or mode 0644)
                              :recurse true
                              :skip-files #{})))

             (not local-file?)
             (do
               (.mkdirs destination)
               (scp-result
                (when (not=
                       (count identical-content)
                       (count (filter #(= :file (:type (second %))) remote)))
                  (scp/scp-from session src (str destination)
                                :progress-fn progress-fn
                                :preserve preserve
                                :dir-mode (or dir-mode 0755)
                                :mode (or mode 0644)
                                :recurse true
                                :skip-files identical-content
                                )))))

           ;; non recursive
           (let [local-md5sum (get-in local [(.getName (io/file src)) :md5sum])
                 remote-md5sum (get-in remote ["" :md5sum])]
             (.mkdirs destination)
             (scp-result
              ;; (println "--" local remote)
              ;; (println ">>" local-md5sum remote-md5sum)
              (when (not= local-md5sum remote-md5sum)
                (scp/scp-from session src (str destination)
                              :progress-fn progress-fn
                              :preserve preserve
                              :dir-mode (or dir-mode 0755)
                              :mode (or mode 0644)
                              )))))

         passed-attrs? (or owner group dir-mode mode attrs)

         attrs? (cond
                  ;; generally we assume that if a copy happened, all attributes
                  ;; and modes are correctly setup.
                  (and (= :ok (:result copy-result)) passed-attrs?)
                  (do
                    #_ (println ">>>" mode remote-file?
                             (io/file destination (.getName (io/file src))))
                    (nio/set-attrs
                     {:path (io/file destination (.getName (io/file src)))
                      :owner owner
                      :group group
                      :mode mode
                      :dir-mode dir-mode
                      :attrs attrs
                      :recurse recurse}))

                  preserve
                  (nio/set-attrs-preserve
                   remote
                   (if remote-file? destination (io/file destination (.getName (io/file src))))))]
     (process-result
      opts
      copy-result
      {:result (if attrs? :changed :ok)}))))

(defmacro download [& args]
  `(utils/wrap-report ~*file* ~&form (download* ~@args)))

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
     {:description ["A local path to copy the files into"]
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

    [:flat
     {:description ["When set to false (the default) each remote host's files are written into a subdirectory on the client `:dest` that is the name of the host."
                    "When set to true, each host's files are written directly into the destination. Later copies from one host will overwrite earlier copies from another host"]
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
