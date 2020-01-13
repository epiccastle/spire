(ns spire.namespaces
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.transfer :as transfer]
            [spire.transport :as transport]
            [spire.state :as state]
            [spire.output :as output]
            [spire.facts :as facts]
            [spire.selmer :as selmer]
            [spire.module.line-in-file :as line-in-file]
            [spire.module.get-file :as get-file]
            [spire.module.download :as download]
            [spire.module.upload :as upload]
            [spire.module.user :as user]
            [spire.module.apt :as apt]
            [spire.module.apt-repo :as apt-repo]
            [spire.module.pkg :as pkg]
            [spire.module.group :as group]
            [spire.module.shell :as shell]
            [spire.module.sysctl :as sysctl]
            [spire.module.service :as service]
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
  {'apt* apt/apt*
   'apt (with-meta @#'apt/apt {:sci/macro true})
   'apt-repo apt-repo/apt-repo
   'pkg pkg/pkg
   ;;'hostname system/hostname
   'line-in-file line-in-file/line-in-file
   ;;'copy copy/copy
   'upload upload/upload

   'user user/user
   'gecos user/gecos

   'get-fact facts/get-fact
   'fetch-facts facts/fetch-facts

   'get-file get-file/get-file

   'sysctl sysctl/sysctl
   'service service/service

   'group group/group

   'selmer selmer/selmer

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
   'on-shell (with-meta @#'facts/on-shell {:sci/macro true})
   'on-distro (with-meta @#'facts/on-distro {:sci/macro true})

   'binding (with-meta @#'clojure.core/binding {:sci/macro true})

   'changed? utils/changed?
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
   'spire.ssh {'host-config-to-string ssh/host-config-to-string
               'host-config-to-connection-key ssh/host-config-to-connection-key
               'host-description-to-host-config ssh/host-description-to-host-config
               }
   'spire.utils {'colour utils/colour
                 'defmodule (with-meta @#'utils/defmodule {:sci/macro true})
                 'wrap-report (with-meta @#'utils/wrap-report {:sci/macro true})

                 }
   'spire.facts {'get-fact facts/get-fact}
   'spire.state {
                 '*sessions* #'state/*sessions*
                 '*connections* #'state/*connections*
                 '*form* #'state/*form*
                 'get-sessions #'state/get-sessions

                 '*host-config* #'state/*host-config*
                 '*host-string* #'state/*host-string*
                 '*connection* #'state/*connection*
                 'ssh-connections state/ssh-connections

                 'get-host-config state/get-host-config
                 }
   'spire.output {
                  'print-form output/print-form
                  'print-result output/print-result
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

   'clojure.string {
                    'trim clojure.string/trim
                    }
   'spire.module.apt {'apt* apt/apt*
                      'apt (with-meta @#'apt/apt {:sci/macro true})}
   })
