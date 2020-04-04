(ns spire.output.events
  (:require [spire.output.core :as output]
            [puget.printer :as puget]
            [clojure.core.async :refer [<!! put! go chan thread]]))

(set! *warn-on-reflection* true)

(def prn-chan (chan))

(defmethod output/print-thread :events [_]
  (thread
    (loop []
      (puget/cprint (<!! prn-chan))
      (recur))))

(defmethod output/print-form :events
  [_ file form file-meta host-config]
  (put! prn-chan ['start file form file-meta host-config]))

(defmethod output/print-result :events
  [_ file form file-meta host-config result]
  (put! prn-chan ['result file form file-meta host-config result]))

(defmethod output/debug-result :events
  [_ file form file-meta host-config result]
  (put! prn-chan ['debug file form file-meta host-config result]))

(defmethod output/print-progress :events
  [_ file form form-meta host-string {:keys [progress context]}]
  (put! prn-chan ['progress file form form-meta host-string progress context]))
