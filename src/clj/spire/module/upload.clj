(ns spire.module.upload
  (:require [spire.output.core :as output]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.local :as local]
            [spire.state :as state]
            [spire.remote :as remote]
            [spire.compare :as compare]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def debug false)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [{:keys [src content dest
                         owner group mode attrs
                         dir-mode preserve recurse force]
                  :as opts}]
  (or
   (facts/check-bins-present #{:scp})
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

(utils/defmodule upload* [source-code-file form form-meta
                          {:keys [src content dest
                                  owner group mode attrs
                                  dir-mode preserve recurse force]
                           :as opts}]
  [host-config session {:keys [exec exec-fn shell-fn stdin-fn] :as shell-context}]
  (or
   (preflight opts)
   (let [run (fn [command]
               (let [{:keys [out exit err]}
                     (exec-fn session (shell-fn "bash") (stdin-fn command) "UTF-8" {})]
                 (when debug
                   (println "-------")
                   (prn 'shell (shell-fn "bash"))
                   (prn 'stdin (stdin-fn command))
                   (prn 'exit exit)
                   (prn 'out out)
                   (prn 'err err))
                 (if (zero? exit)
                   (string/trim out)
                   "")))

         content? content

         ;; analyse local and remote paths
         local-file? (when-not content (local/is-file? src))
         remote-file? (remote/is-file? run dest)
         remote-dir? (remote/is-dir? run dest)

         remote-writable? (if (not (string/ends-with? dest "/"))
                            (remote/is-writable? run (utils/containing-folder dest))
                            (remote/is-writable? run dest))
         content (or content
                     (let [src-file (io/file src)]
                       (if (.isAbsolute src-file)
                         src-file
                         (io/file (utils/current-file-parent) src))))
         ]
     (cond
       (and (not force)
            (or local-file? content?)
            (not (string/ends-with? dest "/"))
            remote-dir?)
       {:result :failed
        :err ":src is a single file while :dest is a folder. Append '/' to dest to write into directory or set :force to true to delete destination folder and write as file."
        :exit 1
        :out ""}

       (not remote-writable?)
       {:result :failed
        :err "destination path unwritable"
        :exit 1
        :out ""}

       :else
       (let [remote-file? (remote/is-file? run dest)
             transfers (compare/compare-full-info (str content) run
                                                  dest
                                                  #_(if local-file?
                                                      dest
                                                      (io/file dest (.getName (io/file (str content))))))

             {:keys [local local-to-remote identical-content remote]} transfers
             total-size (if content?
                          (utils/content-size content)
                          (->> local-to-remote
                               (map (comp :size local))
                               (apply +)))

             max-filename-length (->> local-to-remote
                                      (map (comp count #(.getName %) io/file :filename local))
                                      (apply max 0))

             progress-fn (fn [file bytes total frac context]
                           (output/print-progress
                            @state/output-module
                            source-code-file form form-meta
                            host-config
                            (utils/progress-stats
                             file bytes total frac
                             total-size
                             max-filename-length
                             context)
                            ))

             copied?
             (if recurse
               (let [identical-content (->> identical-content
                                            (map #(.getPath (io/file src %)))
                                            (into #{}))
                     remote-folder-exists? (and (remote "")
                                                (= :dir (:type (remote ""))))
                     ]
                 (comment
                   (prn "identical:" identical-content)
                   (prn "local:" local)
                   (prn "remote:" remote)
                   (prn "lkeys:" (keys local)))

                 (cond
                   (and remote-file? (not force))
                   {:result :failed
                    :err "Cannot copy :src directory over :dest. Destination is a file. Use :force to delete destination file and replace."
                    :exit 1
                    :out ""}

                   (and remote-file? force)
                   (do
                     (run (format "rm -f \"%s\"" dest))
                     (scp-result
                      (scp/scp-to session content dest
                                  :progress-fn progress-fn
                                  :preserve preserve
                                  :dir-mode (or dir-mode 0755)
                                  :mode (or mode 0644)
                                  :recurse true
                                  :skip-files #{}
                                  :exec exec
                                  :exec-fn exec-fn
                                  :shell-fn shell-fn
                                  :stdin-fn stdin-fn
                                  )))

                   (not remote-file?)
                   (scp-result
                    (when (not=
                           (count identical-content)
                           (count (filter #(= :file (:type (second %))) local))
                           )
                      (scp/scp-to session
                                  (if remote-folder-exists?
                                    (mapv #(.getPath %) (.listFiles (io/file content)))
                                    content
                                    )

                                  dest
                                  :progress-fn progress-fn
                                  :preserve preserve
                                  :dir-mode (or dir-mode 0755)
                                  :mode (or mode 0644)
                                  :recurse true
                                  :skip-files identical-content
                                  :exec exec
                                  :exec-fn exec-fn
                                  :shell-fn shell-fn
                                  :stdin-fn stdin-fn

                                  #_:remote-folder-exists
                                  #_(and (remote "")
                                         (= :dir (:type (remote ""))))
                                  )))))

               ;; straight single copy
               (if content?
                 ;; content upload
                 (let [local-md5 (digest/md5 content)
                       remote-md5 (some-> (facts/on-shell
                                           :csh (run (format "%s \"%s\"" (facts/md5) dest))
                                           :else (run (format "%s \"%s\"" (facts/md5) dest)))
                                          process-md5-out
                                          first)
                       ]
                   (scp-result
                    (when (not= local-md5 remote-md5)
                      (scp/scp-content-to session content dest
                                          :progress-fn progress-fn
                                          :preserve preserve
                                          :dir-mode (or dir-mode 0755)
                                          :mode (or mode 0644)
                                          :exec exec
                                          :exec-fn exec-fn
                                          :shell-fn shell-fn
                                          :stdin-fn stdin-fn
                                          ))))

                 ;; file upload
                 (let [local-md5 (digest/md5 content)
                       remote-md5 (some-> (facts/on-shell
                                           :csh (run (format "%s \"%s\"" (facts/md5) dest))
                                           :else (run (format "%s \"%s\"" (facts/md5) dest)))
                                          process-md5-out
                                          first)
                       ;; _ (println "l:" local-md5 "r:" remote-md5)
                       ;; _ (do (println "\n\n\n"))
                       ]
                   (comment (prn "local-md5:" local-md5)
                            (prn "remote-md5:" remote-md5))
                   (scp-result
                    (when (not= local-md5 remote-md5)
                      (scp/scp-to session content dest
                                  :progress-fn progress-fn
                                  :preserve preserve
                                  :dir-mode (or dir-mode 0755)
                                  :mode (or mode 0644)
                                  :exec exec
                                  :exec-fn exec-fn
                                  :shell-fn shell-fn
                                  :stdin-fn stdin-fn
                                  ))))))

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
             :out out}))))
     )))

(defmacro upload
  "transfer files and directories from the local client to the remote
  machines.
  (upload options)

  given:

  `options`: A hashmap of options

  `:src` A path to a file or directory on the local machine

  `:content` Alternatively specify content to upload. Can be a string,
  a `java.io.File` instance or a `byte-array`

  `:dest` A remote path to copy the files into

  `:recurse` If the local path is a directory, recurse through all
  the file and directories

  `:preserve` Preserve the local files' modification flags when
  copying them to the remote filesystem

  `:dir-mode` If `:preserve` is `false`, this specifies the modification
  parameters created directories will have.

  `:mode` If `:preserve` is `false`, this specifies the modification
  parameters of copied files.

  `:owner` If specified the local files and directories will be owned
  by this user.

  `:group` If specified the local files and directories will be owned
  by this group.

  `:force` Force the copy operation to overwrite incompatible
  content. Such as a file upload overwriting a directory or a
  directory copy overwriting a file.

  `:attrs` Set the file or files special attributes. Provided as a
  string that is accepted to the chattr shell command.
  "
  [& args]
  `(utils/wrap-report ~&form (upload* ~(utils/current-file) (quote ~&form) ~(meta &form) ~@args)))

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
