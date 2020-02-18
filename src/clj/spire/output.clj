(ns spire.output
  (:require [clojure.set :as set]
            [spire.utils :as utils]
            [spire.state :as state]
            [clojure.core.async :refer [<!! put! go chan thread]]))

(defonce state
  (atom []))

(defonce failed-set
  (atom #{}))

(defn up [n]
  (cond
    (pos? n) (do
               (print (str "\033[" n "A"))
               (.flush *out*))
    (neg? n) (do
               (print (str "\033[" (- n) "B"))
               (.flush *out*))))

(defn down [n]
  (up (- n)))

(defn right [n]
  (cond
    (pos? n) (do
               (print (str "\033[" n "C"))
               (.flush *out*))
    (neg? n) (do (print (str "\033[" (- n) "D"))
                 (.flush *out*))))

(defn left [n]
  (right (- n)))

(defn find-forms [s form]
  (filter #(= form (:form %)) s))

(defn find-last-form [s form]
  (last (find-forms s form)))

(defn find-form-indices [s form]
  (->> s
       (map-indexed (fn [n f]
                      (when (= form (:form f)) n)))
       (filter identity)))

(defn find-last-form-index [s form]
  (last (find-form-indices s form)))

(defn find-form-missing-hoststring-indices [s form host-string]
  (let [indices (find-form-indices s form)]
    (->> indices
         (filter (fn [i]
                   (let [returned (into #{} (map :host-string (get-in s [i :results])))]
                     (not (returned host-string))))))))

(defn find-first-form-missing-hoststring-index [s form host-string]
  (first (find-form-missing-hoststring-indices s form host-string)))

#_ (find-last-form-index
    [{:form :a}
     {:form :b}
     {:form :c}
     {:form :b}
     {:form :d}]
    :b)

(def state-change-chan (chan))

(defn calculate-heights [s]
  (let [line-heights (for [l s] (inc (count (:copy-progress l))))
        cumulative-height (reductions + line-heights)
        line-offsets (conj (butlast cumulative-height) 0)
        total-height (last cumulative-height)]
    [line-offsets total-height]))

(defn state-line-complete? [{:keys [results connections]}]
  (= (count results) (count connections)))

(defn cut-trailing-blank-line [s]
  (let [l (count s)]
    (if (zero? l)
      s
      (let [c (subs s (dec l))]
        (if (= "\n" c)
          (subs s 0 (dec l))
          s)))))

#_ (cut-trailing-blank-line "foo bar bard\n")

(defn print-new-failures [results]
  (let [failed (->> results
                    (filter #(= :failed (:result (:result %)))))
        old-failed @failed-set
        new-failed (->> failed
                        (filter #(not (old-failed %))))
        ]
    (swap! failed-set into new-failed)
    (doseq [{:keys [result host-config]} new-failed]
      (println
       (str
        (utils/colour :yellow)
        (utils/escape-codes 40 0 31 1)
        (format "%s failed!%s %s%s exit:%d%s"
                (str (:key host-config))
                (utils/reset)
                (utils/escape-codes 40 0 31 5)
                (:host-string host-config)
                (:exit result)
                (utils/reset))))
      (println "--stdout--")
      (let [trimmed (cut-trailing-blank-line (:out result))]
        (when-not (empty? trimmed)
          (println trimmed)))
      (println "--stderr--")
      (let [trimmed (cut-trailing-blank-line (:err result))]
        (when-not (empty? trimmed)
          (println trimmed)))
      (println "----------"))
    new-failed))

(defn print-state [s]
  (doseq [{:keys [form file meta results copy-progress]} s]
    ;;(prn 'doseq form results copy-progress)
    (let [completed (for [{:keys [host-config result]} results]
                      (str " "
                           (utils/colour
                            (case (:result result)
                              :ok :green
                              :changed :yellow
                              :failed :red
                              :blue))
                           (str (:key host-config))
                           (utils/colour)))
          line (str (format "%s:%d " file (:row meta))
                    (pr-str form)
                    (apply str completed))
          ]
      (println (utils/append-erasure-to-line line)))

    ;; progress bars for this module
    (let [max-host-key-length (when-not (empty? copy-progress)
                                (apply max (map (fn [[h _]] (count (:key h))) copy-progress)))
          max-filename-length (when-not (empty? copy-progress)
                                (apply max (map (fn [[_ v]] (:max-filename-length v)) copy-progress)))
          ]
      (doseq [[host-config progress] copy-progress]
        (println (utils/progress-bar-from-stats (:key host-config) max-host-key-length max-filename-length progress)))))

  (let [just-printed
        (->>
         (for [{:keys [results] :as line} s]
           ;; failure reports for this module
           (print-new-failures results))
         doall
         (filter identity)
         flatten)]
    (when (not (empty? just-printed))
      (print-state s))))

(defn state-change [[o n]]
  (let [[_ old-total-height] (calculate-heights o)
        [_ new-total-height] (calculate-heights n)
        completed-head [] #_ (take-while state-line-complete? o)
        completed-count (count completed-head)
        ]
    (up (- old-total-height completed-count))
    #_ (println 1)
    (print-state n #_(drop completed-count n))
    #_ (println 2)
    (let [lines-lost (- old-total-height new-total-height )]
      (when (pos? lines-lost)
        (dotimes [n lines-lost]
          (println (utils/erase-line)))
        (up lines-lost)))))

(defn print-thread []
  (thread
    (loop []
      (state-change (<!! state-change-chan))
      (recur))))

(defonce state-watcher
  (add-watch
   state :output
   (fn [k a o n]
     (when (not= o n)
       (put! state-change-chan [o n]))
     )))

(defn find-forms-matching-index [forms-vec search]
  (let [search-keys (keys search)]
    (->> forms-vec
         (map-indexed vector)
         (map (fn [[i data]]
                [i data]
                (when (= search (select-keys data search-keys))
                  i)))
         (filter identity))))

(defn print-form [file form file-meta host-config]
  ;;(prn 'print-form form file meta)
  (swap! state
         (fn [s]
           (if-let [match (first (find-forms-matching-index s {:form form :file file :meta file-meta}))]
            s
            (conj s {:form form
                     :file file
                     :meta meta
                     :line (count s)
                     :width (count (pr-str form))
                     :results []})))))

(defn print-result [file form file-meta host-config result]
  (comment
    (prn 'print-result result host-config)
    (prn (find-forms-matching-index @state {:form form :file file :meta meta})))
  (swap! state
         (fn [s]
           (if-let [matching-index (first (find-forms-matching-index s {:form form :file file :meta file-meta}))]
             (update
              s
              matching-index
              (fn [{:keys [width results] :as data}]
                (-> data
                    (update :copy-progress dissoc (:host-string host-config))
                    (assoc
                     :width (+ width (count (:host-string host-config)) 1)
                     :results (conj results
                                    {:result result
                                     :host-config host-config
                                     :pos width
                                     }
                                    )))))

             ;; TODO: this check shouldnt be done here
             ;; in test output handler no key will exist because nothing is stored
             s
             ))))

(defn print-progress [file form form-meta host-string {:keys [progress context] :as data}]
  ;;(prn 'print-progress file form form-meta host-string data)
  (swap! state
         (fn [s]
           (if-let [matching-index (first (find-forms-matching-index s {:form form :file file :meta form-meta}))]
             (update
              s
              matching-index
              (fn [{:keys [width results] :as data}]
                (assoc-in data [:copy-progress host-string] progress)))

             ;; no matching-index. probably a test being run
             s)))
  context)
