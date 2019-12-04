(ns spire.module.line-in-file
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.utils :as utils]
            [clojure.string :as string]))

(def documentation
  {
   :module "line-in-file"
   :description
   [
    "This module ensures a particular line is in a text file, or can replace an existing line using a regular excpression."
    "It can ensure a particular line is absent from a file."
    "It can gather a line or lines that match a regular expression from a file for further processing."
    "This is most useful when only a few lines need changing. It is more often better to template the entire file with the [template] module."]
   :opts
   {
    :regexp
    {
     :description
     [
      "The regular expression to look for in every line of the file"
      "In [command=present] mode, the pattern to replace if found. Replaces the last occurance of that pattern."
      "In [command=absent] mode, the pattern of lines to remove. All occurances of that line will be removed."
      "In [command=get] mode, the pattern of lines to bre returned. All occurances that match will be returned."
      "If the regular expression is not matched, the line will be added to the file. [before] and [after] will allow you to control where in the file the line is inserted."
      "If [before] or [after] is not used then ensure the regular expression matches both the initial state of the line as well as its state after replacement to ensure idempotence."]
     :type :regexp
     :aliases [:regex]
     :added "0.1"}

    :path
    {
     :description ["Path to the file"]
     :type :path
     :required true
     :aliases [:dest :name]
     :added "0.1"}

    :after
    {
     :description
     [
      "Used with [state=present]"
      "If specified, the line will be inserted after the last match of the specified regular expression."
      "If the first match is required, use [first-match=true]"
      "If the specified regular expression has no matches, the line will be appended to the end of the file."
      "If a match is found for [regexp], insertion is skipped and this parameter is ignored"
      "May not be used in conjunction with [:before]"]
     :type :regexp
     :added "0.1"}}}
  )

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (line-in-file :present ...)
;;
(defmethod preflight :present [_ {:keys [path regexp after before]}]
  (cond
    (empty? path)
    (assoc failed-result
           :exit 4
           :err ":path must be specified")

    (and after before)
    (assoc failed-result
           :exit 3
           :err "Cannot specify both :after and :before to :present")
    ))

(defmethod make-script :present [_ {:keys [path regexp line-num line after before]}]
  (utils/make-script
   "line_in_file_present.sh"
   {:REGEX (some->> regexp utils/re-pattern-to-sed)
    :FILE (some->> path utils/path-escape)
    :LINENUM line-num
    :LINE line
    :AFTER (some->> after utils/re-pattern-to-sed)
    :BEFORE (some->> before utils/re-pattern-to-sed)}))

(defmethod process-result :present
  [_ {:keys [path line-num regexp]} {:keys [out err exit] :as result}]
  (if (zero? exit)
    (assoc result
           :exit 0
           :result :ok)
    (assoc result
           :result :failed)))


;;
;; (line-in-file :get ...)
;;
(defmethod preflight :get [_ {:keys [path line-num regexp]}]
  (cond
    (empty? path)
    (assoc failed-result
           :exit 4
           :err ":path must be specified")

    (and line-num regexp)
    (assoc failed-result
           :exit 3
           :err "Cannot specify both :line-num and :regexp to :get")

    (= 0 line-num)
    (assoc failed-result
           :exit 2
           :err "No line number 0 in file. File line numbers are 1 offset.")))

(defmethod make-script :get [_ {:keys [path line-num regexp]}]
  (utils/make-script
   "line_in_file_get.sh"
   {:REGEX (some->> regexp utils/re-pattern-to-sed)
    :FILE (some->> path utils/path-escape)
    :LINENUM line-num}))

(defmethod process-result :get [_
                                {:keys [path line-num regexp]}
                                {:keys [out err exit] :as result}]
  (if (zero? exit)
    (if regexp
      (if (= "no match" out)
        {:exit 0
         :result :ok
         :line-num nil
         :line nil
         :line-nums []
         :lines []
         :matches {}}
        (let [out-lines (string/split out #"\n")
              line-nums (-> out-lines first (string/split #"\s+")
                            (->> (map #(Integer/parseInt %)) (into [])))
              lines (into [] (rest out-lines))]
          {:exit 0
           :result :ok
           :line-num (last line-nums)
           :line (last lines)
           :line-nums line-nums
           :lines lines
           :matches (into {} (mapv vector line-nums lines))})
        )
      (let [out-lines (string/split out #"\n")
            [line-num line] out-lines
            line-num (Integer/parseInt line-num)]
        {:exit 0
         :result :ok
         :line-num line-num
         :line line
         :line-nums [line-num]
         :lines [line]
         :matches {line-num line}}))
    (assoc result
           :result :failed)))

(defn line-in-file*
  "
### line-in-file

Manage lines in text files

#### Overview

 * Get lines from a file, by line number or matching regular expression
 * Ensure lines are in a file
 * Ensure lines are not in a file
 * Works with single lines only. Does not insert/replace blocks of text

#### Usage

 `(line-in-file command opts)`

 `command` is `:get`, `:present` or `:absent`

#### Options

The following are the keys of the entries in the hashmap passed into `opts`

| Key      | Value                                                    |
| -------- | -------------------------------------------------------- |
| path     | The location on the remote system of the file to process |
| line-num | The line number (1 offset) in the file to get/replace    |

  "
  [command & [{:keys [path regexp line state after before]
               :or {state :present}
               :as opts}]]
  (transport/pipelines
   (fn [_ session]
     (or
      (preflight command opts)
      (->>
       (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
       (process-result command opts))))))

(defn line-in-file
  "Make sure a line is present in, or absent from, a file"
  [& args]
  (binding [state/*form* (concat '(line-in-file) args)]
    (output/print-form state/*form*)
    (apply line-in-file* args)))
