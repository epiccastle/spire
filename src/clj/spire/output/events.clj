(ns spire.output.ecents)

(set! *warn-on-reflection* true)

(defmethod output/print-thread :events [driver])

(defmethod output/print-form :events [driver file form file-meta host-config])

(defmethod output/print-result :events [driver file form file-meta host-config result])

(defmethod output/debug-result :events [driver file form file-meta host-config result])

(defmethod output/print-progress :events [driver file form form-meta host-string {:keys [progress context]}])
