(ns spire.output
  (:require [clojure.set :as set]
            [spire.utils :as utils]
            [spire.state :as state]))

(defonce state
  (atom []))

(defn up [n]
  (print (str "\033[" n "A"))
  (.flush *out*))

(defn down [n]
  (print (str "\033[" n "B"))
  (.flush *out*))

(defn right [n]
  (print (str "\033[" n "C"))
  (.flush *out*))

(defn left [n]
  (print (str "\033[" n "D"))
  (.flush *out*))

(defn find-forms [s form]
  (filter #(= form (:form %)) s))

(defn find-last-form [s form]
  (last (find-forms s form)))

(defn find-last-form-index [s form]
  (->> s
       (map-indexed (fn [n f]
                      (when (= form (:form f)) n)))
       (filter identity)
       last))

#_ (find-last-form-index
    [{:form :a}
     {:form :b}
     {:form :c}
     {:form :b}
     {:form :d}]
    :b)

(defonce state-watcher
  (add-watch
   state :output
   (fn [k a o n]
     (if (not= (count o) (count n))
       ;; new form added. print it
       (let [new-forms (subvec n (count o))]
         (doseq [{:keys [form]} new-forms]
           (prn form)))

       ;; adding to existing form
       #_(let [height (count n)]
         (doseq [[new old] (map vector n o)]
           (when (not= (:results new) (:results old))
             (let [new-results (subvec (:results new) (count (:results old)))]
               (doseq [{:keys [result string pos]} new-results]
                 (print "\r")
                 (up (- height (:line new)))
                 (right pos)

                 (print (str " "
                             (utils/colour
                              (case result
                                :ok :green
                                :changed :yellow
                                :failed :red
                                :blue))
                             string
                             (utils/colour)))

                 (print "\r")
                 (.flush *out*)
                 (down (- height (:line new))))))))))))

(defn print-form [form]
  (swap! state
         (fn [s]
           (println "f"
                    (find-last-form s form)
                    (count (get-in s [(find-last-form-index s form) :results]))
                    (get-in s [(find-last-form-index s form) :max-results]))
           (if (and
                (find-last-form s form)
                (< (count (get-in s [(find-last-form-index s form) :results]))
                   (get-in s [(find-last-form-index s form) :max-results])))
             s
             (conj s {:form form
                      :line (count s)
                      :width (count (pr-str form))
                      :max-results (count state/*sessions*)
                      :results []})))))

(defn print-result [form result string]
  (swap! state
         (fn [s]
           (update
            s
            (find-last-form-index s form)
            (fn [{:keys [width results] :as data}]
              (assoc data
                     :width (+ width (count string) 1)
                     :results (conj results
                                    {:result result
                                     :string string
                                     :pos width})))))))
