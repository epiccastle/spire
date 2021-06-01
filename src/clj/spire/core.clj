(ns spire.core
  (:require [spire.state :as state]
            [spire.config :as config]
            [spire.output.core :as output]
            [spire.utils :as utils]
            [spire.eval :as eval]
            [spire.pod.core :as pod]
            [spire.transport :as transport]
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
   [nil "--help" "Print the command line help"]
   ["-e" "--evaluate CODE" "Evaluate a snippet of code instead of loading code from file"]
   [nil "--output MODULE" "Use the specified output module to print programme progress"]
   [nil "--version" "Print the version string and exit"]
   [nil "--nrepl-server ADDRESS" "Run a spire nrepl server to connect to. Format: IP:PORT or just PORT" ]
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

        (if (System/getenv "BABASHKA_POD")
          (pod/main)

          (cond
            (:help options)
            (println (usage summary))

            (:version options)
            (println "Version:" version)

            (:evaluate options)
            (->> options :evaluate (eval/evaluate args) delay-print)

            (:nrepl-server options)
            (->> options :nrepl-server (eval/nrepl-server args))

            (pos? (count arguments))
            (sci/binding [sci/file (first arguments)]
              (-> arguments first slurp (->> (eval/evaluate args)) delay-print))

            :else
            (println (usage summary))))))
    (finally
      (transport/disconnect-all!)
      (shutdown-agents))))
