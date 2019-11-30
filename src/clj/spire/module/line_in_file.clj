(ns spire.module.line-in-file
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [clojure.string :as string]))

(defn re-pattern-to-sed [re]
  (-> re
      .pattern
      (string/replace "\"" "\"")
      (string/replace "/" "\\/")
      (str "/")
      (->> (str "/"))))


(defn path-escape [path]
  (string/replace path "\"" "\\\""))

(defn double-quote [string]
  (str "\"" string "\""))

(defn path-quote [path]
  (double-quote (path-escape path)))

(defn line-in-file* [path & [{:keys [regexp line state after before]
                               :or {state :present}}]]
  ;; (println "path" path)
  ;; (println "regexp" regexp)
  ;; (println "line" line)
  ;; (println "state" state)
  ;; (println "after" after)
  ;; (println "before" before)

  (assert regexp "missing option :regexp")

  (transport/pipelines
   (fn [_ _ _ session]
     (println (re-pattern-to-sed regexp))
     (println (path-escape path))
     (let [{:keys [out err exit] :as result}
           (ssh/ssh-exec
            session
            (str "sed -n "
                 (double-quote (format "%s=" (re-pattern-to-sed regexp)))
                 " "
                 (path-quote path))
            "" "UTF-8" {})]
       (if (zero? exit)
         (assoc result
                :result :ok
                :out-lines (string/split out #"\n"))
         (assoc result
                :result :failed))))))

(defn line-in-file [& args]
  (binding [state/*form* (concat '(line-in-file) args)]
    (output/print-form state/*form*)
    (apply line-in-file* args)))
