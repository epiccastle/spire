(ns spire.output.quiet
  (:require [spire.output.core :as output]))

(set! *warn-on-reflection* true)

(defmethod output/print-thread :quiet [driver])

(defmethod output/print-form :quiet [driver file form file-meta host-config])

(defmethod output/print-result :quiet [driver file form file-meta host-config result])

(defmethod output/debug-result :quiet [driver file form file-meta host-config result])

(defmethod output/print-progress :quiet [driver file form form-meta host-string {:keys [progress context]}])

(defmethod output/print-streams :quiet [driver file form form-meta host-string stdout stderr]
  (prn 'stdout stdout 'stderr stderr)
  (when stdout (print stdout) (.flush ^java.io.OutputStreamWriter *out*))
  (when stderr (binding [*out* *err*] (print stderr) (.flush ^java.io.PrintWriter *err*)))
  )
