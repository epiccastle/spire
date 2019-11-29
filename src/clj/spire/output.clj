(ns spire.output
  (:require [clojure.set :as set]
            [spire.utils :as utils]))

(defonce state
  (atom {}))

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

(defonce state-watcher
  (add-watch
   state :output
   (fn [k a o n]
     (if (not= (count o) (count n))
       (let [new-keys (set/difference (into #{} (keys n)) (into #{} (keys o)))]
         (doseq [form new-keys]
           (prn form)))
       (let [height (->> n vals (map :line) (apply max) inc)]
         (doseq [[k {:keys [line results]}] n]
           (let [old-results (get-in o [k :results])]
             (when (not= (count results) (count old-results))
               (let [new-results (set/difference (into #{} results) (into #{} old-results))]
                 (doseq [{:keys [result string pos]} new-results]
                   (print "\r")
                   (up (- height line))
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
                   (down (- height line))))))))))))

(defn print-form [form]
  (swap! state
         (fn [s]
           (if (s form)
             s
             (assoc s form {:line (count s)
                            :width (count (pr-str form))
                            :results []})))))

(defn print-result [form result string]
  (swap! state
         (fn [s]
           (update s form
                   (fn [{:keys [line width results] :as data}]
                     {:line line
                      :width (+ width (count string) 1)
                      :results (conj results
                                     {:result result
                                      :string string
                                      :pos width})})))))
