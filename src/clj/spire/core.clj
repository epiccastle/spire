(ns spire.core
  (:require [spire.state]
            [spire.config :as config]
            [spire.output :as output]
            [spire.namespaces :as namespaces]
            [spire.utils :as utils]
            [puget.printer :as puget]
            [sci.core :as sci]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            )
  (:gen-class))

(def version (utils/embed ".meta/VERSION"))

(def cli-options
  [
   ["-h" "--help" "Print the command line help"]
   ["-e" "--evaluate CODE" "Evaluate a snipped of code instead of loading code from file"]
   ["-v" "--version" "Print the version string and exit"]])

(defn initialise []
  (config/init!)

  ;;(prn "java.library.path:" (System/getProperty "java.library.path"))

  ;;(System/loadLibrary "spire")
  (clojure.lang.RT/loadLibrary "spire")
  )

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
    (second (string/split script #"\n" 2))
    script))

(defn evaluate [args script]
  (sci/eval-string
   (remove-shebang script)
   {:namespaces namespaces/namespaces
    :bindings (assoc namespaces/bindings
                     'argv args
                     'get-argv (fn [] args))
    :imports {'System 'java.lang.System}
    :classes {'java.lang.System System}}))

(defn -main
  [& args]
  (initialise)
  (output/print-thread)
  (let [{:keys [options summary arguments]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (println (usage summary))

      (:version options)
      (println "Version:" version)

      (:server options)
      (doseq [line (line-seq (java.io.BufferedReader. *in*))]
        (try
          (-> line evaluate prn)
          (catch Exception e
            (binding [*out* *err*]
              (prn e)))))


      (:evaluate options)
      (->> options :evaluate (evaluate args) puget/cprint)

      (pos? (count arguments))
      (binding [*file* (first arguments)]
        (-> arguments first slurp (->> (evaluate args)) puget/cprint))

      :else
      ;; repl
      (puget/cprint 0))

    (shutdown-agents)))
