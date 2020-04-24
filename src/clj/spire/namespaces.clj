(ns spire.namespaces
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.context :as context]
            [spire.transfer :as transfer]
            [spire.transport :as transport]
            [spire.state :as state]
            [spire.output.core :as output]
            [spire.facts :as facts]
            [spire.selmer :as selmer]
            [spire.module.curl :as curl]
            [spire.module.line-in-file :as line-in-file]
            [spire.module.get-file :as get-file]
            [spire.module.download :as download]
            [spire.module.upload :as upload]
            [spire.module.user :as user]
            [spire.module.apt :as apt]
            [spire.module.attrs :as attrs]
            [spire.module.apt-repo :as apt-repo]
            [spire.module.pkg :as pkg]
            [spire.module.rm :as rm]
            [spire.module.group :as group]
            [spire.module.mkdir :as mkdir]
            [spire.module.shell :as shell]
            [spire.module.sysctl :as sysctl]
            [spire.module.service :as service]
            [spire.module.authorized-keys :as authorized-keys]
            [spire.module.stat :as stat]
            [spire.module.sudo :as sudo]
            [clojure.tools.cli]
            [clojure.java.shell]
            [clojure.edn]
            [sci.core :as sci]
            [sci.impl.vars :as sci-vars]
            [clj-http.lite.core]
            [clj-http.lite.client]
            [clj-http.lite.links]
            [clj-http.lite.util]
            [edamame.core]
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
   'apt-repo* apt-repo/apt-repo*
   'apt-repo (with-meta @#'apt-repo/apt-repo {:sci/macro true})
   'attrs* attrs/attrs*
   'attrs (with-meta @#'attrs/attrs {:sci/macro true})
   'curl* curl/curl*
   'curl (with-meta @#'curl/curl {:sci/macro true})
   'pkg* pkg/pkg*
   'pkg (with-meta @#'pkg/pkg {:sci/macro true})
   'rm* rm/rm*
   'rm (with-meta @#'rm/rm {:sci/macro true})
   ;;'hostname system/hostname
   'line-in-file* line-in-file/line-in-file*
   'line-in-file (with-meta @#'line-in-file/line-in-file {:sci/macro true})
   ;;'copy copy/copy
   'upload* upload/upload*
   'upload (with-meta @#'upload/upload {:sci/macro true})

   'user* user/user*
   'user (with-meta @#'user/user {:sci/macro true})
   'gecos user/gecos

   'get-fact facts/get-fact
   'fetch-facts facts/fetch-facts

   'get-file* get-file/get-file*
   'get-file (with-meta @#'get-file/get-file {:sci/macro true})

   'mkdir* mkdir/mkdir*
   'mkdir (with-meta @#'mkdir/mkdir {:sci/macro true})

   'sysctl* sysctl/sysctl*
   'sysctl (with-meta @#'sysctl/sysctl {:sci/macro true})
   'service* service/service*
   'service (with-meta @#'service/service {:sci/macro true})

   'group* group/group*
   'group (with-meta @#'group/group {:sci/macro true})

   ;;'sudo* sudo/sudo*
   'sudo-user (with-meta @#'sudo/sudo-user {:sci/macro true})
   'sudo (with-meta @#'sudo/sudo {:sci/macro true})

   'selmer selmer/selmer

   'download* download/download*
   'download (with-meta @#'download/download {:sci/macro true})
   'authorized-keys* authorized-keys/authorized-keys*
   'authorized-keys (with-meta @#'authorized-keys/authorized-keys {:sci/macro true})

   'stat* stat/stat*
   'stat (with-meta @#'stat/stat {:sci/macro true})
   'other-exec? stat/other-exec?
   'other-read? stat/other-read?
   'other-write? stat/other-write?
   'group-exec? stat/group-exec?
   'group-read? stat/group-read?
   'group-write? stat/group-write?
   'user-exec? stat/user-exec?
   'user-read? stat/user-read?
   'user-write? stat/user-write?
   'mode-flags stat/mode-flags
   'exec? stat/exec?
   'readable? stat/readable?
   'writeable? stat/writeable?
   'directory? stat/directory?
   'block-device? stat/block-device?
   'char-device? stat/char-device?
   'symlink? stat/symlink?
   'fifo? stat/fifo?
   'regular-file? stat/regular-file?
   'socket? stat/socket?

   'slurp slurp

   'shell* shell/shell*
   'shell (with-meta @#'shell/shell {:sci/macro true})

   ;;'ln (system/ln
   ;;'mkdir system/mkdir

   ;;'git vcs/git

   ;;'copy transfer/copy
   ;;'template transfer/template

   'ssh (with-meta @#'transport/ssh {:sci/macro true})
   'ssh-group (with-meta @#'transport/ssh-group {:sci/macro true})

   'on-os (with-meta @#'facts/on-os {:sci/macro true})
   'on-shell (with-meta @#'facts/on-shell {:sci/macro true})
   'on-distro (with-meta @#'facts/on-distro {:sci/macro true})

   'changed? utils/changed?
   'failed? (with-meta @#'utils/failed? {:sci/macro true})
   'debug (with-meta @#'utils/debug {:sci/macro true})

   ;; '*command-line-args* (sci/new-dynamic-var '*command-line-args* *command-line-args*)
   '*in* (sci/new-dynamic-var '*in* *in*)
   '*out* (sci/new-dynamic-var '*out* *out*)
   '*err* (sci/new-dynamic-var '*err* *err*)
   })

(def namespaces
  {
   'spire.transfer {'ssh (with-meta @#'transfer/ssh {:sci/macro true})}
   'clojure.core { ;;'binding (with-meta binding* {:sci/macro true})
                  ;;'push-thread-bindings clojure.core/push-thread-bindings
                  ;;'pop-thread-bindings clojure.core/pop-thread-bindings
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
                     'open-connection transport/open-connection
                     'close-connection transport/close-connection
                     'get-connection transport/get-connection
                     'flush-out transport/flush-out
                     'safe-deref transport/safe-deref
                     }
   'spire.ssh {'host-config-to-string ssh/host-config-to-string
               'host-config-to-connection-key ssh/host-config-to-connection-key
               'host-description-to-host-config ssh/host-description-to-host-config
               }
   'spire.utils {'colour utils/colour
                 'defmodule (with-meta @#'utils/defmodule {:sci/macro true})
                 'wrap-report (with-meta @#'utils/wrap-report {:sci/macro true})

                 }
   'spire.facts {'get-fact facts/get-fact
                 'replace-facts-user! facts/replace-facts-user!
                 'update-facts! facts/update-facts!
                 }
   'spire.state {
                 'host-config state/host-config
                 'connection state/connection
                 'shell-context state/shell-context
                 'ssh-connections state/ssh-connections
                 'output-module state/output-module
                 'get-host-config state/get-host-config
                 'get-connection state/get-connection
                 'get-shell-context state/get-shell-context
                 'get-output-module state/get-output-module
                 }

   'spire.context {'context context/context
                   'binding-sym context/binding-sym
                   'binding* (with-meta @#'context/binding* {:sci/macro true})
                   'deref-sym context/deref-sym
                   'deref* (with-meta @#'context/deref* {:sci/macro true})}

   'spire.output.core {
                       'print-form output/print-form
                       'print-result output/print-result
                       'debug-result output/debug-result
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

   ;; modules
   'spire.module.apt {'apt* apt/apt*
                      'apt (with-meta @#'apt/apt {:sci/macro true})}
   'spire.module.attrs {'attrs* attrs/attrs*
                        'attrs (with-meta @#'attrs/attrs {:sci/macro true})}
   'spire.module.authorized-keys {'authorized-keys* authorized-keys/authorized-keys*
                                  'authorized-keys (with-meta @#'authorized-keys/authorized-keys {:sci/macro true})}
   'spire.module.apt-repo {'apt-repo* apt-repo/apt-repo*
                           'apt-repo (with-meta @#'apt-repo/apt-repo {:sci/macro true})}
   'spire.module.curl {'curl* curl/curl*
                       'curl (with-meta @#'curl/curl {:sci/macro true})}
   'spire.module.download {'download* download/download*
                           'download (with-meta @#'download/download {:sci/macro true})}
   'spire.module.group {'group* group/group*
                        'group (with-meta @#'group/group {:sci/macro true})}
   'spire.module.get-file {'get-file* get-file/get-file*
                           'get-file (with-meta @#'get-file/get-file {:sci/macro true})}
   'spire.module.line-in-file {'line-in-file* line-in-file/line-in-file*
                               'line-in-file (with-meta @#'line-in-file/line-in-file {:sci/macro true})}
   'spire.module.mkdir {'mkdir* mkdir/mkdir*
                        'mkdir (with-meta @#'mkdir/mkdir {:sci/macro true})}
   'spire.module.pkg {'pkg* pkg/pkg*
                      'pkg (with-meta @#'pkg/pkg {:sci/macro true})}
   'spire.module.rm {'rm* rm/rm*
                     'rm (with-meta @#'rm/rm {:sci/macro true})}
   'spire.module.service {'service* service/service*
                          'service (with-meta @#'service/service {:sci/macro true})}
   'spire.module.shell {'shell* shell/shell*
                        'shell (with-meta @#'shell/shell {:sci/macro true})}
   'spire.module.stat {'stat* stat/stat*
                       'stat (with-meta @#'stat/stat {:sci/macro true})}

   'spire.module.sudo {'requires-password? sudo/requires-password?
                       'prefix-sudo-stdin sudo/prefix-sudo-stdin
                       'make-sudo-command sudo/make-sudo-command
                       'passwords sudo/passwords
                       'sudo-id sudo/sudo-id
                       'sudo-user (with-meta @#'sudo/sudo-user {:sci/macro true})
                       'sudo (with-meta @#'sudo/sudo {:sci/macro true})
                       }
   'spire.module.sysctl {'sysctl* sysctl/sysctl*
                         'sysctl (with-meta @#'sysctl/sysctl {:sci/macro true})}
   'spire.module.upload {'upload* upload/upload*
                         'upload (with-meta @#'upload/upload {:sci/macro true})}
   'spire.module.user {'user* user/user*
                       'user (with-meta @#'user/user {:sci/macro true})}

   'clj-http.lite.client
   {
    'update clj-http.lite.client/update
    'when-pos clj-http.lite.client/when-pos
    'parse-url clj-http.lite.client/parse-url
    'unexceptional-status? clj-http.lite.client/unexceptional-status?
    'wrap-exceptions clj-http.lite.client/wrap-exceptions
    'wrap-redirects clj-http.lite.client/wrap-redirects
    'follow-redirect clj-http.lite.client/follow-redirect
    'wrap-decompression clj-http.lite.client/wrap-decompression
    'wrap-output-coercion clj-http.lite.client/wrap-output-coercion
    'wrap-input-coercion clj-http.lite.client/wrap-input-coercion
    'content-type-value clj-http.lite.client/content-type-value
    'wrap-content-type clj-http.lite.client/wrap-content-type
    'wrap-accept clj-http.lite.client/wrap-accept
    'accept-encoding-value clj-http.lite.client/accept-encoding-value
    'wrap-accept-encoding clj-http.lite.client/wrap-accept-encoding
    'generate-query-string clj-http.lite.client/generate-query-string
    'wrap-query-params clj-http.lite.client/wrap-query-params
    'basic-auth-value clj-http.lite.client/basic-auth-value
    'wrap-basic-auth clj-http.lite.client/wrap-basic-auth
    'parse-user-info clj-http.lite.client/parse-user-info
    'wrap-user-info clj-http.lite.client/wrap-user-info
    'wrap-method clj-http.lite.client/wrap-method
    'wrap-form-params clj-http.lite.client/wrap-form-params
    'wrap-url clj-http.lite.client/wrap-url
    'wrap-unknown-host clj-http.lite.client/wrap-unknown-host
    'wrap-request clj-http.lite.client/wrap-request
    'request clj-http.lite.client/request
    'get clj-http.lite.client/get
    'head clj-http.lite.client/head
    'post clj-http.lite.client/post
    'put clj-http.lite.client/put
    'delete clj-http.lite.client/delete}

   'clj-http.lite.core
   {
    'parse-headers clj-http.lite.core/parse-headers
    'request clj-http.lite.core/request}

   'clj-http.lite.links
   {
    'read-link-params clj-http.lite.links/read-link-params
    'read-link-value clj-http.lite.links/read-link-value
    'read-link-headers clj-http.lite.links/read-link-headers
    'wrap-links clj-http.lite.links/wrap-links
    }

   'clj-http.lite.util
   {
    'utf8-bytes clj-http.lite.util/utf8-bytes
    'utf8-string clj-http.lite.util/utf8-string
    'url-decode clj-http.lite.util/url-decode
    'url-encode clj-http.lite.util/url-encode
    'to-byte-array clj-http.lite.util/to-byte-array
    'gunzip clj-http.lite.util/gunzip
    'gzip clj-http.lite.util/gzip
    'inflate clj-http.lite.util/inflate
    'deflate clj-http.lite.util/deflate}

   'edamame.core
   {
    'parse-string edamame.core/parse-string
    'parse-string-all edamame.core/parse-string-all
    }

   'clojure.edn
   {
    'read clojure.edn/read
    'read-string clojure.edn/read-string
    }

   'clojure.java.shell
   {
    'with-sh-dir (with-meta @#'clojure.java.shell/with-sh-dir {:sci/macro true})
    'with-sh-env (with-meta @#'clojure.java.shell/with-sh-env {:sci/macro true})
    'sh clojure.java.shell/sh}



   'sci.impl.vars
   {'current-file sci.impl.vars/current-file}}
  )

(def classes
  {'java.lang.System System
   'java.lang.Thread Thread
   'java.time.Clock java.time.Clock
   'java.time.DateTimeException java.time.DateTimeException
   'java.time.DayOfWeek java.time.DayOfWeek
   'java.time.Duration java.time.Duration
   'java.time.Instant java.time.Instant
   'java.time.LocalDate java.time.LocalDate
   'java.time.LocalDateTime java.time.LocalDateTime
   'java.time.LocalTime java.time.LocalTime
   'java.time.Month java.time.Month
   'java.time.MonthDay java.time.MonthDay
   'java.time.OffsetDateTime java.time.OffsetDateTime
   'java.time.OffsetTime java.time.OffsetTime
   'java.time.Period java.time.Period
   'java.time.Year java.time.Year
   'java.time.YearMonth java.time.YearMonth
   'java.time.ZonedDateTime java.time.ZonedDateTime
   'java.time.ZoneId java.time.ZoneId
   'java.time.ZoneOffset java.time.ZoneOffset
   'java.time.temporal.TemporalAccessor java.time.temporal.TemporalAccessor
   'java.time.format.DateTimeFormatter java.time.format.DateTimeFormatter
   'java.time.format.DateTimeFormatterBuilder java.time.format.DateTimeFormatterBuilder
   }
  )
