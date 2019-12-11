(ns spire.output
  (:require [clojure.set :as set]
            [spire.utils :as utils]
            [spire.state :as state]
            [clojure.core.async :refer [<!! put! go chan thread]]))

(defonce state
  (atom []))

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

(defn print-state [s]
  (doseq [{:keys [form results copy-progress]} s]
    (let [completed (for [{:keys [host-string result]} results]
                      (str " "
                           (utils/colour
                            (case result
                              :ok :green
                              :changed :yellow
                              :failed :red
                              :blue))
                           host-string
                           (utils/colour)))
          line (str (pr-str form) (apply str completed))
          ]
      (println (utils/append-erasure-to-line line)))
    (let [max-host-string-length (when-not (empty? copy-progress)
                                   (apply max (map (fn [[h _]] (count h)) copy-progress)))
          max-filename-length (when-not (empty? copy-progress)
                                   (apply max (map (fn [[_ v]] (:max-filename-length v)) copy-progress)))
          ]
      (doseq [[host-string progress] copy-progress]
        (println (utils/progress-bar-from-stats host-string max-host-string-length max-filename-length progress))))
    )
  )

(defn state-change [[o n]]
  (let [[_ old-total-height] (calculate-heights o)
        [_ new-total-height] (calculate-heights n)
        completed-head (take-while state-line-complete? o)
        completed-count (count completed-head)
        ]
    (up (- old-total-height completed-count))
    (print-state (drop completed-count n))
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

(defn print-form [form]
  (swap! state
         (fn [s]
           (if
             (->> state/*sessions*
                  (map #(find-first-form-missing-hoststring-index s form %))
                  (every? nil?)
                  not)
             s
             (let [cumulative-widths (concat [0] (reductions + (map (comp inc count) (butlast state/*connections*))))
                   form-width (count (pr-str form))
                   offsets (map #(+ % form-width) cumulative-widths)
                   host-positions (->> (map vector state/*connections* offsets)
                                       (into {}))]
               (conj s {:form form
                        :line (count s)
                        :width (count (pr-str form))
                        :positions host-positions
                        :connections state/*connections*
                        :results []}))))))

(defn print-result [result host-string]
  (swap! state
         (fn [s]
           (update
            s
            (find-first-form-missing-hoststring-index s state/*form* host-string)
            (fn [{:keys [width positions results] :as data}]
              (-> data
                  (update :copy-progress dissoc host-string)
                  (assoc
                   :width (+ width (count host-string) 1)
                   :results (conj results
                                  {:result result
                                   :host-string host-string
                                   :pos width
                                   ;;:pos (positions host-string)
                                   }
                                  ))))))))

(defn print-progress [host-string progress-args file-sizes]
  ;; (println progress-args)
  ;; (println file-sizes)
  ;; (println)
  (let [{:keys [progress context]} (apply utils/progress-stats progress-args)]
    (swap! state
           (fn [s]
             (update
              s
              (find-first-form-missing-hoststring-index s state/*form* host-string)
              (fn [{:keys [width results] :as data}]
                (assoc-in data [:copy-progress host-string] progress)))))
    context))
