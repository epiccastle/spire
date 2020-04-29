(ns spire.shlex)

;; write lexical analyzers for simple syntaxes resembling that of the
;; Unix shell. useful for splitting shell syntax or for parsing quoted
;; strings.

(def comment-chars (into #{} "#"))
(def word-chars (into #{} "abcdfeghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"))
(def posix-chars (into #{} "ßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞ"))
(def extra-filename-chars (into #{} "~-./*?="))
(def whitespace-chars (into #{} " \t\r\n"))
(def quote-chars (into #{} "\"'"))
(def escape-chars (into #{} "\\"))
(def punctuation-chars (into #{} "();<>|&"))
(def newline-chars (into #{} "\n"))


(defn read-char [input]
  [(first input) (rest input)])

#_ (read-char "ls -alF")

(defn skip-until [input pred]
  (loop [[c remain] (read-char input)]
    (if (newline-chars c)
      remain
      (recur (read-char remain)))))

#_ (skip-until "this is a test foo bar" newline-chars)

(defn decode-escape-char
  "given the char token after an escape char, return its decoded form"
  [c]
  c)

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
  "given the input startinf just after some whitespace, read and decode
  the string until the next legitimate whitespace. return the decoded string
  and the remaining text"
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

        (whitespace-chars c)
        [output remain]

        :else
        (recur remain (str output c))))))
