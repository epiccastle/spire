(ns spire.core
  (:require [spire.state]
            [spire.config :as config]
            [spire.output.core :as output]
            [spire.utils :as utils]
            [spire.eval :as eval]
            [spire.state :as state]
            [spire.output.default]
            [spire.output.events]
            [spire.output.quiet]
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
   ["-o" "--output MODULE" "Use the specified output module to print programme progress"]
   ["-v" "--version" "Print the version string and exit"]
   [nil "--debug-ssh" "Debug ssh connections printing to stderr"]])

(defn initialise [{:keys [debug-ssh]}]
  (when debug-ssh
    (JSch/setLogger
     (proxy [Logger] []
       (isEnabled [level]
         true)
       (log [level mesg]
         (binding [*out* *err*]
           (println mesg))))))
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

(defn delay-print
  "Wait for output module to finish printing, then print the value"
  [val]
  ;; TODO - efficiently test end out output module. for now just delay
  (Thread/sleep 100)
  (puget/cprint val))

(defn -main
  [& args]
  (try
    (let [{:keys [options summary arguments]} (cli/parse-opts args cli-options)]

      (sci/binding [state/output-module (clojure.edn/read-string (get options :output ":default"))]
        (initialise options)
        (output/print-thread @state/output-module)

        (cond
          (:help options)
          (println (usage summary))

          (:version options)
          (println "Version:" version)

          (:evaluate options)
          (binding [*file* ""]
            (->> options :evaluate (eval/evaluate args) delay-print))

          (pos? (count arguments))
          (binding [*file* (first arguments)]
            (-> arguments first slurp (->> (eval/evaluate args)) delay-print))

          :else
          (println (usage summary)))))
    (finally
      (shutdown-agents))))
