(ns spire.state)

;; all the open ssh connections
;; keys => host-string
;; value => session
(defonce ssh-connections
  (atom {}))

;; threadlocal *sessions* holds:
;; the currect execution sessions that each idempotent call will use
;; in lockstep mode: this will be all the current connection
;; in parallel mode: this will usually be one of the current connections
;; format is a vector of host strings
(def ^:dynamic *sessions* [])

;; threadlocal *connections* holds:
;; all the host strings needed to complete a step or action
;; this will be the complete set of hosts to operate on and will be
;; the same in parallel or lockstep modes
(def ^:dynamic *connections* [])

;; threadlocal *form* holds:
;; the unevaluated clojure form that is presently executing
;; used to print updates on progress to the output
(def ^:dynamic *form* nil)

(def ^:dynamic *host-string* nil)

(def ^:dynamic *host-config* nil)

(def ^:dynamic *connection* nil)


;; some helper functions for use inside sci evaluated code
(defn get-sessions [] *sessions*)
(defn get-connections [] *connections*)

(defn get-host-config [] *host-config*)
