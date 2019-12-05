(ns spire.namespaces
  (:require [spire.system :as system]
            [spire.utils :as utils]
            [spire.transfer :as transfer]
            [spire.transport :as transport]
            [spire.state :as state]
            [spire.module.line-in-file :as line-in-file]
            ;;[sci.impl.vars :as vars]
            )
  )

#_ (defn internal-future [_ _ & body]
  `(let [f# (~'clojure.core/binding-conveyor-fn (fn [] ~@body))]
     (~'clojure.core/future-call f#)))

(defn binding*
  "This macro only works with symbols that evaluate to vars themselves. See `*in*` and `*out*` below."
  [_ _ bindings & body]
  `(do
     (let []
       (push-thread-bindings (hash-map ~@bindings))
       (try
         ~@body
         (finally
           (pop-thread-bindings))))))

(def bindings
  {'apt system/apt
   'hostname system/hostname
   'line-in-file line-in-file/line-in-file
   ;;'ln system/ln
   ;;'mkdir system/mkdir

   ;;'git vcs/git

   ;;'copy transfer/copy
   ;;'template transfer/template

   'ssh (with-meta @#'transport/ssh {:sci/macro true})
   'ssh-group (with-meta @#'transport/ssh-group {:sci/macro true})
   'ssh-parallel (with-meta @#'transport/ssh-parallel {:sci/macro true})
   'on (with-meta @#'transport/on {:sci/macro true})

   'future (with-meta transport/internal-future
             {:sci/macro true})

   ;;'binding (with-meta @#'clojure.core/binding {:sci/macro true})
   })

(def namespaces
  {
   'spire.transfer {'ssh-line transfer/ssh-line
                    'ssh (with-meta @#'transfer/ssh {:sci/macro true})

                    }
   ;; 'vars {'push-thread-bindings(with-meta @#'vars/push-thread-bindings {:sci/macro true}) }
   'clojure.core {
                  ;;'binding (with-meta binding* {:sci/macro true})

                  ;;'binding (with-meta vars/binding {:sci/macro true})
                  ;; {:type :sci/error, :row 1, :col 1, :message "sci.impl.vars.SciVar cannot be cast to clojure.lang.Var [at line 1, column 1]"}

                  ;;'binding (with-meta @#'vars/with-bindings {:sci/macro true})
                  ;; Exception in thread "main" clojure.lang.ExceptionInfo: Could not resolve symbol: vars/push-thread-bindings [at line , column ] {:type :sci/error, :row nil, :col nil}

                  ;;'binding (with-meta @#'vars/with-bindings {:sci/macro true})
                  ;;'binding (with-meta @#'vars/with-sci-bindings {:sci/macro true})

                  ;;'push-thread-bindings clojure.core/push-thread-bindings
                  ;;'pop-thread-bindings clojure.core/pop-thread-bindings
                  ;;                  'var (with-meta @#'clojure.core/var {:sci/macro true})
                  'println println
                  'prn prn
                  'pr pr

                  'future (with-meta transport/internal-future
                            {:sci/macro true})

                  ;; 'future (with-meta @#'clojure.core/future {:sci/macro true})
                  'future-call clojure.core/future-call

                  }
   'clojure.set {'intersection clojure.set/intersection
                 }
   'spire.transport {'connect transport/connect
                     'disconnect transport/disconnect

                     'flush-out transport/flush-out
                     'on (with-meta @#'transport/on {:sci/macro true})
                     }
   'spire.utils {'colour utils/colour}
   'spire.state {
                 ;;'*sessions* (vars/dynamic-var 'state/*sessions*)
                 ;;'*connections* (vars/dynamic-var 'state/*connections*)
                 '*sessions* state/*sessions*
                 '*connections* state/*connections*
                 }
   ;; 'spire.system {
   ;;                '*form* #'system/*form*
   ;;                'apt* #'system/apt*
   ;;                }
   ;; 'spire.output {
   ;;                'print-form spire.output/print-form
   ;;                }
   })
