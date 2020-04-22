(ns spire.eval
  (:require [sci.core :as sci]
            [spire.namespaces :as namespaces]
            [spire.context :as context]
            [clojure.string :as string]
            [clojure.java.io :as io]))

(defn remove-shebang [script]
  (if (string/starts-with? script "#!")
    (str "\n" (second (string/split script #"\n" 2)))
    script))

(defn make-target [clj-path]
  (let [path (butlast clj-path)
        filename (str (last clj-path) ".clj")
        full-path (concat path [filename])]
    (apply io/file full-path)))

(defn find-file [target-file search-path]
  (let [searches (->> search-path
                      (map #(io/file % target-file)))]
    (->> searches
         (map (fn [f] (when (.isFile f) f)))
         (filter identity)
         first)))

(defn make-search-path [spire-path]
  (let [cwd (or (some-> *file* io/file .getParent) ".")]
    (if spire-path
      (concat (string/split spire-path #":") [cwd])
      [cwd])))

(defn load-fn [{:keys [namespace]}]
  ;;(prn 'load-fn namespace)
  (let [path (-> namespace
                 str
                 (string/split #"\.")
                 )
        spire-path (System/getenv "SPIREPATH")

        target (make-target path)
        search-path (make-search-path spire-path)
        source-file (find-file target search-path)
        ]
    ;;(prn 'load-fn path source-file)
    (when source-file
      {:file (.getPath source-file)
       :source (str (format "(ns %s)" namespace)
                    (slurp source-file))})))

(defn evaluate [args script]
  (prn 'evaluate script)
  (sci/binding [context/context :sci]
    (sci/eval-string
     (remove-shebang script)
     {:namespaces namespaces/namespaces
      :bindings (assoc namespaces/bindings
                       '*command-line-args*
                       (sci/new-dynamic-var '*command-line-args* args))
      :imports {'System 'java.lang.System
                'Thread 'java.lang.Thread}
      :classes namespaces/classes
      :load-fn load-fn
      })))
