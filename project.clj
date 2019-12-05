(defproject spire #= (clojure.string/trim #= (slurp ".meta/VERSION"))
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.2"]

                 ;; vars branch
                 [borkdude/sci "0.0.11-alpha.13"]

                 ;; normal
                 ;;[borkdude/sci "0.0.11-alpha.14"]

                 [fipp "0.6.21"]
                 [mvxcvi/puget "1.2.0"]
                 [digest "1.4.9"]
                 [clj-time "0.15.2"]

                 ;; dev
                 [nrepl "0.6.0"]

                 [com.jcraft/jsch "0.1.55"]

                 ;; base64
                 [commons-codec/commons-codec "1.12"]
                 ]
  :plugins [[cider/cider-nrepl "0.21.1"]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/c"]
  :jvm-opts ["-Djava.library.path=./"]
  :main ^:skip-aot spire.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
