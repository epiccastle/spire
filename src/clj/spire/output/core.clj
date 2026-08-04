(ns spire.output.core
  (:require [spire.state :as state]))

(set! *warn-on-reflection* true)

(defmulti print-thread
  (fn [driver] driver))

(defmulti print-form
  (fn [driver file form file-meta host-config] driver))

(defmulti print-result
  (fn [driver file form file-meta host-config result] driver))

(defmulti debug-result
  (fn [driver file form file-meta host-config result] driver))

(defmulti print-progress
  (fn [driver file form form-meta host-string {:keys [progress context] :as data}] driver))

(defmulti print-streams
  (fn [driver file form form-meta host-string stdout stderr] driver))

(defmulti worker-thread-start
  (fn [driver] driver))

(defmulti worker-thread-stop
  (fn [driver worker] driver))

(defmacro with-output [driver & body]
  `(let [worker# (worker-thread-start ~driver)]
     (binding [state/*output-module* ~driver]
       (try
         ~@body
         (finally
           (worker-thread-stop ~driver worker#))))))

;; default implementation prints nothing
(defmethod print-form :default [driver file form file-meta host-config])

(defmethod print-result :default [driver file form file-meta host-config result])

(defmethod debug-result :default [driver file form file-meta host-config result])

(defmethod print-progress :default [driver file form form-meta host-string {:keys [progress context]}])

(defmethod print-streams :default [driver file form form-meta host-string stdout stderr]
  (when stdout
    (print stdout)
    (.flush ^java.io.Writer *out*))
  (when stderr
    (binding [*out* *err*]
      (print stderr)
      (.flush ^java.io.Writer *err*))))

(defmethod worker-thread-start :default [driver])

(defmethod worker-thread-stop :default [driver worker])
