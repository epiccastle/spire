(defproject spire "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [fipp "0.6.21"]
                 [mvxcvi/puget "1.2.0"]
                 [digest "1.4.9"]
                 [clj-time "0.15.2"]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :main ^:skip-aot spire.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
