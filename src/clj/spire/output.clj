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
       (let [height (count n)]
         (doseq [[new old] (map vector n o)]
           (when (not= (:results new) (:results old))
             (let [new-results (subvec (:results new) (count (:results old)))]
               ;;(println "new-results:" new-results)
               (doseq [{:keys [result host-string pos]} new-results]
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
                             host-string
                             (utils/colour)))

                 (print "\r")
                 (.flush *out*)
                 (down (- height (:line new))))))))))))

(defn print-form [form]
  (swap! state
         (fn [s]
           (if
             (->> state/*sessions*
                  (map #(find-first-form-missing-hoststring-index s form %))
                  (every? nil?)
                  not)
             s
             (conj s {:form form
                      :line (count s)
                      :width (count (pr-str form))
                      :connections state/*connections*
                      :results []})))))

(defn print-result [form result host-string]
  (swap! state
         (fn [s]
           ;; (println "1" s)
           ;; (println "c" form result host-string)
           ;; (println "f" (find-first-form-missing-hoststring-index s form host-string))
           (update
            s
            (find-first-form-missing-hoststring-index s form host-string)
            (fn [{:keys [width results] :as data}]
              ;;(println "assoc:" data)
              (assoc data
                     :width (+ width (count host-string) 1)
                     :results (conj results
                                    {:result result
                                     :host-string host-string
                                     :pos width})))))))
