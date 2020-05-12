(ns spire.eval
  (:require [sci.core :as sci]
            [sci.addons :as addons]
            [sci.impl.opts :as sci-opts]
            [sci-nrepl.server :as sci-nrepl]
            [sci-nrepl.utils :as sci-nrepl-utils]
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
    (.getPath (apply io/file full-path))))

(defn make-target-file-possiblities [target-file]
  (if (.contains target-file "-")
    [target-file (string/replace target-file #"-" "_")]
    [target-file]))

(defn find-file [target-file search-path]
  (let [target-files (make-target-file-possiblities target-file)
        searches (->> search-path
                      (map (fn [path]
                             (map #(io/file path %) target-files)))
                      (apply concat))]
    (->> searches
         (map (fn [f] (when (.isFile f) f)))
         (filter identity)
         first)))

(defn make-search-path [spire-path]
  (let [cwd (utils/current-file-parent)]
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
      (sci/eval-string source sci-opts))))

(defn evaluate [args script]
  (let [env (atom {})
        ctx {:env env
             :namespaces namespaces/namespaces
             :bindings namespaces/bindings
             :imports {'System 'java.lang.System
                       'Thread 'java.lang.Thread}
             :features #{:bb :clj}
             :classes namespaces/classes
             :load-fn load-fn}]
    (sci/binding [context/context :sci]
      (let [ctx (update-in ctx [:namespaces 'clojure.core]
                  assoc
                  'load-file #(load-file* ctx %)
                  '*command-line-args* (sci/new-dynamic-var '*command-line-args* args))
            ctx (addons/future ctx)
            ctx (sci-opts/init ctx)
            ]
        (-> (sci-nrepl/start-server! ctx {:address "127.0.0.1"
                                          :port 1667})
            :future
            deref)
        (sci/eval-string
         (remove-shebang script)
         ctx)))))

(defn nrepl-server [args address]
  (let [nrepl-opts
        (if (.contains address ":")
          (let [[ip port] (string/split address #":")
                port (Integer/parseInt port)]
            {:address ip
             :port port})
          {:port (Integer/parseInt address)})]
    (let [env (atom {})
          ctx {:env env
               :namespaces namespaces/namespaces
               :bindings namespaces/bindings
               :imports {'System 'java.lang.System
                         'Thread 'java.lang.Thread}
               :features #{:bb :clj}
               :classes namespaces/classes
               :load-fn load-fn}]
      (sci/binding [context/context :sci]
        (let [ctx (update-in ctx [:namespaces 'clojure.core]
                             assoc
                             'load-file #(load-file* ctx %)
                             '*command-line-args* (sci/new-dynamic-var '*command-line-args* args))
              ctx (addons/future ctx)
              ctx (sci-opts/init ctx)
              ]
          (-> (sci-nrepl/start-server! ctx nrepl-opts)
              :future
              deref))))))
