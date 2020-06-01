(ns spire.namespaces
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.context :as context]
            [spire.transfer :as transfer]
            [spire.transport :as transport]
            [spire.state :as state]
            [spire.output.core]
            [spire.output.default]
            [spire.output.events]
            [spire.output.quiet]
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
            [spire.default]
            [clojure.tools.cli]
            [clojure.java.shell]
            [clojure.edn]
            [clojure.stacktrace :as stacktrace]
            [clojure.string]
            [clojure.set]
            [clojure.java.io]
            [clojure.data.json]
            [sci.core :as sci]
            [clj-http.lite.core]
            [clj-http.lite.client]
            [clj-http.lite.links]
            [clj-http.lite.util]
            [cheshire.core]
            [cheshire.custom]
            [cheshire.exact]
            [cheshire.experimental]
            [cheshire.factory]
            [cheshire.generate]
            [cheshire.generate-seq]
            [cheshire.parse]
            [fipp.edn]
            [edamame.core]
            [spire.sci :refer [make-sci-bindings
                               make-sci-bindings-clean
                               sci-bind-macro
                               clojure-repl]]
            [sci.impl.namespaces :refer [copy-var]]
            [sci.impl.vars :as vars]))


(defmacro all-modules [ns]
  (let [ns-sym (gensym "ns-")]
    `(let  [~ns-sym (vars/->SciNamespace (quote ~ns) nil)]
       {(quote ~'apt*) (copy-var apt/apt* ~ns-sym)
        (quote ~'apt) (sci-bind-macro apt/apt ~ns-sym)
        (quote ~'apt-repo*) (copy-var apt-repo/apt-repo* ~ns-sym)
        (quote ~'apt-repo) (sci-bind-macro apt-repo/apt-repo ~ns-sym)
        (quote ~'attrs*) (copy-var attrs/attrs* ~ns-sym)
        (quote ~'attrs) (sci-bind-macro attrs/attrs ~ns-sym)
        (quote ~'curl*) (copy-var curl/curl* ~ns-sym)
        (quote ~'curl) (sci-bind-macro curl/curl ~ns-sym)
        (quote ~'pkg*) (copy-var pkg/pkg* ~ns-sym)
        (quote ~'pkg) (sci-bind-macro pkg/pkg ~ns-sym)
        (quote ~'rm*) (copy-var rm/rm* ~ns-sym)
        (quote ~'rm) (sci-bind-macro rm/rm ~ns-sym)
        ;;~'hostname system/hostname
        (quote ~'line-in-file*) (copy-var line-in-file/line-in-file* ~ns-sym)
        (quote ~'line-in-file) (sci-bind-macro line-in-file/line-in-file ~ns-sym)
        ;;~'copy copy/copy
        (quote ~'upload*) (copy-var upload/upload* ~ns-sym)
        (quote ~'upload) (sci-bind-macro upload/upload ~ns-sym)

        (quote ~'user*) (copy-var user/user* ~ns-sym)
        (quote ~'user) (sci-bind-macro user/user ~ns-sym)
        (quote ~'gecos) (copy-var user/gecos ~ns-sym)

        (quote ~'get-fact) (copy-var facts/get-fact ~ns-sym)
        (quote ~'fetch-facts) (copy-var facts/fetch-facts ~ns-sym)

        (quote ~'get-file*) (copy-var get-file/get-file* ~ns-sym)
        (quote ~'get-file) (sci-bind-macro get-file/get-file ~ns-sym)

        (quote ~'mkdir*) (copy-var mkdir/mkdir* ~ns-sym)
        (quote ~'mkdir) (sci-bind-macro mkdir/mkdir ~ns-sym)

        (quote ~'sysctl*) (copy-var sysctl/sysctl* ~ns-sym)
        (quote ~'sysctl) (sci-bind-macro sysctl/sysctl ~ns-sym)
        (quote ~'service*) (copy-var service/service* ~ns-sym)
        (quote ~'service) (sci-bind-macro service/service ~ns-sym)

        (quote ~'group*) (copy-var group/group* ~ns-sym)
        (quote ~'group) (sci-bind-macro group/group ~ns-sym)

        ;;~'sudo* sudo/sudo*
        (quote ~'sudo-user) (sci-bind-macro sudo/sudo-user ~ns-sym)
        (quote ~'sudo) (sci-bind-macro sudo/sudo ~ns-sym)

        (quote ~'selmer) (copy-var selmer/selmer ~ns-sym)

        (quote ~'download*) (copy-var download/download* ~ns-sym)
        (quote ~'download) (sci-bind-macro download/download ~ns-sym)
        (quote ~'authorized-keys*) (copy-var authorized-keys/authorized-keys* ~ns-sym)
        (quote ~'authorized-keys) (sci-bind-macro authorized-keys/authorized-keys ~ns-sym)

        (quote ~'stat*) (copy-var stat/stat* ~ns-sym)
        (quote ~'stat) (sci-bind-macro stat/stat ~ns-sym)
        (quote ~'other-exec?) (copy-var stat/other-exec? ~ns-sym)
        (quote ~'other-read?) (copy-var stat/other-read? ~ns-sym)
        (quote ~'other-write?) (copy-var stat/other-write? ~ns-sym)
        (quote ~'group-exec?) (copy-var stat/group-exec? ~ns-sym)
        (quote ~'group-read?) (copy-var stat/group-read? ~ns-sym)
        (quote ~'group-write?) (copy-var stat/group-write? ~ns-sym)
        (quote ~'user-exec?) (copy-var stat/user-exec? ~ns-sym)
        (quote ~'user-read?) (copy-var stat/user-read? ~ns-sym)
        (quote ~'user-write?) (copy-var stat/user-write? ~ns-sym)
        (quote ~'mode-flags) (copy-var stat/mode-flags ~ns-sym)
        (quote ~'exec?) (copy-var stat/exec? ~ns-sym)
        (quote ~'readable?) (copy-var stat/readable? ~ns-sym)
        (quote ~'writeable?) (copy-var stat/writeable? ~ns-sym)
        (quote ~'directory?) (copy-var stat/directory? ~ns-sym)
        (quote ~'block-device?) (copy-var stat/block-device? ~ns-sym)
        (quote ~'char-device?) (copy-var stat/char-device? ~ns-sym)
        (quote ~'symlink?) (copy-var stat/symlink? ~ns-sym)
        (quote ~'fifo?) (copy-var stat/fifo? ~ns-sym)
        (quote ~'regular-file?) (copy-var stat/regular-file? ~ns-sym)
        (quote ~'socket?) (copy-var stat/socket? ~ns-sym)

        (quote ~'shell*) (copy-var shell/shell* ~ns-sym)
        (quote ~'shell) (sci-bind-macro shell/shell ~ns-sym)

        (quote ~'local) (sci-bind-macro transport/local ~ns-sym)
        (quote ~'ssh) (sci-bind-macro transport/ssh ~ns-sym)
        (quote ~'ssh-group) (sci-bind-macro transport/ssh-group ~ns-sym)

        (quote ~'on-os) (sci-bind-macro facts/on-os ~ns-sym)
        (quote ~'on-shell) (sci-bind-macro facts/on-shell ~ns-sym)
        (quote ~'on-distro) (sci-bind-macro facts/on-distro ~ns-sym)

        (quote ~'changed?) (copy-var utils/changed? ~ns-sym)
        (quote ~'failed?) (sci-bind-macro utils/failed? ~ns-sym)
        (quote ~'debug) (sci-bind-macro utils/debug ~ns-sym)

        }))
  )

#_(macroexpand-1 '(all-modules user))

(def bindings (all-modules user)
  )

(defmacro redirect-out-to-sci [f]
  `(fn [& ~'args]
     (binding [*out* @sci/out]
       (apply ~f ~'args))))

(def namespaces
  {
   'clojure.core {'slurp slurp
                  'future (with-meta @#'clojure.core/future {:sci/macro true})
                  'future-call clojure.core/future-call
                  '*in* (sci/new-dynamic-var '*in* *in*)
                  '*out* (sci/new-dynamic-var '*out* *out*)
                  '*err* (sci/new-dynamic-var '*err* *err*)
                  }
   'clojure.main {'repl-requires
                  '[[clojure.repl :refer [dir doc]]
                    [clojure.pprint :refer [pprint]]
                    [spire.default :refer [push-ssh! set-ssh!
                                           push-local! set-local!
                                           pop! empty!]]]}
   'clojure.pprint (make-sci-bindings fipp.edn)
   'clojure.repl clojure-repl
   'clojure.stacktrace {'root-cause stacktrace/root-cause
                        'print-trace-element (redirect-out-to-sci stacktrace/print-trace-element)
                        'print-throwable (redirect-out-to-sci stacktrace/print-throwable)
                        'print-stack-trace (redirect-out-to-sci stacktrace/print-stack-trace)
                        'print-cause-trace (redirect-out-to-sci stacktrace/print-cause-trace)}

   'spire.transport (make-sci-bindings spire.transport)
   'spire.ssh (make-sci-bindings spire.ssh)
   'spire.utils (make-sci-bindings spire.utils)
   'spire.facts (make-sci-bindings spire.facts)
   'spire.state (make-sci-bindings-clean spire.state)
   'spire.context (make-sci-bindings-clean spire.context)
   'spire.local (make-sci-bindings spire.local)
   'spire.remote (make-sci-bindings spire.remote)
   'spire.default (make-sci-bindings spire.default)
   'spire.selmer (make-sci-bindings spire.selmer)
   'spire.output.core (make-sci-bindings spire.output.core)
   'spire.output.default (make-sci-bindings spire.output.default)
   'spire.output.events (make-sci-bindings spire.output.events)
   'spire.output.quiet (make-sci-bindings spire.output.quiet)


   'clojure.java.io (make-sci-bindings clojure.java.io)
   'clojure.tools.cli (make-sci-bindings clojure.tools.cli)
   'clojure.set (make-sci-bindings clojure.set)
   'clojure.string (make-sci-bindings clojure.string)
   'clojure.data.json (make-sci-bindings clojure.data.json
                                         {:only #{read-json read-str read
                                                  write-json write-str write}})

   ;; modules
   'spire.modules (all-modules spire.modules)
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
   'clojure.java.shell (make-sci-bindings clojure.java.shell)

   'cheshire.core (make-sci-bindings cheshire.core)
   'cheshire.custom (make-sci-bindings cheshire.custom)
   'cheshire.exact (make-sci-bindings cheshire.exact)
   'cheshire.experimental (make-sci-bindings cheshire.experimental)
   'cheshire.factory (make-sci-bindings cheshire.factory)
   'cheshire.generate (make-sci-bindings cheshire.generate)
   'cheshire.generate-seq (make-sci-bindings cheshire.generate-seq)
   'cheshire.parse (make-sci-bindings cheshire.parse)
   }
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
   'clojure.lang.ExceptionInfo clojure.lang.ExceptionInfo
   'java.io.BufferedReader java.io.BufferedReader
   'java.io.BufferedWriter java.io.BufferedWriter
   'java.io.ByteArrayInputStream java.io.ByteArrayInputStream
   'java.io.ByteArrayOutputStream java.io.ByteArrayOutputStream
   'java.io.File java.io.File
   'java.io.InputStream java.io.InputStream
   'java.io.IOException java.io.IOException
   'java.io.OutputStream java.io.OutputStream
   'java.io.FileReader java.io.FileReader
   'java.io.InputStreamReader java.io.InputStreamReader
   'java.io.PushbackInputStream java.io.PushbackInputStream
   'java.io.Reader java.io.Reader
   'java.io.SequenceInputStream java.io.SequenceInputStream
   'java.io.StringReader java.io.StringReader
   'java.io.StringWriter java.io.StringWriter
   'java.io.Writer java.io.Writer
   'java.lang.ArithmeticException java.lang.ArithmeticException
   'java.lang.AssertionError java.lang.AssertionError
   'java.lang.Boolean java.lang.Boolean
   'java.lang.Byte java.lang.Byte
   'java.lang.Character java.lang.Character
   'java.lang.Class java.lang.Class
   'java.lang.ClassNotFoundException java.lang.ClassNotFoundException
   'java.lang.Comparable java.lang.Comparable
   'java.lang.Double java.lang.Double
   'java.lang.Exception java.lang.Exception
   'java.lang.Integer java.lang.Integer
   'java.lang.Long java.lang.Long
   'java.lang.NumberFormatException java.lang.NumberFormatException
   'java.lang.Math java.lang.Math
   'java.lang.Object java.lang.Object
   'java.lang.Process java.lang.Process
   'java.lang.ProcessBuilder java.lang.ProcessBuilder
   'java.lang.ProcessBuilder$Redirect java.lang.ProcessBuilder$Redirect
   'java.lang.Runtime java.lang.Runtime
   'java.lang.RuntimeException java.lang.RuntimeException
   'java.lang.String java.lang.String
   'java.lang.StringBuilder java.lang.StringBuilder
   'java.lang.Throwable java.lang.Throwable
   'java.math.BigDecimal java.math.BigDecimal
   'java.math.BigInteger java.math.BigInteger
   'java.net.DatagramSocket java.net.DatagramSocket
   'java.net.DatagramPacket java.net.DatagramPacket
   'java.net.HttpURLConnection java.net.HttpURLConnection
   'java.net.InetAddress java.net.InetAddress
   'java.net.ServerSocket java.net.ServerSocket
   'java.net.Socket java.net.Socket
   'java.net.UnknownHostException java.net.UnknownHostException
   'java.net.URI java.net.URI
   'java.net.URL java.net.URL
   'java.net.URLEncoder java.net.URLEncoder
   'java.net.URLDecoder java.net.URLDecoder
   'java.nio.file.OpenOption java.nio.file.OpenOption
   'java.nio.file.CopyOption java.nio.file.CopyOption
   'java.nio.file.FileAlreadyExistsException java.nio.file.FileAlreadyExistsException
   'java.nio.file.FileSystem java.nio.file.FileSystem
   'java.nio.file.FileSystems java.nio.file.FileSystems
   'java.nio.file.Files java.nio.file.Files
   'java.nio.file.LinkOption java.nio.file.LinkOption
   'java.nio.file.NoSuchFileException java.nio.file.NoSuchFileException
   'java.nio.file.Path java.nio.file.Path
   'java.nio.file.Paths java.nio.file.Paths
   'java.nio.file.StandardCopyOption java.nio.file.StandardCopyOption
   'java.nio.file.attribute.FileAttribute java.nio.file.attribute.FileAttribute
   'java.nio.file.attribute.FileTime java.nio.file.attribute.FileTime
   'java.nio.file.attribute.PosixFilePermission java.nio.file.attribute.PosixFilePermission
   'java.nio.file.attribute.PosixFilePermissions java.nio.file.attribute.PosixFilePermissions
   'java.security.MessageDigest   java.security.MessageDigest
   'java.util.concurrent.LinkedBlockingQueue java.util.concurrent.LinkedBlockingQueue
   'java.util.jar.JarFile java.util.jar.JarFile
   'java.util.jar.JarEntry java.util.jar.JarEntry
   'java.util.jar.JarFile$JarFileEntry java.util.jar.JarFile$JarFileEntry
   'java.util.regex.Pattern java.util.regex.Pattern
   'java.util.Base64 java.util.Base64
   'java.util.Base64$Decoder java.util.Base64$Decoder
   'java.util.Base64$Encoder java.util.Base64$Encoder
   'java.util.Date java.util.Date
   'java.util.MissingResourceException java.util.MissingResourceException
   'java.util.Properties java.util.Properties
   'java.util.UUID java.util.UUID
   'java.util.concurrent.TimeUnit java.util.concurrent.TimeUnit
   'java.util.zip.InflaterInputStream java.util.zip.InflaterInputStream
   'java.util.zip.DeflaterInputStream java.util.zip.DeflaterInputStream
   'java.util.zip.GZIPInputStream java.util.zip.GZIPInputStream
   'java.util.zip.GZIPOutputStream java.util.zip.GZIPOutputStream
   'org.yaml.snakeyaml.error.YAMLException org.yaml.snakeyaml.error.YAMLException
   'clojure.lang.Delay clojure.lang.Delay
   'clojure.lang.MapEntry clojure.lang.MapEntry
   'clojure.lang.LineNumberingPushbackReader clojure.lang.LineNumberingPushbackReader
   'java.io.EOFException java.io.EOFException
   'java.io.PrintWriter java.io.PrintWriter
   'java.io.PushbackReader java.io.PushbackReader
   'clojure.lang.PersistentQueue clojure.lang.PersistentQueue
   'clojure.lang.IObj clojure.lang.IObj
   'clojure.lang.IEditableCollection clojure.lang.IEditableCollection
   }
  )

(def imports
  {'ArithmeticException 'java.lang.ArithmeticException
   'AssertionError 'java.lang.AssertionError
   'BigDecimal 'java.math.BigDecimal
   'Boolean 'java.lang.Boolean
   'Byte 'java.lang.Byte
   'Character 'java.lang.Character
   'Class 'java.lang.Class
   'ClassNotFoundException 'java.lang.ClassNotFoundException
   'Comparable 'java.lang.Comparable
   'Double 'java.lang.Double
   'Exception 'java.lang.Exception
   'IllegalArgumentException 'java.lang.IllegalArgumentException
   'Integer 'java.lang.Integer
   'Long 'java.lang.Long
   'Math 'java.lang.Math
   'NumberFormatException 'java.lang.NumberFormatException
   'Object 'java.lang.Object
   'Runtime 'java.lang.Runtime
   'RuntimeException 'java.lang.RuntimeException
   'Process 'java.lang.Process
   'ProcessBuilder 'java.lang.ProcessBuilder
   'String 'java.lang.String
   'StringBuilder 'java.lang.StringBuilder
   'System 'java.lang.System
   'Thread 'java.lang.Thread
   'Throwable 'java.lang.Throwable
   })
