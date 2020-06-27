(ns spire.module.shell
  (:require [spire.state :as state]
            [spire.facts :as facts]
            [spire.remote :as remote]
            [spire.utils :as utils]
            [spire.module.rm :as rm]
            [spire.module.upload :as upload]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [{:keys [shell] :as opts}]
  (facts/check-bins-present #{(keyword (or shell "bash"))}))

(defn process-result [opts result]
  {:result :ok})

(defn make-env-string [env]
  (string/join
   " "
   (for [[k v] env] (format "%s=\"%s\"" (name k) (str v)))))

(defn make-exists-string [files]
  (string/join
   " ] && [ "
   (map (fn [f] (str "-e " (utils/path-quote f))) files)))

#_ (make-exists-string ["privatekey" "public\"key"])

(utils/defmodule shell* [{:keys [env dir shell out opts cmd creates stdin]
                          :or {env {}
                               dir "."
                               shell "bash"}

                          :as opts}]
  [host-string session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (or (preflight opts)
      (let [{:keys [agent-forwarding]} (state/get-host-config)
            shell-path (facts/get-fact [:paths (keyword shell)])
            remote-script-file (remote/make-temp-filename {:prefix "spire-shell-"
                                                           :extension "sh"})
            default-env (if shell-path
                          {:SHELL shell-path}
                          {})
            env-string (->> env
                            (merge default-env)
                            make-env-string)
            script (if creates
                     (format "cd \"%s\"\nif [ %s ]; then\nexit 0\nelse\nexport %s; set -e; %s\nexit -1\nfi\n"
                             dir (make-exists-string creates)
                             env-string cmd)
                     (format "cd \"%s\"; export %s; %s" dir env-string cmd))]

        ;; if stdin is specified we create a remote script to execute
        ;; and pass in out stdin to it. We delete this file after execution
        (when stdin (upload/upload* nil nil nil {:content script :dest remote-script-file}))

        ;; if stdin is not specified, we feed the script directly to the
        ;; process as stdin
        (try
          (let [{:keys [exit out err] :as result}
                (exec-fn session
                         ;; command
                         (shell-fn
                          (if stdin
                            (format "%s %s" shell remote-script-file)
                            shell))

                         ;; stdin
                         (stdin-fn
                          (if stdin
                            stdin
                            script))

                         ;; output format
                         (or out "UTF-8")

                         ;; options
                         (into {:agent-forwarding agent-forwarding}
                               (or opts {})))]
            (assoc result
                   :out-lines (string/split-lines out)

                   :result
                   (cond
                     (zero? exit) :ok
                     (= 255 exit) :changed
                     :else :failed)))
          (finally
            (when stdin (rm/rm* remote-script-file)))
          ))))

(defmacro shell
  "run commands and shell snippets on the remote hosts.
  (shell options)

  given:

  `options`: a hashmap of options, where:

  `:cmd` the command string to execute.

  `:shell` the shell to use to run the command. The default is `bash`

  `:dir` the working directory to run the command within. If a
  relative path, is relative to the users home directory when running
  on a remote system, and relative to the execution that was the
  current working directory when spire was executed.

  `:stdin` supply a string argument to pass to the executing script as
  its standard input

  `:env` a hashmap of environment variables to set before executing
  the command. Environment variable names (the hashmap keys) can be
  keywords or strings.

  `:out` the encoding of the commands output. Default is \"UTF-8\"

  `:creates` a list of filenames that the command will create. If
  these files exist on the machine, the command will not be executed
  and the job result will be reported as `:ok`
  "
  [& args]
  `(utils/wrap-report ~&form (shell* ~@args)))


(def documentation
  {
   :module "shell"
   :blurb "Run commands and shell snippets on the remote hosts"
   :description ["Run executables on the remote hosts"
                 "Run shell scripts on the remote hosts"]
   :form "(shell options)"
   :args
   [{:arg "options"
     :desc "A hashmap of options. All available keys and their values are described below"}]

   :opts
   [
    [:cmd
     {:description ["The command or shell snippet to run"]
      :type :string
      :required true}]

    [:shell
     {:description ["Specify the shell to use to run the command. Default is `bash`."]
      :type :string
      :required false}]

    [:stdin
     {:description ["Supply a string argument to pass to the executing script as its standard input"]
      :type :string
      :required :false}]

    [:dir
     {:description ["Execute the command from within the specified directory."
                    "Path can be relative or absolute."
                    "Relative paths are specified in relation to the users home directory."
                    "Default `:dir` is \".\" (users home directory)."]
      :type :string
      :required false}]

    [:out
     {:description ["Specify the encoding of the commands output."
                    "Default is \"UTF-8\"."]
      :type :string
      :required false}]

    [:env
     {:description ["Specify a hashmap of environment variables to set before executing the command."
                    "Environment variable names can be specified as keywords or strings."]
      :type :hashmap
      :required false}]

    [:creates
     {:description ["Specify a list of files that the command will create."
                    "If these files exist on the remote host, the command will not be executed and the job execution will be marked as `:ok`."]
      :type [:vector :list :lazy-seq]
      :required false}]]})

#_ [env dir shell out opts cmd creates]
