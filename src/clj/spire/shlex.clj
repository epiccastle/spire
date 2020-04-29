(ns spire.shlex)

;; write lexical analyzers for simple syntaxes resembling that of the
;; Unix shell. useful for splitting shell syntax or for parsing quoted
;; strings.

(def whitespace-chars (into #{} " \t\r\n"))

(defn read-char [input]
  [(first input) (rest input)])

#_ (read-char "ls -alF")

(defn skip-until [input pred]
  (loop [[c remain] (read-char input)]
    (if (newline-chars c)
      remain
      (recur (read-char remain)))))

#_ (skip-until "this is a test foo bar" newline-chars)

(defn read-double-quotes
  "given the input starting just after an opening double-quote, read and
  decode the string until closing double-quote. return the decoded string
  and the remaining text"
  [input]
  (loop [remain input
         output ""]
    (let [[c remain] (read-char remain)]
      (cond
        (= \\ c)
        (let [[n remain] (read-char remain)]
          (case n
            \\ (recur remain (str output "\\"))
            \" (recur remain (str output "\""))
            (recur remain (str output "\\" n))))

        (= \" c)
        [output remain]

        :else
        (recur remain (str output c))))))

(defn read-single-quotes
  "given the input starting just after an opening single-quote, read and
  decode the string until closing single-quote. return the decoded string
  and the remaining text"
  [input]
  (loop [remain input
         output ""]
    (let [[c remain] (read-char remain)]
      (cond
        (= \' c)
        [output remain]

        :else
        (recur remain (str output c))))))

(defn read-until-whitespace
  "given the input starting just after some whitespace, read and decode
  the string until the next legitimate whitespace. return the decoded string
  and the remaining text. remaining text includes the first whitespace"
  [input]
  (loop [remain input
         output ""]
    (let [[c remain] (read-char remain)]
      (cond
        (= \' c)
        (let [[qoutput qremain] (read-single-quotes remain)]
          (recur qremain (str output qoutput)))

        (= \" c)
        (let [[qoutput qremain] (read-double-quotes remain)]
          (recur qremain (str output qoutput)))

        (nil? c) ;; end of string
        [output remain]

        (whitespace-chars c)
        [output (cons c remain)]

        (= \\ c)
        (let [[c remain] (read-char remain)]
          (recur remain (str output c)))

        :else
        (recur remain (str output c))))))

(defn read-while-whitespace
  "given the input starting just after some whitespace starts, read all the
  whitespace until there is no more. return the whitespace string  and the
  remaining text."
  [input]
  (loop [remain input
         output ""]
    (let [[c remain] (read-char remain)]
      (cond
        (not (whitespace-chars c))
        [output (cons c remain)]

        :else
        (recur remain (str output c))))))

(defn parse [line]
  (loop [remain line
         output []]
    (if (empty? remain)
      output
      (let [[c remain] (read-char remain)]
        ;;(prn output)
        (cond
          (whitespace-chars c)
          (do ;;(println 1 c remain)
              (recur (second (read-while-whitespace remain)) output))

          (nil? c) ;; end of line
          output

          :else
          (do
            ;;(println 2 c remain)
            (let [[qoutput qremain] (read-until-whitespace (cons c remain))]
              (recur qremain (conj output qoutput)))))))))

#_ (parse "ls -alF")
