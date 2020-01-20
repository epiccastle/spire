(ns spire.state)

;; all the open ssh connections
;; keys => host-string
;; value => session
(defonce ssh-connections
  (atom {}))

(def ^:dynamic *host-config* nil)

(def ^:dynamic *connection* nil)

(defn get-host-config [] *host-config*)
(defn get-connection [] *connection*)
