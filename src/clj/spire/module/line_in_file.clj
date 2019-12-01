(ns spire.module.line-in-file
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [clojure.string :as string]))

(defn re-pattern-to-sed [re]
  (-> re
      .pattern
      (string/replace "\"" "\"")
      (string/replace "/" "\\/")
      (str "/")
      (->> (str "/"))))


(defn path-escape [path]
  (string/replace path "\"" "\\\""))

(defn double-quote [string]
  (str "\"" string "\""))

(defn path-quote [path]
  (double-quote (path-escape path)))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (line-in-file :present ...)
;;
(defmethod preflight :present [_ {:keys [path regexp]}]
  (cond
    (empty? path)
    (assoc failed-result
           :exit 4
           :err ":path must be specified")))

(defmethod make-script :present [_ {:keys [path regexp line-num line]}]
  (format "
REGEX=\"%s\"
FILE=\"%s\"
LINENUM=\"%s\"
LINE=\"%s\"

if [ ! -f \"$FILE\" ]; then
  echo -n \"File not found.\" 1>&2
  exit 1
fi

if [ ! -r \"$FILE\" ]; then
  echo -n \"File not readable.\" 1>&2
  exit 1
fi

LINECOUNT=$(wc -l \"$FILE\" | awk '{print $1}')

# :present by linenum
if [ \"$LINENUM\" ]; then
  if [ \"$LINENUM\" -gt \"$LINECOUNT\" ]; then
    echo -n \"No line number $LINENUM in file.\" 1>&2
    exit 2
  elif [ \"$LINENUM\" -lt \"-$LINECOUNT\" ]; then
    echo -n \"No line number $LINENUM in file.\" 1>&2
    exit 2
  elif [ \"$LINENUM\" -lt 0 ]; then
    LINENUM=$((LINECOUNT + LINENUM + 1))
  fi

  sed -i \"${LINENUM}c${LINE}\" \"$FILE\"
  exit 0
fi

# :present by regexp
if [ \"$REGEX\" ]; then
  LINENUM=$(sed -n \"${REGEX}=\" \"$FILE\" | head -1)
  if [ \"$LINENUM\" ]; then
    sed -i \"${LINENUM}c${LINE}\" \"$FILE\"
    exit 0
  else
    echo -n \"no match\"
    exit 0
  fi
fi

echo \"script error\" 1>&2
exit 1
"
          (some->> regexp re-pattern-to-sed)
          (some->> path path-escape)
          (str line-num)
          (str line)))

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
  (format "
REGEX=\"%s\"
FILE=\"%s\"
LINENUM=\"%s\"

if [ ! -f \"$FILE\" ]; then
  echo -n \"File not found.\" 1>&2
  exit 1
fi

if [ ! -r \"$FILE\" ]; then
  echo -n \"File not readable.\" 1>&2
  exit 1
fi

LINECOUNT=$(wc -l \"$FILE\" | awk '{print $1}')

# :get by linenum
if [ \"$LINENUM\" ]; then
  if [ \"$LINENUM\" -gt \"$LINECOUNT\" ]; then
    echo -n \"No line number $LINENUM in file.\" 1>&2
    exit 2
  elif [ \"$LINENUM\" -lt \"-$LINECOUNT\" ]; then
    echo -n \"No line number $LINENUM in file.\" 1>&2
    exit 2
  elif [ \"$LINENUM\" -lt 0 ]; then
    LINENUM=$((LINECOUNT + LINENUM + 1))
  fi
  echo $LINENUM
  sed -n \"${LINENUM}p\" \"$FILE\"
  exit 0
fi

# :get by regexp
if [ \"$REGEX\" ]; then
  LINENUMS=$(sed -n \"${REGEX}=\" \"$FILE\")
  if [ \"$LINENUMS\" ]; then
    SED_LP_CMD=$(echo $LINENUMS | sed 's/ /p;/g' | sed 's/$/p;/g')
    LINECONTENTS=$(sed -n \"$SED_LP_CMD\" \"$FILE\")
    echo $LINENUMS
    echo \"$LINECONTENTS\"
    exit 0
  else
    echo -n \"no match\"
    exit 0
  fi
fi

echo \"script error\" 1>&2
exit 1
"
          (some->> regexp re-pattern-to-sed)
          (some->> path path-escape)
          (str line-num)))

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
  "# line-in-file

  Manage lines in text files

  ## Overview

   * Get lines from a file, by line number or matching regular expression
   * Ensure lines are in a file
   * Ensure lines are not in a file
   * Works with single lines only. Does not insert/replace blocks of text

  ## Usage

   `(line-in-file command opts)`

  `command` is `:get`, `:present` or `:absent`

  ## Options

  The following are the keys of the entries in the hashmap passed into `opts`

  | Key    | Value  |
  |--------|--------|
  |path|The location on the remote system of the file to process|
  |line-num|The line number (1 offset) in the file to get/replace|
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

(defn line-in-file [& args]
  (binding [state/*form* (concat '(line-in-file) args)]
    (output/print-form state/*form*)
    (apply line-in-file* args)))
