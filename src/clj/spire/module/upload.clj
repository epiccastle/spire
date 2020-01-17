(ns spire.module.upload
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.local :as local]
            [spire.remote :as remote]
            [spire.compare :as compare]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [{:keys [src content dest
                         owner group mode attrs
                         dir-mode preserve recurse force]
                  :as opts}]
  (cond
    (not (or src content))
    (assoc failed-result
           :exit 3
           :err ":src or :content must be provided")

    (not dest)
    (assoc failed-result
           :exit 3
           :err ":dest must be provided")

    (and src content)
    (assoc failed-result
           :exit 3
           :err ":src and :content cannot both be specified")

    (and preserve (or mode dir-mode))
    (assoc failed-result
           :exit 3
           :err "when providing :preverse you cannot also specify :mode or :dir-mode")

    (and content (utils/content-recursive? content) (not recurse))
    (assoc failed-result
           :exit 3
           :err ":recurse must be true when :content specifies a directory.")

    (and src (utils/content-recursive? (io/file src)) (not recurse))
    (assoc failed-result
           :exit 3
           :err ":recurse must be true when :src specifies a directory.")))

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
  `(let [res# (do ~@body)]
    (if res#
      {:result :changed}
      {:result :ok}))
  #_ (catch Exception e#
    {:result :failed :exception e#}))

(defn process-md5-out
  ([line]
   (process-md5-out (facts/get-fact [:system :os]) line))
  ([os line]
   (cond
     (#{:linux} os)
     (vec (string/split line #"\s+" 2))

     :else
     (let [[_ filename hash] (re-matches #"MD5\s+\((.+)\)\s*=\s*([0-9a-fA-F]+)" line)]
       [hash filename])     )))

(utils/defmodule upload* [{:keys [src content dest
                                  owner group mode attrs
                                  dir-mode preserve recurse force]
                           :as opts}]
  [host-config session]
  (or
   (preflight opts)
   (let [run (fn [command]
               (let [{:keys [out exit err]}
                     (ssh/ssh-exec session command "" "UTF-8" {})]
                 (comment)
                 (println "command:" command)
                 (println "exit:" exit)
                 (println "out:" out)
                 (println "err:" err)
                 (if (zero? exit)
                   (string/trim out)
                   "")))

         ;; analyse local and remote paths
         local-file? (when-not content (local/is-file? src))
         remote-file? (remote/is-file? run dest)

         content (or content (io/file src))

         copied?
         (if recurse
           (let [
                 transfers (compare/compare-full-info (str content) run
                                                      dest
                                                      #_(if local-file?
                                                          dest
                                                          (io/file dest (.getName (io/file (str content))))))
                 {:keys [local local-to-remote identical-content remote]} transfers
                 total-size (->> local-to-remote
                                 (map (comp :size local))
                                 (apply +))
                 max-filename-length (->> local-to-remote
                                          (map (comp count #(.getName %) io/file :filename local))
                                          (apply max 0))

                 identical-content (->> identical-content
                                        (map #(.getPath (io/file src %)))
                                        (into #{}))
                 ]
             (comment)
             (prn "identical:" identical-content)
             (prn "local:" local)
             (prn "remote:" remote)
             (prn "lkeys:" (keys local))

             (cond
               (and remote-file? (not force))
               {:result :failed :err "Cannot copy `content` directory over `dest`: destination is a file. Use :force to delete destination file and replace."}

               (and remote-file? force)
               (do
                 (run (format "rm -f \"%s\"" dest))
                 (scp-result
                  (scp/scp-to session content dest
                              :progress-fn (fn [file bytes total frac context]
                                             (output/print-progress
                                              host-config
                                              (utils/progress-stats
                                               file bytes total frac
                                               total-size
                                               max-filename-length
                                               context)
                                              ))
                              :preserve preserve
                              :dir-mode (or dir-mode 0755)
                              :mode (or mode 0644)
                              :recurse true
                              :skip-files #{}
                              )))

               (not remote-file?)
               (scp-result
                (when (not=
                       (count identical-content)
                       (count (filter #(= :file (:type (second %))) local))
                       )
                  (scp/scp-to session content dest
                              :progress-fn (fn [file bytes total frac context]
                                             (output/print-progress
                                              host-config
                                              (utils/progress-stats
                                               file bytes total frac
                                               total-size
                                               max-filename-length
                                               context)
                                              ))
                              :preserve preserve
                              :dir-mode (or dir-mode 0755)
                              :mode (or mode 0644)
                              :recurse true
                              :skip-files identical-content
                              )))))

           ;; straight single copy
           (let [local-md5 (digest/md5 content)
                 remote-md5 (some-> (facts/on-shell
                                     :csh (run (format "%s \"%s\"" (facts/md5) dest))
                                     :else (run (format "%s \"%s\"" (facts/md5) dest)))
                                    process-md5-out
                                    first)
                 ;; _ (println "l:" local-md5 "r:" remote-md5)
                 ;; _ (do (println "\n\n\n"))
                 ]
             (prn "local-md5:" local-md5)
             (prn "remote-md5:" remote-md5)
             (scp-result
              (when (not= local-md5 remote-md5)
                (scp/scp-to session content dest
                            :progress-fn (fn [file bytes total frac context]
                                           (output/print-progress
                                            host-config
                                            (utils/progress-stats
                                             file bytes total frac
                                             (utils/content-size content)
                                             (count (utils/content-display-name content))
                                             context)
                                            ))
                            :preserve preserve
                            :dir-mode (or dir-mode 0755)
                            :mode (or mode 0644)
                            )))))

         passed-attrs? (or owner group dir-mode mode attrs)

         {:keys [exit err out]} (cond
                                  ;; generally we assume that if a copy happened, all attributes
                                  ;; and modes are correctly setup.
                                  (and (= :ok (:result copied?)) passed-attrs?)
                                  (attrs/set-attrs
                                   session
                                   {:path dest
                                    :owner owner
                                    :group group
                                    :mode mode
                                    :dir-mode dir-mode
                                    :attrs attrs
                                    :recurse recurse})

                                  preserve
                                  (attrs/set-attrs-preserve
                                   session
                                   src
                                   dest))]
     (process-result
      opts
      copied?
      (cond
        (= 0 exit)
        {:result :ok}

        (= 255 exit)
        {:result :changed}

        (nil? exit)
        {:result :ok}

        :else
        {:result :failed
         :exit exit
         :err err
         :out out}))
     )))

(defmacro upload [& args]
  `(utils/wrap-report ~*file* ~&form (upload* ~@args)))

(def documentation
  {
   :module "upload"
   :blurb "upload files to remote systems"
   :description
   [
    "This module uploads files from the local machine to the remote machine."
    "Files are verified with md5 checksums before transfer and files that are already present and intact on the remote machine are not recopied."
    "Use the download module to gather files from remote filesystem to the local machine."]
   :form "(upload options)"
   :args
   [{:arg "options"
     :desc "A hashmap of options. All available option keys and their values are described below"}]
   :opts
   [[:src
     {:description
      ["A path to a file or directory on the local machine that you want to copy to the remote machine."
       "If this option is specified then `:content` cannot be specified."
       "If this path is a directory the `:recurse` must be true."]
      :type :string}]
    [:content
     {:description
      ["Some content to upload to a file. Can be a `String`, a `java.io.File` object or a `byte array`."
       "If this option is specified then `:src` cannot be specified."
       ]
      :type [:string :bytearray :file]}]
    [:dest
     {:description ["The destination path to copy the file or directory into."]
      :type :string}]
    [:owner
     {:description ["Make the destination file or files owned by this user."
                    "Can be specified as a username or as a uid."]
      :type [:integer :string]}]
    [:group
     {:description ["Make the destination file or files owned by this group."
                    "Can be specified as a group name or as a gid."]
      :type [:integer :string]}]
    [:mode
     {:description ["Set the access mode of this file or files."
                    "Can be specified as an octal value of the form 0xxx, as a decimal value, or as a change string as is accepted by the system chmod command (eg. \"u+rwx\")."]
      :type [:integer :string]}]
    [:attrs
     {:description ["Set the file or files special attributes."
                    "Provide as a string that is accepted to the chattr shell command"]
      :type :string}]
    [:dir-mode
     {:description ["When doing a recursive copy, set the access mode of directories to this mode."
                    "Can be specified as an octal value of the form 0xxx, as a decimal value, or as a change string as is accepted by the system chmod command (eg. \"u+rwx\")."]
      :type [:integer :string]}]
    [:preserve
     {:description ["Preserve the access mode and file timestamps of the original source file."]
      :type :boolean}]
    [:recurse
     {:description ["Perform a recursive copy to transfer the entire directory contents"]
      :type :boolean}]
    [:force
     {:description ["Force the copy operation to overwrite other content."
                    "If the destination path already exists as a file, and the upload is to recursively copy a directory, delete the destination file before copying the directory."]
      :type :boolean}]]
   :examples
   [
    {:description "Copy a local file to the destination server"
     :form
     "
(upload {:src \"./project.clj\"
         :dest \"/tmp/project.clj\"
         :owner \"john\"
         :group \"users\"
         :mode 0755
         :dir-mode 0644})"}

    {:description "Recursively copy parent directory to the destination server preserving file permissions"
     :form
     "
(upload {:src \"../\"
         :dest \"/tmp/folder-path\"
         :recurse true
         :preserve true})
"}
    {:description "Alternative way to copy a file to the server"
     :form "
(upload {:content (clojure.java.io/file \"/usr/local/bin/spire\")
         :dest \"/usr/local/bin/\"
         :mode \"a+x\"})
"}
    {:description "Render a template and place it on the server"
     :form "
(upload {:content (selmer \"nginx.conf\" (edn \"webserver.edn\"))
         :dest \"/etc/nginx/nginx.conf\"})
"}
    {:description "Create a binary file on the server from a byte array"
     :form "
(upload {:content (byte-array [0x01 0x02 0x03 0x04])
         :dest \"small.bin\"})
"}



    ]
   })
