(ns spire.core
  (:require [spire.config :as config]
            [spire.namespaces :as namespaces]
            [puget.printer :as puget]
            [sci.core :as sci]
            [clojure.string :as string]
            [clojure.tools.cli :as cli])
  (:gen-class))

(defmacro embed [filename]
  (slurp filename))

(def version (embed ".meta/VERSION"))

(def cli-options
  [
   ["-h" "--help" "Print the command line help"]
   ["-v" "--version" "Print the version string and exit"]
   ["-e" "--evaluate CODE" "Evaluate the code passed in on the command line" ]
   [nil "--server" "Run in server mode on the other end of the connection"]])

(defn initialise []
  (config/init!)
  (System/loadLibrary "spire"))

(defn usage [options-summary]
  (->> ["Pragmatic Provisioning"
        ""
        "Usage: spire [options] username@hostname"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))


(defn evaluate [script]
  (sci/eval-string script {:namespaces namespaces/namespaces
                           :bindings namespaces/bindings}))

(defn -main
  [& args]
  (initialise)
  (let [{:keys [options summary arguments]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (println (usage summary))

      (:version options)
      (println "Version:" version)

      (:server options)
      (do
        (doseq [line (line-seq (java.io.BufferedReader. *in*))]
          (try
            (-> line evaluate prn)
            (catch Exception e
              (binding [*out* *err*]
                (prn e)))))
        (shutdown-agents))

      (:evaluate options)
      (->> options :evaluate evaluate puget/cprint)

      (pos? (count arguments))
      (-> arguments first slurp evaluate puget/cprint)

      :else
      ;; repl
      (puget/cprint 0))))
