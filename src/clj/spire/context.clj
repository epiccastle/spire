(ns spire.context
  (:require [sci.core :as sci]))

(def context "var to keep track in which environment we are evaluating: :clojure or :sci"
  (sci/new-dynamic-var 'context :clojure))

(defn binding-sym []
  (cond
    (= :babashka context) 'clojure.core/binding
    (= :sci @context) 'clojure.core/binding
    :default 'sci.core/binding))

(defmacro binding* [binding-pairs & body]
  (concat [(binding-sym) binding-pairs] body))

#_ (macroexpand-1 '(binding [a 1 b 2] (foo) (bar)))

(defn deref-sym []
  (cond
    (= :babashka context) 'clojure.core/identity
    (= :sci @context) 'clojure.core/identity
    :default 'clojure.core/deref))

(defmacro deref* [& body]
  (concat [(deref-sym)] body))
