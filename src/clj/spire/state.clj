(ns spire.state)

;; all the open ssh connections
;; keys => host-string
;; value => session
(defonce ssh-connections
  (atom {}))

;; the host config hashmap for the present executing context
(def ^:dynamic *host-config* nil)

;; the ssh session
(def ^:dynamic *connection* nil)

;; the execution context. Used for priviledge escalation currently
(def ^:dynamic *shell-context* nil)

(defn get-host-config [] *host-config*)
(defn get-connection [] *connection*)
(defn get-shell-context [] *shell-context*)
