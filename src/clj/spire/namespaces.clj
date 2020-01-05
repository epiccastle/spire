(ns spire.namespaces
  (:require [spire.system :as system]
            [spire.utils :as utils]
            [spire.transfer :as transfer]
            [spire.transport :as transport]
            [spire.state :as state]
            [spire.facts :as facts]
            [spire.module.line-in-file :as line-in-file]
            [spire.module.download :as download]
            [spire.module.upload :as upload]
            [spire.module.user :as user]
            [spire.module.apt :as apt]
            [spire.module.pkg :as pkg]
            [spire.module.group :as group]
            [spire.module.shell :as shell]
            [spire.module.authorized-keys :as authorized-keys]
            [clojure.tools.cli]
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
  {'apt apt/apt
   'pkg pkg/pkg
   ;;'hostname system/hostname
   'line-in-file line-in-file/line-in-file
   ;;'copy copy/copy
   'upload upload/upload

   'user user/user
   'gecos user/gecos

   'get-fact facts/get-fact
   'fetch-facts facts/fetch-facts

   'group group/group

   'download download/download
   'authorized-keys authorized-keys/authorized-keys

   'slurp slurp

   'shell shell/shell

   ;;'ln system/ln
   ;;'mkdir system/mkdir

   ;;'git vcs/git

   ;;'copy transfer/copy
   ;;'template transfer/template

   'ssh (with-meta @#'transport/ssh {:sci/macro true})
   'ssh-group (with-meta @#'transport/ssh-group {:sci/macro true})
   ;;'ssh-parallel (with-meta @#'transport/ssh-parallel {:sci/macro true})

   'on-os (with-meta @#'facts/on-os {:sci/macro true})

   'binding (with-meta @#'clojure.core/binding {:sci/macro true})
   })

(def namespaces
  {
   'spire.transfer {'ssh (with-meta @#'transfer/ssh {:sci/macro true})}
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
                     'safe-deref transport/safe-deref
                     ;;'on (with-meta @#'transport/on {:sci/macro true})
                     }
   'spire.utils {'colour utils/colour}
   'spire.facts {'get-fact facts/get-fact}
   'spire.state {
                 '*sessions* #'state/*sessions*
                 '*connections* #'state/*connections*
                 '*form* #'state/*form*
                 'get-sessions #'state/get-sessions

                 '*host-string* #'state/*host-string*
                 '*connection* #'state/*connection*
                 'ssh-connections state/ssh-connections
                 }

   'clojure.java.io {'file clojure.java.io/file
                     }

   'clojure.tools.cli {
                       'cli clojure.tools.cli/cli
                       'make-summary-part clojure.tools.cli/make-summary-part
                       'format-lines clojure.tools.cli/format-lines
                       'summarize clojure.tools.cli/summarize
                       'get-default-options clojure.tools.cli/get-default-options
                       'parse-opts clojure.tools.cli/parse-opts
                       }
   })
