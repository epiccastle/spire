(ns spire.eval
  (:require [sci.core :as sci]))

(def context "var to keep track in which environment we are evaluating: :clojure or :sci"
  (sci/new-dynamic-var 'context :clojure))

(defn binding-sym []
  (if (= :sci @context)
    'binding
    'sci.core/binding))

(defmacro binding [binding-pairs & body]
  (concat [(binding-sym) binding-pairs] body))

#_ (macroexpand-1 '(binding [a 1 b 2] (foo) (bar)))

(defn deref-sym []
  (if (= :sci @context)
    'identity
    'deref))

(defmacro deref [& body]
  (concat [(deref-sym)] body))
