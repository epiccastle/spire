(ns build
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'io.epiccastle/spire)

(defn- latest-version-tag
  "Return the most recent git tag that looks like a version string
  (e.g. 1.2.3 or v1.2.3). Returns nil if no matching tag is found."
  []
  (let [{:keys [exit out]} (b/process {:command-args
                                       ["git" "tag" "--list"
                                        "--sort=-creatordate"
                                        "v[0-9]*" "[0-9]*"]
                                       :out :capture})]
    (when (zero? exit)
      (some->> out
               str/split-lines
               (map str/trim)
               (filter #(re-matches #"v?\d+(\.\d+)*" %))
               first))))

(defn- head-at-tag?
  "Return true if the given git tag points at the same commit as HEAD."
  [tag]
  (let [resolve (fn [rev]
                  (let [{:keys [exit out]}
                        (b/process {:command-args ["git" "rev-parse" rev]
                                    :out :capture})]
                    (when (zero? exit) (str/trim out))))
        tag-sha (resolve (str tag "^{commit}"))
        head-sha (resolve "HEAD")]
    (and tag-sha head-sha (= tag-sha head-sha))))

(defn- compute-version []
  (if-let [tag (latest-version-tag)]
    (let [base (str/replace tag #"^v" "")]
      (if (head-at-tag? tag)
        base
        (str base "-SNAPSHOT")))
    "0.0.0-SNAPSHOT"))

(def version-tag (compute-version))
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def jar-file (format "target/%s-%s.jar" (name lib) version-tag))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version-tag
                :basis @basis
                :src-dirs ["src"]
                :scm {:url "https://github.com/epiccastle/spire"
                      :connection "scm:git:git://github.com/epiccastle/spire.git"
                      :developerConnection "scm:git:ssh://git@github.com/epiccastle/spire.git"
                      :tag (str "v" version-tag)}
                :pom-data
                [[:description "A Clojure library for using SSH in Clojure that is API compatible with bbssh"]
                 [:licenses
                  [:license
                   [:name "Eclipse Public License 2.0"]
                   [:url "https://www.eclipse.org/legal/epl-2.0/"]]]]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install [_]
  (clean nil)
  (jar nil)
  (b/install {:basis @basis
              :lib lib
              :version version-tag
              :jar-file jar-file
              :class-dir class-dir}))

(defn deploy [_]
  (clean nil)
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))

(defn version [_]
  (println version-tag))
