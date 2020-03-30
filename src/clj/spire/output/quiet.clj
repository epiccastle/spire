(ns spire.output.quiet
  (:require [spire.output.core :as output]))

(set! *warn-on-reflection* true)

(defmethod output/print-thread :quiet [driver])

(defmethod output/print-form :quiet [driver file form file-meta host-config])

(defmethod output/print-result :quiet [driver file form file-meta host-config result])

(defmethod output/debug-result :quiet [driver file form file-meta host-config result])

(defmethod output/print-progress :quiet [driver file form form-meta host-string {:keys [progress context]}])
