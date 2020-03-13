(ns spire.core
  (:require [spire.state]
            [spire.config :as config]
            [spire.output :as output]
            [spire.namespaces :as namespaces]
            [spire.utils :as utils]
            [spire.eval :as eval]
            [puget.printer :as puget]
            [sci.core :as sci]
            [clojure.string :as string]
            [clojure.tools.cli :as cli])
  (:import [com.jcraft.jsch JSch Logger])
  (:gen-class))

(def version (utils/embed ".meta/VERSION"))

(def cli-options
  [
   ["-h" "--help" "Print the command line help"]
   ["-e" "--evaluate CODE" "Evaluate a snipped of code instead of loading code from file"]
   ["-v" "--version" "Print the version string and exit"]])

(def debug false)

(defn initialise []
  (when debug
    (JSch/setLogger
     (proxy [Logger] []
       (isEnabled [level]
         true)
       (log [level mesg]
         (println mesg)))))
  (config/init!)
  (clojure.lang.RT/loadLibrary "spire"))

(defn usage [options-summary]
  (->> ["Pragmatic Provisioning"
        ""
        "Usage: spire [options] FILE"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn remove-shebang [script]
  (if (string/starts-with? script "#!")
    (str "\n" (second (string/split script #"\n" 2)))
    script))

(defn evaluate [args script]
  (sci/binding [eval/context :sci]
    (sci/eval-string
     (remove-shebang script)
     {:namespaces namespaces/namespaces
      :bindings (assoc namespaces/bindings
                       '*command-line-args* (sci/new-dynamic-var '*command-line-args* *command-line-args*))
      :imports {'System 'java.lang.System}
      :classes namespaces/classes})))

(defn -main
  [& args]
  (initialise)
  (output/print-thread)
  (binding [*command-line-args* args]
    (try
      (let [{:keys [options summary arguments]} (cli/parse-opts args cli-options)]
        (cond
          (:help options)
          (println (usage summary))

          (:version options)
          (println "Version:" version)

          (:evaluate options)
          (binding [*file* ""]
            (->> options :evaluate (evaluate args) puget/cprint))

          (pos? (count arguments))
          (binding [*file* (first arguments)]
            (-> arguments first slurp (->> (evaluate args)) puget/cprint))

          :else
          (println (usage summary))))
      (finally
        (shutdown-agents)))))
