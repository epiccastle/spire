(ns spire.state
  (:require [sci.impl.vars :as vars])
  )

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
;;(def ^:dynamic *sessions* [])
(def ^:dynamic *sessions* (vars/dynamic-var '*session* []))
(.setDynamic #'*sessions* false)
(alter-meta! #'*sessions* assoc :dynamic false)

;; threadlocal *connections* holds:
;; all the host strings needed to complete a step or action
;; this will be the complete set of hosts to operate on and will be
;; the same in parallel or lockstep modes
;;(def ^:dynamic *connections* [])
(def ^:dynamic *connections* (vars/dynamic-var '*connections* []))
(.setDynamic #'*connections* false)
(alter-meta! #'*connections* assoc :dynamic false)

;; threadlocal *form* holds:
;; the unevaluated clojure form that is presently executing
;; used to print updates on progress to the output
(def ^:dynamic *form* (vars/dynamic-var '*form* nil))
(.setDynamic #'*form* false)
(alter-meta! #'*form* assoc :dynamic false)
