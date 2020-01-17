(ns spire.module.line-in-file
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
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
;; (line-in-file :present ...)
;;
(defmethod preflight :present [_ {:keys [path regexp after before match insert-at]}]
  (cond
    (empty? path)
    (assoc failed-result
           :exit 4
           :err ":path must be specified")

    (and after before)
    (assoc failed-result
           :exit 3
           :err "Cannot specify both :after and :before to :present")

    (not (options-match-choices (or match options-match-default)))
    (assoc failed-result
           :exit 3
           :err (format ":match needs to be one of %s"
                        (prn-str options-match-choices)))

    (not (#{:bof :eof} (or insert-at :eof)))
    (assoc failed-result
           :exit 3
           :err (format ":insert-at needs to be one of %s"
                        (prn-str #{:bof :eof})))))

(defmethod make-script :present [_ {:keys [path regexp line-num line after before match insert-at]}]
  (utils/make-script
   "line_in_file_present.sh"
   {:REGEX (some->> regexp utils/re-pattern-to-sed)
    :FILE (some->> path utils/path-escape)
    :LINENUM line-num
    :LINE line
    :AFTER (some->> after utils/re-pattern-to-sed)
    :BEFORE (some->> before utils/re-pattern-to-sed)
    :SELECTOR (case (or match options-match-default)
                :first "head -1"
                :last "tail -1"
                :all "cat")
    :INSERTAT (some->> insert-at name)}))

(defmethod process-result :present
  [_ {:keys [path line-num regexp]} {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    (assoc result
           :exit 0
           :result :ok)

    (= 255 exit)
    (assoc result
           :exit 0
           :result :changed)

    :else
    (assoc result
           :result :failed)))

;;
;; (line-in-file :absent ...)
;;
(defmethod preflight :absent [_ {:keys [path regexp line-num]}]
  (cond
    (empty? path)
    (assoc failed-result
           :exit 4
           :err ":path must be specified")

    (not (or regexp line-num))
    (assoc failed-result
           :exit 3
           :err "must specify :regexp or :line-num")))

(defmethod make-script :absent [_ {:keys [path regexp line-num]}]
  (utils/make-script
   "line_in_file_absent.sh"
   {:REGEX (some->> regexp utils/re-pattern-to-sed)
    :FILE (some->> path utils/path-escape)
    :LINENUM line-num}))

(defmethod process-result :absent
  [_ {:keys [path line-num regexp]} {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    (assoc result
           :exit 0
           :result :ok)

    (= 255 exit)
    (assoc result
           :exit 0
           :result :changed)

    :else
    (assoc result
           :result :failed)))


;;
;; (line-in-file :get ...)
;;
(defmethod preflight :get [_ {:keys [path line-num regexp match]}]
  (cond
    (empty? path)
    (assoc failed-result
           :exit 4
           :err ":path must be specified")

    (and line-num regexp)
    (assoc failed-result
           :exit 3
           :err "Cannot specify both :line-num and :regexp to :get")

    (not (options-match-choices (or match options-match-default)))
    (assoc failed-result
           :exit 3
           :err (format ":match needs to be one of %s"
                        (prn-str options-match-choices)))

    (= 0 line-num)
    (assoc failed-result
           :exit 2
           :err "No line number 0 in file. File line numbers are 1 offset.")))

(defmethod make-script :get [_ {:keys [path line-num regexp match]}]
  (utils/make-script
   "line_in_file_get.sh"
   {:REGEX (some->> regexp utils/re-pattern-to-sed)
    :FILE (some->> path utils/path-escape)
    :LINENUM line-num
    :SELECTOR (case (or match options-match-default)
                :first "head -1"
                :last "tail -1"
                :all "cat")}))

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
           :matches (into {} (mapv vector line-nums lines))
           })
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

(utils/defmodule line-in-file* [command & [{:keys [path regexp line after before]
                                           :as opts}]]
  [host-string session ]
  (println "line-in-file*" (make-script command opts))
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts))))

(defmacro line-in-file [& args]
  `(utils/wrap-report ~*file* ~&form (line-in-file* ~@args)))


(def documentation
  {
   :module "line-in-file"
   :blurb "Manage lines in text files"
   :description
   [
    "This module ensures a particular line is in a text file, or can replace an existing line using a regular excpression."
    "It can ensure a particular line is absent from a file."
    "It can gather a line or lines that match a regular expression from a file for further processing."
    "This is most useful when only a few lines need changing. Often it is better to template the entire file with the `template` module."]
   :form "(line-in-file mode options)"
   :args
   [{:arg "mode"
     :desc "The mode to operate in. Should be one of `:get`, `:present` or `:absent`"
     :values
     [[:get "Return all lines matching the regular expression `:regexp` from the file. Also returns their line numbers."]
      [:present "Ensure that the passed in `:line` is in the file optionally placing it in the specified position."]
      [:absent "Ensure that any lines maching the regular expression `:regexp` are not in the file."]]}
    {:arg "options"
     :desc "A hashmap of options. All available option keys and their values are described below"}]

   :opts
   [
    [:path
     {
      :description ["Path to the file"]
      :type :string
      :required true}]

    [:regexp
     {
      :description
      [
       "The regular expression to look for in every line of the file"
       "In `:present` mode, the pattern to replace if found. Replaces the last occurance of that pattern."
       "In `:absent` mode, the pattern of lines to remove. All occurances of that line will be removed."
       "In `:get` mode, the pattern of lines to bre returned. All occurances that match will be returned."
       "If the regular expression is not matched, the line will be added to the file. `:before` and `:after` will allow you to control where in the file the line is inserted."
       "If `:before` or `:after` is not used then ensure the regular expression matches both the initial state of the line as well as its state after replacement to ensure idempotence."]
      :aliases [:regex]
      :type :regexp}]

    [:line
     {
      :description ["The contents of the line to insert into the file"]
      :type :string
      }]

    [:after
     {
      :description
      [
       "Used with mode `:present`"
       "If specified, the line will be inserted after the last match of the specified regular expression."
       "If the first match is required, use `:first-match true`"
       "If the specified regular expression has no matches, the line will be appended to the end of the file."
       "If a match is found for `:regexp`, insertion is skipped and this parameter is ignored"
       "May not be used in conjunction with `:before`"]
      :type :regexp}]]

   :examples
   [
    {:description "Replace localhost entry with a custom line"
     :form "
(line-in-file :present
              {:path \"/etc/hosts\"
               :regexp #\"^127\\.0\\.0\\.1\"
               :line \"127.0.0.1 localhost local myhostname\"})"}

    {:description "Add a comment to the web port definition in /etc/services"
     :form "
(line-in-file :present
              {:path \"/etc/services\"
               :regexp #\"^# http service port\"
               :line \"# http service port\"})"}
    ]}
  )
