(ns spire.namespaces
  (:require [spire.system :as system]
            [spire.utils :as utils]
            [spire.transfer :as transfer]
            [spire.transport :as transport]
            [spire.state :as state]
            )
  )

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
   ;;'ln system/ln
   ;;'mkdir system/mkdir

   ;;'git vcs/git

   ;;'copy transfer/copy
   ;;'template transfer/template

   'ssh (with-meta @#'transport/ssh {:sci/macro true})
   'ssh-group (with-meta @#'transport/ssh-group {:sci/macro true})
   'ssh-parallel (with-meta @#'transport/ssh-parallel {:sci/macro true})
   'on (with-meta @#'transport/on {:sci/macro true})

   'binding (with-meta @#'clojure.core/binding {:sci/macro true})
   })

(def namespaces
  {
   'spire.transfer {'ssh-line transfer/ssh-line
                    'ssh (with-meta @#'transfer/ssh {:sci/macro true})

                    }
   'clojure.core {'binding (with-meta binding* {:sci/macro true})
                  'push-thread-bindings clojure.core/push-thread-bindings
                  'pop-thread-bindings clojure.core/pop-thread-bindings
                  ;;                  'var (with-meta @#'clojure.core/var {:sci/macro true})
                  'println println
                  'prn prn
                  'pr pr

                  'future (with-meta @#'clojure.core/future {:sci/macro true})
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
                 '*sessions* #'state/*sessions*
                 '*connections* #'state/*connections*
                 }
   ;; 'spire.system {
   ;;                '*form* #'system/*form*
   ;;                'apt* #'system/apt*
   ;;                }
   ;; 'spire.output {
   ;;                'print-form spire.output/print-form
   ;;                }
   })
