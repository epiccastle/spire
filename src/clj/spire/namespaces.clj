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
            [spire.local]
            [spire.remote]
            [clojure.tools.cli]
            [clojure.java.shell]
            [clojure.edn]
            [clojure.string]
            [clojure.set]
            [clojure.java.io]
            [clojure.data.json]
            [sci.core :as sci]
            [clj-http.lite.core]
            [clj-http.lite.client]
            [clj-http.lite.links]
            [clj-http.lite.util]
            [edamame.core]
            [spire.sci :refer [make-sci-bindings]]))

(def all-modules
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

   'shell* shell/shell*
   'shell (with-meta @#'shell/shell {:sci/macro true})

   'local (with-meta @#'transport/local {:sci/macro true})
   'ssh (with-meta @#'transport/ssh {:sci/macro true})
   'ssh-group (with-meta @#'transport/ssh-group {:sci/macro true})

   'on-os (with-meta @#'facts/on-os {:sci/macro true})
   'on-shell (with-meta @#'facts/on-shell {:sci/macro true})
   'on-distro (with-meta @#'facts/on-distro {:sci/macro true})

   'changed? utils/changed?
   'failed? (with-meta @#'utils/failed? {:sci/macro true})
   'debug (with-meta @#'utils/debug {:sci/macro true})

   }
  )

(def bindings all-modules)

(def namespaces
  {
   'clojure.core {'println println
                  'prn prn
                  'pr pr
                  'slurp slurp
                  'future (with-meta @#'clojure.core/future {:sci/macro true})
                  'future-call clojure.core/future-call
                  '*in* (sci/new-dynamic-var '*in* *in*)
                  '*out* (sci/new-dynamic-var '*out* *out*)
                  '*err* (sci/new-dynamic-var '*err* *err*)
                  }
   'spire.transport (make-sci-bindings spire.transport)
   'spire.ssh (make-sci-bindings spire.ssh)
   'spire.utils (make-sci-bindings spire.utils)
   'spire.facts (make-sci-bindings spire.facts)
   'spire.state (make-sci-bindings spire.state)
   'spire.context (make-sci-bindings spire.context)
   'spire.local (make-sci-bindings spire.local)
   'spire.remote (make-sci-bindings spire.remote)
   'spire.output.core (make-sci-bindings spire.output.core)

   'clojure.java.io (make-sci-bindings clojure.java.io)
   'clojure.tools.cli (make-sci-bindings clojure.tools.cli)
   'clojure.set (make-sci-bindings clojure.set)
   'clojure.string (make-sci-bindings clojure.string)
   'clojure.data.json (make-sci-bindings clojure.data.json
                                         {:only #{read-json read-str read
                                                  write-json write-str write}})

   ;; modules
   'spire.modules all-modules
   'spire.module.apt (make-sci-bindings spire.module.apt)
   'spire.module.attrs (make-sci-bindings spire.module.attrs)
   'spire.module.authorized-keys (make-sci-bindings spire.module.authorized-keys)
   'spire.module.apt-repo (make-sci-bindings spire.module.apt-repo)
   'spire.module.curl (make-sci-bindings spire.module.curl)
   'spire.module.download (make-sci-bindings spire.module.download)
   'spire.module.group (make-sci-bindings spire.module.group)
   'spire.module.get-file (make-sci-bindings spire.module.get-file)
   'spire.module.line-in-file (make-sci-bindings spire.module.line-in-file)
   'spire.module.mkdir (make-sci-bindings spire.module.mkdir)
   'spire.module.pkg (make-sci-bindings spire.module.pkg)
   'spire.module.rm (make-sci-bindings spire.module.rm)
   'spire.module.service (make-sci-bindings spire.module.service)
   'spire.module.shell (make-sci-bindings spire.module.shell)
   'spire.module.stat (make-sci-bindings spire.module.stat)
   'spire.module.sudo (make-sci-bindings spire.module.sudo)
   'spire.module.sysctl (make-sci-bindings spire.module.sysctl)
   'spire.module.upload (make-sci-bindings spire.module.upload)
   'spire.module.user (make-sci-bindings spire.module.user)

   'clj-http.lite.client (make-sci-bindings clj-http.lite.client {:exclusions #{with-connection-pool}})
   'clj-http.lite.core (make-sci-bindings clj-http.lite.core)
   'clj-http.lite.links (make-sci-bindings clj-http.lite.links)
   'clj-http.lite.util (make-sci-bindings clj-http.lite.links {:exclusions #{base64-encode}})

   'edamame.core (make-sci-bindings edamame.core)
   'clojure.edn (make-sci-bindings clojure.edn)
   'clojure.java.shell (make-sci-bindings clojure.java.shell)}
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
