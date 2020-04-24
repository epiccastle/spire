(ns spire.module.shell
  (:require [spire.state :as state]
            [spire.facts :as facts]
            [spire.ssh :as ssh]
            [spire.utils :as utils]
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

(utils/defmodule shell* [{:keys [env dir shell out opts cmd creates]
                          :or {env {}
                               dir "."
                               shell "bash"}

                          :as opts}]
  [host-string session {:keys [shell-fn stdin-fn] :as shell-context}]
  (or (preflight opts)
      (let [{:keys [agent-forwarding]} (state/get-host-config)
            {:keys [exit out err] :as result}
            (ssh/ssh-exec session
                          ;; command
                          (shell-fn shell)

                          ;; stdin
                          (stdin-fn
                           (if creates
                             (format "cd \"%s\"\nif [ %s ]; then\nexit 0\nelse\n%s %s\nexit -1\nfi\n"
                                     dir (make-exists-string creates)
                                     (make-env-string env) cmd)
                             (format "cd \"%s\"; %s %s" dir (make-env-string env) cmd)))

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
                 :else :failed)))))

(defmacro shell [& args]
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
     {:description ["Specify the shell to use to run the command. Deafult is `bash`."]
      :type :string
      :required false}]

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
