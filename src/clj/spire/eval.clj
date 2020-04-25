(ns spire.eval
  (:require [sci.core :as sci]
            [sci.impl.interpreter :refer [eval-string*]]
            [spire.namespaces :as namespaces]
            [spire.context :as context]
            [spire.utils :as utils]
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

(defn load-fn
  "callback used by sci's require handler to find the source
  for a required namespace. Uses os evironment SPIREPATH to locate
  the sourcefile"
  [{:keys [namespace]}]
  (let [path (-> namespace
                 str
                 (string/split #"\."))
        spire-path (System/getenv "SPIREPATH")
        target (make-target path)
        search-path (make-search-path spire-path)
        source-file (find-file target search-path)]
    (when source-file
      {:file (.getPath source-file)
       :source (str (format "(ns %s)" namespace)
                    (slurp source-file))})))

(defn load-file*
  "loads and evaluates a file, merging its root var defs into the
  present namespace"
  [sci-opts path]
  (let [file-path (io/file (utils/current-file-parent) path)
        source (slurp file-path)]
    (sci/with-bindings {sci/ns @sci/ns
                        sci/file file-path}
      (eval-string* sci-opts source))))

(defn evaluate [args script]
  (let [env (atom {})
        ctx {:env env
             :namespaces namespaces/namespaces
             :bindings namespaces/bindings
             :imports {'System 'java.lang.System
                       'Thread 'java.lang.Thread}
             :classes namespaces/classes
             :load-fn load-fn}]
    (sci/binding [context/context :sci]
      (sci/eval-string
       (remove-shebang script)
       (update-in ctx [:namespaces 'clojure.core]
                  assoc
                  'load-file #(load-file* ctx %)
                  '*command-line-args* (sci/new-dynamic-var '*command-line-args* args))))))
