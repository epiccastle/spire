(defproject spire #= (clojure.string/trim #= (slurp ".meta/VERSION"))
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.6.532"]
                 [org.clojure/tools.cli "0.4.2"]
                 [borkdude/sci "0.0.12-alpha.7"]
                 [fipp "0.6.21"]
                 [mvxcvi/puget "1.2.0"]
                 [digest "1.4.9"]
                 [clj-time "0.15.2"]
                 [selmer "1.12.18"]

                 ;; dev
                 [nrepl "0.6.0"]

                 [com.jcraft/jsch "0.1.55"]

                 ;; base64
                 [commons-codec/commons-codec "1.12"]

                 ;; http
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 ]
  :plugins [[cider/cider-nrepl "0.21.1"]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/c"]

  :jvm-opts ["-Djava.library.path=./"]
  ;;:native-path "./"
  ;;:native-dependencies [[SpireUtils "libspire.so"]]

  :main ^:skip-aot spire.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
