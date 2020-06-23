(ns spire.output.default
  (:require [spire.utils :as utils]
            [spire.output.core :as output]
            [puget.printer :as puget]
            [clojure.core.async :refer [<!! put! chan thread]]))


(set! *warn-on-reflection* true)

(defonce state
  (atom {:log []
         :failed #{}
         :debug #{}
         }))

;; remember the state of those that have failed so we don't print twice
(defonce failed-set
  (atom #{}))

;; remember the state of those that have print debug so we don't print twice
(defonce debug-set
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

(defn print-failure [{:keys [result host-config]}]
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

(defn print-debug [[file form {:keys [line]} {:keys [key]} result]]
  (let [str-line (str "--------- " file ":" line " " key " " form " ---------")]
    (println
     (str
      (utils/colour :blue)
      str-line
      (utils/reset)))
    (puget/cprint result)
    (println
     (str
      (utils/colour :blue)
      (apply str (take (count str-line) (repeat "-")))
      (utils/reset)))))

(defn print-state [o s]
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
          line (str (format "%s:%d " file (:line meta))
                    (pr-str form)
                    (apply str completed))
          ]
      (println (utils/append-erasure-to-line line)))

    ;; progress bars for this module
    (let [max-host-key-length (when-not (empty? copy-progress)
                                (apply max (map (fn [[h _]] (count (str (:key h)))) copy-progress)))
          max-filename-length (when-not (empty? copy-progress)
                                (apply max (map (fn [[_ v]] (:max-filename-length v)) copy-progress)))
          ]
      (doseq [[host-config progress] copy-progress]
        (println (utils/progress-bar-from-stats (str (:key host-config)) max-host-key-length max-filename-length progress)))))

  #_ (let [just-printed
        (->>
         (for [{:keys [results] :as line} s]
           ;; failure reports for this module
           (print-new-failures results))
         doall
         (filter identity)
         flatten)]
    (let [debug-entries (first (reset-vals! debug-set #{}))]
      (doall
       (for [entry debug-entries]
         (print-debug entry)))

      ;; reprint all output state at end


      #_ (when (or (not (empty? just-printed))
                   (not (empty? debug-entries)))
           (print-state s)
           #_ (reset! state [])
           )))

  )

(defn state-change-old [[o n]]
  (let [o (:log o)
        n (:log n)
        [_ old-total-height] (calculate-heights o)
        [_ new-total-height] (calculate-heights n)
        completed-head [] #_ (take-while state-line-complete? o)
        completed-count (count completed-head)
        ]
    #_ (up (- old-total-height completed-count))
    (println "old:" old-total-height)
    (clojure.pprint/pprint o)
    (println "new:" new-total-height)
    (clojure.pprint/pprint n)
    (println "completed:" completed-count)
    (println "up:" (- old-total-height completed-count))
    (print-state n #_(drop completed-count n))
    #_ (println 2)
    (let [lines-lost (- old-total-height new-total-height )]
      (when (pos? lines-lost)
        (dotimes [n lines-lost]
          (println (utils/erase-line)))
        (print "up lines-lost:" lines-lost)
        #_(up lines-lost)))))

;; which lines are immediately accessible above the cursor position
;; that we can move the cursor to to rewrite
(defonce accessible-lines
  (atom []))

(defn update-accessible-line-count [s uform ufile umeta uline line-count]
  (let [before s
        after
        (->> s
             (mapv (fn [{:keys [form file meta line] :as data}]
                     (if (and (= form uform)
                              (= file ufile)
                              (= meta umeta)
                              (= line uline))
                       (assoc data :line-count line-count)
                       data))))]
    ;; (prn 'update-accessible-line-count 'before)
    ;; (clojure.pprint/pprint before)
    ;; (prn 'update-accessible-line-count 'after)
    ;; (clojure.pprint/pprint after)
    after
    ))

(defn state-change [[o n]]
  ;; (println "OLD")
  ;; (clojure.pprint/pprint o)
  ;; (println "NEW")
  ;; (clojure.pprint/pprint n)
  (println)

  (println "-------")
  (let [old-log (:log o)
        new-log (:log n)
        new-lines (- (count new-log) (count old-log))
        ]
    (if (pos? new-lines)
      (do
        (prn 'new-lines new-lines)

        ;; new lines to print
        (print-state [] (subvec new-log (- (count new-log) new-lines)))

        ;; remember these lines as being accessible
        (swap! accessible-lines into
               (for [l (subvec new-log (- (count new-log) new-lines))]
                 (do ;;(println "adding!" l)
                     (assoc (select-keys l [:form :file :meta :line])
                            :line-count 1)))))

      (do
        ;; update lines if they are still just above our cursor position...
        ;;(println "up:" new-lines)
        ;;(prn "accessible:" accessible-lines)

        ;; work out what has changed, and if those lines changed are still accessible
        ;; cursor move and update them
        (let [accessible @accessible-lines
              lines-accessible (map :line accessible)
              max-line-num (apply max 0 lines-accessible)

              diff-log-indices
              (filter identity
                      (map (fn [line o n]
                             (if (or (not= (:results o) (:results n))
                                     (not= (:copy-progress o) (:copy-progress n)))
                               line
                               nil))
                           (range) old-log new-log))

              diff-log-entries
              (map new-log diff-log-indices)

              ;;_ (clojure.pprint/pprint accessible)

              ;; find those diffs in the accessible
              accessible-info accessible #_(sort-by :line
                                       (for [[k v] accessible]
                                         (into k v)))
              rows (reductions + (map :line-count accessible-info))
              accessible-info (map (fn [{:keys [line-count] :as info} last-row]
                                     (assoc info
                                            :last-row last-row
                                            :first-row (- last-row line-count)
                                            ))
                                   accessible-info rows)
              max-row (last rows)

              accessible-by-line (->> (for [acc accessible-info]
                                        [(:line acc) acc])
                                      (into {}))

              accessibles-found (filter identity
                                        (for [{:keys [form file meta line] :as d} diff-log-entries]
                                          (when (accessible-by-line line)
                                            (into (accessible-by-line line) d))))

              non-accessibles-found (filter identity
                                            (for [{:keys [form file meta line] :as d} diff-log-entries]
                                              (when-not (accessible-by-line line)
                                                d)))


              ]

          ;; (prn 'diff diff-log-entries)
          ;; (prn 'info accessible-info)
          ;; (prn 'rows rows)
          ;; (prn 'found accessibles-found)

          ;; update those that are accessible
          (when (not (empty? accessibles-found))
            ;;(prn accessibles-found)
            (doseq [{:keys [first-row last-row copy-progress
                            form file meta line
                            ] :as acc} accessibles-found]
              (prn 'up (- max-row first-row) ;;acc
                   )
              ;;(prn acc)
              (print-state [(old-log line)] [acc])
              (prn 'down (- max-row last-row))
              (swap! accessible-lines update-accessible-line-count
                     form file meta line (inc (count copy-progress)))))


          #_(when (not (empty? diff-log-indices))
              (let [highest-update (apply min (map :line diff-log-entries))]

                (prn diff-log-entries)
                (prn max-line-num)
                (prn highest-update)

                ;; TODO: deal with multilines
                (prn "up:" (inc (- max-line-num highest-update)))
                (print-state (into [] diff-log-entries))))

          ;; then for inaccessible changes... print new lines.
          (when (not (empty? non-accessibles-found))
            (prn 'non-acc (count non-accessibles-found))

            ;; new lines to print
            (print-state []  non-accessibles-found)

            ;; remember these lines as being accessible
            (swap! accessible-lines into
                   (for [l non-accessibles-found]
                     (assoc (select-keys l [:form :file :meta :line])
                            :line-count 1)))
            ))

        ;;(print-state (subvec new-log 0))

        )
      )
    )
  ;;(println "-------")

  (let [new-debug (:debug n)
        old-debug (:debug o)
        entries (clojure.set/difference new-debug old-debug)
        ]
    (doseq [item entries] (print-debug item))
    (when-not (empty? entries) (reset! accessible-lines []))
    )

  ;;(println "-------")

  ;; find new failures
  (let [old-log-results (mapv :results (:log o))
        new-log-results (mapv :results (:log n))
        new-failures
        (->>
         (map (fn [o n]
                (if (not= o n)
                  (filter #(= :failed (:result (:result %))) n)
                  nil)) old-log-results new-log-results)
         (filter identity)
         (apply concat))]
    (doseq [failure new-failures]
      (print-failure failure)
      )
    (when-not (empty? new-failures) (reset! accessible-lines []))
    )

  (println "-------")




  (println)

  )


(defmethod output/print-thread :default [_]
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

(defmethod output/print-form :default [_ file form file-meta host-config]
  ;; (prn 'print-form form file file-meta)
  (swap! state
         update :log
         (fn [s]
           (if-let [match (first (find-forms-matching-index s {:form form :file file :meta file-meta}))]
             ;; already printed
             s

             ;; print form
             (conj s {:form form
                      :file file
                      :meta file-meta
                      :line (count s)
                      :width (count (pr-str form))
                      :results []})))))

(defmethod output/print-result :default [_ file form file-meta host-config result]
  ;;(prn 'print-result file form file-meta host-config result)
  (comment
    (prn 'print-result result host-config)
    (prn (find-forms-matching-index @state {:form form :file file :meta file-meta})))
  (swap! state
         update :log
         (fn [s]
           (if-let [matching-index (first (find-forms-matching-index s {:form form :file file :meta file-meta}))]
             ;; already a line output. add to it.
             (do

               #_(prn 'MATCH matching-index
                    )
               (update
                s
                matching-index
                (fn [{:keys [width results] :as data}]
                  #_(prn 'UPDATE results result)
                  (-> data
                      (update :copy-progress dissoc (:host-string host-config))
                      (assoc
                       :width (+ width (count (:host-string host-config)) 1)
                       :results (conj results
                                      {:result result
                                       :host-config host-config
                                       :pos width
                                       }
                                      ))))))

             ;; TODO: this check shouldnt be done here
             ;; in test output handler no key will exist because nothing is stored
             s
             ))))

(defmethod output/debug-result :default [_ file form file-meta host-config result]
  ;;(prn 'debug-result file form file-meta host-config result)
  (swap! state
         update :debug
         conj [file form file-meta host-config result]))

(defmethod output/print-progress :default [_ file form form-meta host-string {:keys [progress context] :as data}]
  #_(prn 'print-progress file form form-meta host-string data)
  (swap! state
         update :log
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
