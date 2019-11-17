(ns spire.config
  (:require [clojure.java.io :as io])
  (:import [java.util Base64]))

(def libs-set ["sunec.lib" "sunec.dll" "libsunec.dylib" "libsunec.so" "libspire.so"])

(defn path-split
  "give a full path filename, return a tuple of
  [path basename]

  eg \"blog/posts/1/main.yml\" -> [\"blog/posts/1\" \"main.yml\"]
  "
  [filename]
  (let [file (io/file filename)]
    [(.getParent file) (.getName file)]))

(defn path-join
  "given multiple file path parts, join them all together with the
  file separator"
  [& parts]
  (.getPath (apply io/file parts)))

(defn setup
  "Copy any of the bundled dynamic libs from resources to the
  run time lib directory"
  [libs-dir]
  (doseq [filename libs-set]
    (when-let [file (io/resource filename)]
      (let [[_ name] (path-split (.getFile file))
            dest-path (path-join libs-dir name)]
        ;; writing to a library while running its code can result in segfault
        ;; TODO: version the c library code.
        (when (not (.exists (io/file dest-path)))
          ;; (println "installing:" name "to:" )
          (io/copy (io/input-stream file) (io/file dest-path)))))))

(defn init! []
  (let [native-image?
        (and (= "Substrate VM" (System/getProperty "java.vm.name"))
             (= "runtime" (System/getProperty "org.graalvm.nativeimage.imagecode")))
        home-dir (System/getenv "HOME")
        config-dir (path-join home-dir ".spire")
        libs-dir (path-join config-dir "libs")]
    (.mkdirs (io/as-file libs-dir))

    (when native-image?
      (setup libs-dir)
      (System/setProperty "java.library.path" libs-dir))))
