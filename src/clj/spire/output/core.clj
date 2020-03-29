(ns spire.output.core)

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
