(ns spire.output.default
  (:require [spire.utils :as utils]
            [spire.output.core :as output]
            [puget.printer :as puget]
            [sci.core :as sci]
            [clojure.string :as string]
            [clojure.core.async :refer [<!! put! chan thread]]))

(set! *warn-on-reflection* true)

(def debug false)

;; truncate the printing of any string literals inside forms.
(def max-string-length (sci/new-dynamic-var 'max-string-length nil))

(defonce state
  (atom {:log []
         :debug #{}}))

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

(defn clear-screen-from-cursor-down []
  (print (str "\033[J")))

(defn read-until [ch]
  (loop [input ""]
    (let [c (char (.read ^java.io.Reader *in*))]
      (if (not= ch (str c))
        (recur (str input c))
        (str input c)))))

(defn read-cursor-position []
  (print (str "\033[6n"))
  (SpireUtils/enter-raw-mode 0)
  (.flush *out*)
  (let [text (read-until "R")]
    (SpireUtils/leave-raw-mode 0)
    (let [[_ body] (string/split text #"\[")
          [body _] (string/split body #"R")
          [row col] (string/split body #";")]
      [(Integer/parseInt row)
       (Integer/parseInt col)])))

(defn elide-form-strings
  [form max-length]
  (if max-length
    (->> form
         (clojure.walk/postwalk
          (fn [f]
            (if (string? f)
              (if (< max-length (count f))
                (str (subs f 0 max-length) "…")
                f)
              f))))
    form))

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


;; which lines are immediately accessible above the cursor position
;; that we can move the cursor to to rewrite
;; lines are in order as they appear. entries are hashmaps.
;; keys are form, file, meta, line, line-count
(defonce accessible-lines
  (atom []))

(defn update-accessible-line-count [s uform ufile umeta uline line-count]
  (->> s
       (mapv (fn [{:keys [form file meta line] :as data}]
               (if (and (= form uform)
                        (= file ufile)
                        (= meta umeta)
                        (= line uline))
                 (assoc data :line-count line-count)
                 data)))))

(defn print-failure [{:keys [result host-config]}]
  (println
   (str
    (utils/colour :yellow)
    (utils/escape-codes 40 0 31 1)
    (format "%s failed!%s %sexit:%d%s"
            (str (:key host-config))
            (utils/reset)
            (utils/escape-codes 40 0 31 22)
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

(defn print-state [{:keys [form file meta results copy-progress opts] :as s}]
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
                  (pr-str (elide-form-strings form (:max-string-length opts)))
                  (apply str completed)
                  )]

    (println line)

    ;; progress bars for this module
    (let [max-host-key-length
          (when-not (empty? copy-progress)
            (apply max (map (fn [[h _]] (count (str (:key h)))) copy-progress)))
          max-filename-length
          (when-not (empty? copy-progress)
            (apply max (map (fn [[_ v]] (:max-filename-length v)) copy-progress)))
          ]
      (doseq [[host-config progress] copy-progress]
        (println (utils/progress-bar-from-stats (str (:key host-config)) max-host-key-length max-filename-length progress))))

    ;; return the total number of lines
    (+ (count copy-progress) (utils/num-terminal-lines line))))

(defn count-lines [{:keys [form file meta results copy-progress opts] :as s}]
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
                  (pr-str (elide-form-strings form (:max-string-length opts)))
                  (apply str completed)
                  )]

    ;; return the total number of lines
    (+ (count copy-progress) (utils/num-terminal-lines line)))
  )

(defn state-change [[o n]]
  (when debug (println "-------"))
  (let [old-log (:log o)
        new-log (:log n)
        new-lines (- (count new-log) (count old-log))
        ]
    (if (pos? new-lines)
      (do
        (when debug (prn 'new-lines new-lines))

        ;; new lines to print
        (let [line-counts
              (doall
               (for [n (subvec new-log (- (count new-log) new-lines))]
                 [n (print-state n)]))]

          ;; remember these lines as being accessible
          (swap! accessible-lines into
                 (for [[l line-count] line-counts]
                   (do ;;(println "adding!" l)
                     (assoc (select-keys l [:form :file :meta :line])
                            :line-count line-count))))))

      (do
        ;; update lines if they are still just above our cursor position...
        ;; work out what has changed, and if those lines changed are still accessible
        ;; cursor move and update them
        (let [accessible @accessible-lines

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

              ;; find those diffs in the accessible
              rows (reductions + (map :line-count accessible))
              accessible-info (map (fn [{:keys [line-count] :as info} last-row]
                                     (assoc info
                                            :last-row last-row
                                            :first-row (- last-row line-count)))
                                   accessible rows)
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
                                                d)))]
          ;; update those that are accessible
          (when (not (empty? accessibles-found))
            (doseq [{:keys [first-row last-row copy-progress
                            form file meta line line-count
                            ] :as acc} accessibles-found]

              (if debug
                (prn 'up (- max-row first-row))
                (up (- max-row first-row)))

              (let [old-size line-count
                    new-size (count-lines acc)]

                (cond
                  (not= new-size old-size)
                  (do
                    ;; clear below
                    (if debug
                      (prn 'clear-screen-from-cursor-down)
                      (clear-screen-from-cursor-down))

                    (print-state acc)

                    ;; we reprint all subsequent state
                    ;; state is inside accessibles, after out point
                    (let [after (->> accessible-info
                                     (drop-while #(<= (:first-row %) first-row))
                                     (map
                                      (fn [{:keys [form file meta line] :as data}]
                                        ;; find new-log that matches and return that
                                        (->> new-log
                                             (filter
                                              #(= (select-keys % [:form :file :meta :line])
                                                  (select-keys data [:form :file :meta :line])))
                                             first))))]
                      (doseq [a after]
                        (print-state a))))

                  :else
                  (do

                    (print-state acc)

                    (if debug
                      (prn 'down (- max-row last-row))
                      (down (- max-row last-row)))))

                (swap! accessible-lines update-accessible-line-count
                       form file meta line new-size))))

          ;; then for inaccessible changes... print new lines.
          (when (not (empty? non-accessibles-found))
            ;;(prn 'non-acc (count non-accessibles-found))

            ;; new lines to print
            (let [line-counts
                  (doall
                   (for [n non-accessibles-found]
                     [n (print-state n)]))]

              ;; remember these lines as being accessible
              (swap! accessible-lines into
                     (for [[l line-count] line-counts]
                       (do ;;(println "adding!" l)
                         (assoc (select-keys l [:form :file :meta :line])
                                :line-count line-count)))))))))

    ;; new print data
    (let [new-streams
          (->>
           (map (fn [o n]
                  (when (and (not= o n)
                           (not= (:streams o) (:streams n)))
                    [{:form (:form n)
                      :file (:file n)
                      :meta (:meta n)
                      :line (:line n)}
                     (->> (for [[k v] (:streams n)]
                            (let [old-data (get-in o [:streams k])
                                  new-data v]
                              (when (> (count new-data) (count old-data))
                                (subvec v (count old-data))
                                )))
                          (apply concat))]))
                old-log new-log)
           (filter identity))]
      (doseq [[k v] new-streams]
        (doseq [[out err] v]
          (when out
            (print out)
            (.flush ^java.io.Writer *out*))
          (when err
            (binding [*out* *err*]
              (print err))
            (.flush ^java.io.Writer *err*))))
      (when (seq new-streams)
        (reset! accessible-lines []))))

  (let [new-debug (:debug n)
        old-debug (:debug o)
        entries (clojure.set/difference new-debug old-debug)]
    (doseq [item entries] (print-debug item))
    (when-not (empty? entries) (reset! accessible-lines [])))

  ;; find new failures
  (let [old-log-results (mapv :results (:log o))
        new-log-results (mapv :results (:log n))
        new-failures
        (->>
         (map (fn [o n]
                (when (not= o n)
                  (filter #(= :failed (:result (:result %))) (subvec n (count o)))))
              old-log-results new-log-results)
         (filter identity)
         (apply concat))]
    (doseq [failure new-failures]
      (print-failure failure))
    (when-not (empty? new-failures) (reset! accessible-lines [])))

  (when debug
    (println "-------")
    (println)))

(defn output-print-thread []
  (thread
    (loop []
      (state-change (<!! state-change-chan))
      (recur))))

(defmethod output/print-thread :default [_]
  (output-print-thread))

(defonce state-watcher
  (add-watch
   state :output
   (fn [_ _ o n]
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

(defn output-print-form [file form file-meta host-config]
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
                      :opts {:max-string-length @max-string-length}
                      :results []}))))
  )

(defmethod output/print-form :default [_ file form file-meta host-config]
  (output-print-form file form file-meta host-config))

(defn output-print-result [file form file-meta host-config result]
  ;;(prn 'print-result file form file-meta host-config result)
  (comment
    (prn 'print-result result host-config)
    (prn (find-forms-matching-index @state {:form form :file file :meta file-meta})))
  (swap! state
         update :log
         (fn [s]
           (if-let [matching-index (first (find-forms-matching-index s {:form form :file file :meta file-meta}))]
             ;; already a line output. add to it.
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
             )))
  )

(defmethod output/print-result :default [_ file form file-meta host-config result]
  (output-print-result file form file-meta host-config result)
  )

(defn output-debug-result [file form file-meta host-config result]
  ;;(prn 'debug-result file form file-meta host-config result)
  (swap! state
         update :debug
         conj [file form file-meta host-config result])
  )
(defmethod output/debug-result :default [_ file form file-meta host-config result]
  (output-debug-result file form file-meta host-config result))

(defn output-print-progress [file form form-meta host-string {:keys [progress context] :as data}]
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

(defmethod output/print-progress :default [_ file form form-meta host-string data]
  (output-print-progress file form form-meta host-string data))

(defn output-print-streams [file form form-meta host-string stdout stderr]
  ;; (prn 'print-streams file form form-meta host-string stdout stderr)
  (swap! state
         update :log
         (fn [s]
           (if-let [matching-index (first (find-forms-matching-index s {:form form :file file :meta form-meta}))]
             (update
              s
              matching-index
              (fn [{:keys [width results] :as data}]
                (update-in data [:streams host-string] #(conj (or % []) [stdout stderr]))))

             ;; no matching-index. probably a test being run
             s)))
  )

(defmethod output/print-streams :default [_ file form form-meta host-string stdout stderr]
  (output-print-streams file form form-meta host-string stdout stderr))
