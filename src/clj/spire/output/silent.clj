(ns spire.output.silent
  (:require [spire.output.core :as output]))

(set! *warn-on-reflection* true)

(defmethod output/print-thread :silent [driver])

(defmethod output/print-form :silent [driver file form file-meta host-config])

(defmethod output/print-result :silent [driver file form file-meta host-config result])

(defmethod output/debug-result :silent [driver file form file-meta host-config result])

(defmethod output/print-progress :silent [driver file form form-meta host-string {:keys [progress context]}])

(defmethod output/print-streams :silent [driver file form form-meta host-string stdout stderr])
