(ns spire.module.apt
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]))

;; defaults
(def options-match-choices #{:first :last :all})
(def options-match-default :first)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (apt :install ...)
;;
(defmethod preflight :install [_ _]
  (when-not (facts/get-fact [:paths :apt-get])
    {:exit 1
     :out ""
     :err "apt module requires apt-get installed and present in the path."
     :result :failed}))

(defmethod make-script :install [_ package-or-packages]
  (let [package-string (if (string? package-or-packages)
                         package-or-packages
                         (string/join " " package-or-packages))]
    (str "DEBIAN_FRONTEND=noninteractive apt-get install -y " package-string)))

(defmethod process-result :install
  [_ _ {:keys [out err exit] :as result}]
  (if (zero? exit)
    (let [[_ upgraded installed removed]
          (re-find #"(\d+)\s*\w*\s*upgrade\w*, (\d+)\s*\w*\s*newly instal\w*, (\d+) to remove" out)

          upgraded (Integer/parseInt upgraded)
          installed (Integer/parseInt installed)
          removed (Integer/parseInt removed)
          ]
      (assoc result
             :result (if (and (zero? upgraded)
                              (zero? installed)
                              (zero? removed))
                       :ok
                       :changed)
             :out-lines (string/split out #"\n")
             :packages {:upgraded upgraded
                        :installed installed
                        :removed removed}))
    (assoc result :result :failed)))


;;
;; (apt :update)
;;
(defmethod preflight :update [_ _]
  (when-not (facts/get-fact [:paths :apt-get])
    {:exit 1
     :out ""
     :err "apt module requires apt-get installed and present in the path."
     :result :failed}))

(defmethod make-script :update [_ _]
  (str "DEBIAN_FRONTEND=noninteractive apt-get update -y"))

(defn process-values [result func]
  (->> result
       (map (fn [[k v]] [k (func v)]))
       (into {})))

(defn process-apt-update-line [line]
  (let [[method remain] (string/split line #":" 2)
        method (-> method string/lower-case keyword)
        [url dist component size] (-> remain (string/split #"\s+" 5) rest (->> (into [])))
        size (some->> size (re-find #"\[(.+)\]") second)
        result {:method method
                :url url
                :dist dist
                :component component}]
    (if size
      (assoc result :size size)
      result)))

(defmethod process-result :update
  [_ _ {:keys [out err exit] :as result}]
  (if (zero? exit)
    (let [data (-> out
                   (string/split #"\n")
                   (->>
                    (filter #(re-find #"^\w+:\d+\s+" %))
                    (mapv process-apt-update-line))
                   )
          changed? (some #(= % :get) (map :method data))]
      (assoc result
             :result (if changed? :changed :ok)
             :out-lines (string/split out #"\n")
             :err-lines (string/split err #"\n")
             :update data))
    (assoc result
           :result :failed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )))

(utils/defmodule apt* [command & [opts]]
  [host-string session]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts))))

(defmacro apt [& args]
  `(utils/wrap-report ~*file* ~&form (apt* ~@args)))
