(ns spire.module.line-in-file
  (:require [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]))

;; defaults
(def options-match-choices #{:first :last :all})
(def options-match-default :first)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(def ^:private posix-shell-types #{:bash :sh :dash :zsh :ksh :busybox})

(def ^:private supported-shell-types
  (into posix-shell-types #{:fish :powershell :cmd-exe :nu}))

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defn check-shell-supported
  "Return nil if the eyre-detected shell is supported by the
  line-in-file module, otherwise a failed result map explaining
  the limitation.

  The module ships scripts for POSIX shells, fish, powershell,
  cmd.exe and nushell.  Csh/tcsh and any other exotic shell eyre
  might report are not supported."
  []
  (let [shell-type (facts/get-fact [:shell :type])]
    (when-not (supported-shell-types shell-type)
      {:exit 1
       :out ""
       :err (format "line-in-file module does not support the '%s' shell"
                    (name (or shell-type :unknown)))
       :result :failed})))

(defn- regex-pattern-string
  "Extract the pattern string from a Clojure/Java regex, or return
  the value as-is if it is already a string."
  [re]
  (when re
    (if (instance? java.util.regex.Pattern re)
      (.pattern ^java.util.regex.Pattern re)
      (str re))))

(defmacro line-in-file-script
  "Embed all shell variants of a line-in-file script at compile time
  via `utils/make-script` and select the appropriate one at runtime
  based on the eyre-detected shell `:type`.  `base-name` is the script
  basename without directory prefix or extension (e.g.
  \"line_in_file_present\"); the five `*-vars` arguments are the
  variable maps for POSIX, fish, powershell, cmd.exe and nushell
  respectively.  Only the matching branch's vars are evaluated at
  runtime."
  [base-name posix-vars fish-vars ps-vars cmd-vars nu-vars]
  (let [sh-file   (str "line_in_file/" base-name ".sh")
        fish-file (str "line_in_file/" base-name ".fish")
        ps-file   (str "line_in_file/" base-name ".ps1")
        cmd-file  (str "line_in_file/" base-name ".bat")
        nu-file   (str "line_in_file/" base-name ".nu")]
    `(case (facts/get-fact [:shell :type])
       :fish       (utils/make-script ~fish-file ~fish-vars :fish)
       :powershell (utils/make-script ~ps-file   ~ps-vars   :powershell)
       :cmd-exe    (utils/make-script ~cmd-file  ~cmd-vars  :cmd)
       :nu         (utils/make-script ~nu-file  ~nu-vars   :nu)
       (utils/make-script ~sh-file   ~posix-vars))))

(defn escape-leading-spaces [s]
  (let [[_ space remain] (re-matches #"^(\s*)(.+)$" s)
        escaped (->> space
                     (map #(case %
                             \space "\\ "
                             \tab "\\t"))
                     (apply str))]
    (str escaped (utils/string-escape remain))))

;;
;; (line-in-file :present ...)
;;
(defmethod preflight :present [_ {:keys [path regexp string-match line-match after before match insert-at]}]
  (let [shell-type (facts/get-fact [:shell :type])]
    (or
     (when (or (posix-shell-types shell-type)
               (= :fish shell-type))
       (facts/check-bins-present #{:sed :grep :awk}))
     (cond
       (empty? path)
       (assoc failed-result
              :exit 4
              :err ":path must be specified")

       (and after before)
       (assoc failed-result
              :exit 3
              :err "Cannot specify both :after and :before to :present")

       (< 1 (+ (if regexp 1 0)
               (if string-match 1 0)
               (if line-match 1 0)))
       (assoc failed-result
              :exit 3
              :err "Can only specify one of :regexp, :string-match and :line-match to :present")

       (not (options-match-choices (or match options-match-default)))
       (assoc failed-result
              :exit 3
              :err (format ":match needs to be one of %s"
                           (prn-str options-match-choices)))

       (not (#{:bof :eof} (or insert-at :eof)))
       (assoc failed-result
              :exit 3
              :err (format ":insert-at needs to be one of %s"
                           (prn-str #{:bof :eof})))))))

(defmethod make-script :present [_ {:keys [path
                                           regexp string-match line-match
                                           line-num line
                                           after before
                                           match insert-at]}]
  (let [line-match-val (if (or regexp string-match line-match) line-match line)
        selector-posix (case (or match options-match-default)
                         :first "head -1"
                         :last "tail -1"
                         :all "cat")
        selector-ps (case (or match options-match-default)
                      :first "First"
                      :last "Last"
                      :all "All")
        selector-shell (case (or match options-match-default)
                         :first "first"
                         :last "last"
                         :all "all")
        insertat (some->> insert-at name)
        shell-type (facts/get-fact [:shell :type])
        shell-vars (case shell-type
                     (:bash :sh :dash :zsh :ksh :busybox :fish)
                     {:REGEX (some-> regexp utils/re-pattern-to-sed)
                      :STRING_MATCH (some->> string-match utils/string-escape)
                      :LINE_MATCH (some->> line-match-val utils/string-escape)
                      :FILE (some->> path utils/path-escape)
                      :LINENUM line-num
                      :LINE (some->> line utils/string-escape)
                      :SEDLINE (some->> line escape-leading-spaces utils/string-escape)
                      :AFTER (some->> after utils/re-pattern-to-sed)
                      :BEFORE (some->> before utils/re-pattern-to-sed)
                      :SELECTOR selector-posix
                      :INSERTAT insertat}

                     :powershell
                     {:REGEX (regex-pattern-string regexp)
                      :STRING_MATCH string-match
                      :LINE_MATCH line-match-val
                      :FILE path
                      :LINENUM line-num
                      :LINE line
                      :AFTER (regex-pattern-string after)
                      :BEFORE (regex-pattern-string before)
                      :SELECTOR selector-ps
                      :INSERTAT insertat}

                     :cmd-exe
                     {:REGEX (regex-pattern-string regexp)
                      :STRING_MATCH string-match
                      :LINE_MATCH line-match-val
                      :FILE path
                      :LINENUM line-num
                      :LINE line
                      :AFTER (regex-pattern-string after)
                      :BEFORE (regex-pattern-string before)
                      :SELECTOR selector-shell
                      :INSERTAT insertat}

                     :nu
                     {:REGEX (regex-pattern-string regexp)
                      :STRING_MATCH string-match
                      :LINE_MATCH line-match-val
                      :FILE path
                      :LINENUM line-num
                      :LINE line
                      :AFTER (regex-pattern-string after)
                      :BEFORE (regex-pattern-string before)
                      :SELECTOR selector-shell
                      :INSERTAT insertat})]
    (utils/make-script
      (str "line_in_file/line_in_file_present."
           (case shell-type
             :fish "fish"
             :powershell "ps1"
             :cmd-exe "bat"
             :nu "nu"
             "sh"))
      shell-vars
      shell-type)))

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
(defmethod preflight :absent [_ {:keys [path
                                        regexp string-match line-match
                                        line-num]}]
  (let [shell-type (facts/get-fact [:shell :type])]
    (or
     (when (or (posix-shell-types shell-type)
               (= :fish shell-type))
       (facts/check-bins-present #{:sed :grep :awk}))
     (cond
       (empty? path)
       (assoc failed-result
              :exit 4
              :err ":path must be specified")

       (< 1 (+ (if regexp 1 0)
               (if string-match 1 0)
               (if line-match 1 0)))
       (assoc failed-result
              :exit 3
              :err "Can only specify one of :regexp, :string-match and :line-match to :absent")

       (and regexp string-match)
       (assoc failed-result
              :exit 3
              :err "Cannot specify both :regexp and :string-match to :absent")

       (not (or regexp line-num))
       (assoc failed-result
              :exit 3
              :err "must specify :regexp or :line-num")))))

(defmethod make-script :absent [_ {:keys [path
                                          regexp string-match line-match
                                          line line-num
                                          match]}]
  (let [line-match-val (if (or regexp string-match line-match) line-match line)
        posix-vars {:REGEX (some-> regexp utils/re-pattern-to-sed)
                    :STRING_MATCH (some->> string-match utils/string-escape)
                    :LINE_MATCH (some->> line-match-val utils/string-escape)
                    :FILE (some->> path utils/path-escape)
                    :LINENUM line-num}
        ps-vars {:REGEX (regex-pattern-string regexp)
                 :STRING_MATCH string-match
                 :LINE_MATCH line-match-val
                 :FILE path
                 :LINENUM line-num}
        cmd-vars {:REGEX (regex-pattern-string regexp)
                  :STRING_MATCH string-match
                  :LINE_MATCH line-match-val
                  :FILE path
                  :LINENUM line-num}
        nu-vars {:REGEX (regex-pattern-string regexp)
                 :STRING_MATCH string-match
                 :LINE_MATCH line-match-val
                 :FILE path
                 :LINENUM line-num}]
    (line-in-file-script "line_in_file_absent" posix-vars posix-vars ps-vars cmd-vars nu-vars)))

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
(defmethod preflight :get [_ {:keys [path
                                     line-num regexp string-match
                                     line-match match]}]
  (let [shell-type (facts/get-fact [:shell :type])]
    (or
     (when (or (posix-shell-types shell-type)
               (= :fish shell-type))
       (facts/check-bins-present #{:sed :grep :awk}))
     (cond
       (empty? path)
       (assoc failed-result
              :exit 4
              :err ":path must be specified")

       (and line-num regexp)
       (assoc failed-result
              :exit 3
              :err "Cannot specify both :line-num and :regexp to :get")

       (< 1 (+ (if regexp 1 0)
               (if string-match 1 0)
               (if line-match 1 0)))
       (assoc failed-result
              :exit 3
              :err "Can only specify one of :regexp, :string-match and :line-match to :get")

       (not (options-match-choices (or match options-match-default)))
       (assoc failed-result
              :exit 3
              :err (format ":match needs to be one of %s"
                           (prn-str options-match-choices)))

       (= 0 line-num)
       (assoc failed-result
              :exit 2
              :err "No line number 0 in file. File line numbers are 1 offset.")))))

(defmethod make-script :get [_ {:keys [path
                                       line-num regexp string-match
                                       line-match line match]}]
  (let [line-match-val (if (or regexp string-match line-match) line-match line)
        selector-posix (case (or match options-match-default)
                         :first "head -1"
                         :last "tail -1"
                         :all "cat")
        selector-ps (case (or match options-match-default)
                      :first "First"
                      :last "Last"
                      :all "All")
        selector-shell (case (or match options-match-default)
                         :first "first"
                         :last "last"
                         :all "all")
        posix-vars {:REGEX (some-> regexp utils/re-pattern-to-sed)
                    :STRING_MATCH (some->> string-match utils/string-escape)
                    :LINE_MATCH (some->> line-match-val utils/string-escape)
                    :FILE (some->> path utils/path-escape)
                    :LINENUM line-num
                    :SELECTOR selector-posix}
        ps-vars {:REGEX (regex-pattern-string regexp)
                 :STRING_MATCH string-match
                 :LINE_MATCH line-match-val
                 :FILE path
                 :LINENUM line-num
                 :SELECTOR selector-ps}
        cmd-vars {:REGEX (regex-pattern-string regexp)
                  :STRING_MATCH string-match
                  :LINE_MATCH line-match-val
                  :FILE path
                  :LINENUM line-num
                  :SELECTOR selector-shell}
        nu-vars {:REGEX (regex-pattern-string regexp)
                 :STRING_MATCH string-match
                 :LINE_MATCH line-match-val
                 :FILE path
                 :LINENUM line-num
                 :SELECTOR selector-shell}]
    (line-in-file-script "line_in_file_get" posix-vars posix-vars ps-vars cmd-vars nu-vars)))

(defmethod process-result :get [_
                                {:keys [path line-num regexp]}
                                {:keys [out err exit] :as result}]
  (if (zero? exit)
    (let [out (-> out (string/replace "\r\n" "\n") (string/replace "\r" ""))]
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
             }))
        (let [out-lines (string/split out #"\n")
              [line-num line] out-lines
              line-num (Integer/parseInt line-num)]
          {:exit 0
           :result :ok
           :line-num line-num
           :line line
           :line-nums [line-num]
           :lines [line]
           :matches {line-num line}})))
    (assoc result
           :result :failed)))

(utils/defmodule line-in-file* [command & [{:keys [path regexp line after before]
                                            :as opts}]]
  [host-string session {:keys [exec-fn sudo] :as shell-context}]
  (or
   (check-shell-supported)
   (preflight command opts)
   (let [shell-path (facts/get-fact [:shell :shell])]
     (->>
      (exec-fn session shell-path (make-script command opts) "UTF-8" {:sudo sudo})
      (process-result command opts)))))

(defmacro line-in-file
  "Ensures a particular line is in a text file, or can replace an
  existing line using a regular expression. Or ensure a particular
  line is absent from a file. Or gather a line or lines from a file
  that match a regular expression or by linenum.
  (line-in-file command options)

  given:

  `command`: Should be `:get`, `:present` or `:absent`

  `options`: A hashmap of options, where:

  `:path` The path to a file

  `:regexp` The regular expression to look for in every line of the
  file. The line that matches will be replaced.

  `:string-match` A string to look for in every line of the file. The
  line that contains a match for the string will be replaced.

  `:line-match` An entire line to look for in every line of the
  file. The line that completely matches the string will be
  replaced. If neither `:regexp` nor `:string-match` nor `:line-match`
  is specified, then the contents of `:line` is used as a value for
  `:line-match`.

  `:line-num` Specify the line to match by line number instead of
  regular expression.

  `:line` The contents of the line to insert into the file.

  `:after` A regular expression to insert the line after, if the
  `:regexp` match is not found.

  `:before` A regular expression to insert the line before, if the
  `:regexp` match is not found.

  `:match` How to handle files with multiple lines that match. Should
  be `:first`, `:last` or `:all`

  `:insert-at` If no line matches and no `:after` or `:before`
  matches, where to insert the line. Should be `:eof` for end of file,
  or `:bof` for beginning of file.
  "
  [& args]
  `(utils/wrap-report ~&form (line-in-file* ~@args)))

(def documentation
  {
   :module "line-in-file"
   :blurb "Manage lines in text files"
   :description
   [
    "This module ensures a particular line is in a text file, or can replace an existing line using a regular expression."
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
       "In `:get` mode, the pattern of lines to be returned. All occurances that match will be returned."
       "If the regular expression is not matched, the line will be added to the file. `:before` and `:after` will allow you to control where in the file the line is inserted."
       "If `:before` or `:after` is not used then ensure the regular expression matches both the initial state of the line as well as its state after replacement to ensure idempotence."
       "NOTE: The regular expression is passed as a clojure regex literal or a Java regex but is converted into a sed regular expression for operation on the host. As such the semantics of the regular expression are those of the sed implementation on the host and not of Java's Pattern class."
       ]
      :aliases [:regex]
      :type :regexp}]

    [:string-match
     {:description
      [
       "A string to look for in every line of the file."
       "In `:present` mode, the line that contains a match for the string will be replaced."
       "In `:absent` mode, the line that contains a match for the string will be removed."
       "In `:get` mode, the line that contains a match for the string will be returned."
       "The same `:before`, `:after` and `:insert-at` rules apply as they do for `:regexp`."
       ]
      :required false
      :type :string}]

    [:line-match
     {:description
      [
       "An entire line to look for in every line of the file."
       "In `:present` mode, the line that matches will be replaced."
       "In `:absent` mode, the line that matches will be removed."
       "In `:get` mode, the lines that match will be returned."
       "The same `:before`, `:after` and `:insert-at` rules apply as they do for `:regexp`."
       "If neither `:regexp` nor `:string-match` nor `:line-match` is specified, then the contents of `:line` is used as a value for `:line-match`."
       ]
      :required false
      :type :string}]

    [:line-num
     {:description
      ["Specify the line to match by line number instead of regular expression. or string"]
      :type :integer
      :required false
      }]

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
       "If specified, the line will be inserted after the last (or first) match of the specified regular expression."
       "If the first match is required, use `:first-match true`"
       "If the specified search expression has no matches, the line will be appended to the end (or prepended to the start) of the file."
       "If the prepending to the start is required, use `:insert-at :bof`"
       "If a match is found for `:regexp`, `:string-match` or `:line-match`, insertion is skipped and this parameter is ignored"
       "May not be used in conjunction with `:before`"]
      :type :regexp}]

    [:before
     {
      :description
      [
       "Used with mode `:present`"
       "If specified, the line will be inserted before the last (or first) match of the specified regular expression."
       "If the first match is required, use `:first-match true`"
       "If the specified search expression has no matches, the line will be appended to the end (or prepended to the start) of the file."
       "If the prepending to the start is required, use `:insert-at :bof`"
       "If a match is found for `:regexp`, `:string-match` or `:line-match`, insertion is skipped and this parameter is ignored"
       "May not be used in conjunction with `:after`"]
      :type :regexp}]

    [:match
     {
      :description
      [
       "How to handle files with multiple lines that match."
       "Should be `:first`, `:last` or `:all`"]
      :type :keyword
      :required :false
      :default options-match-default}]

    [:insert-at
     {
      :description
      ["If no line matches and no `:after` or `:before` matches, describes where to insert the line."
       "Should be `:eof` for end of file, or `:bof` for beginning of file."]
      :type :keyword
      :required :false
      :default :eof
      }]



    ]


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
