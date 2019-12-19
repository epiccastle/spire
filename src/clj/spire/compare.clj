(ns spire.compare
  (:require [spire.local :as local]
            [spire.remote :as remote]
            [clojure.java.io :as io]))


(defn same-files [local-md5s remote-md5s]
  (->> (for [[f md5] local-md5s]
         (when (= md5 (get remote-md5s f))
           f))
       (filterv identity)))

#_ (same-files
    (md5-local-dir "test")
    (md5-remote-dir (runner) "/tmp/spire")
    )

(defn gather-file-sizes [src local-files identical-files]
  (let [file-set (clojure.set/difference (into #{} local-files) (into #{} identical-files))]
    (into {}
          (for [f file-set]
            [f (.length (io/file src f))]
            ))
    )
  )

#_
(gather-file-sizes "test" (keys (md5-local-dir "test")) '("files/copy/test.txt"))


(defn compare-local-and-remote [local-path remote-runner remote-path]
  (let [local-md5-files (local/path-md5sums local-path)
        remote-md5-files (remote/path-md5sums remote-runner remote-path)
        local-file? (.isFile (io/file local-path))
        remote-file? (and (= 1 (count remote-md5-files))
                          (= '("") (keys remote-md5-files)))
        identical-files (if (or local-file? remote-file?)
                          #{}
                          (->> (same-files local-md5-files remote-md5-files)
                               (filter identity)
                               (map #(.getPath (io/file local-path %)))
                               (into #{})))
        local-to-remote (->> local-md5-files
                             (map first)
                             (filter (complement identical-files))
                             (into #{}))
        remote-to-local (->> remote-md5-files
                             (map first)
                             (filter (complement identical-files))
                             (into #{}))
        local-to-remote-filesizes (->> local-to-remote
                                       (map (fn [f] [f (.length (io/file local-path f))]))
                                       (into {}))
        local-to-remote-total-size (->> local-to-remote-filesizes
                                        (map second)
                                        (apply +))

        ;; remote-to-local-filesizes

        ]
    {:local-md5 local-md5-files
     :remote-md5 remote-md5-files
     :local-file? local-file?
     :remote-file? remote-file?
     :identical identical-files
     :local-to-remote local-to-remote
     :remote-to-local remote-to-local

     :local-to-remote-filesizes local-to-remote-filesizes
     :local-to-remote-total-size local-to-remote-total-size
     :local-to-remote-max-filename-length (let [fnames (->> local-to-remote
                                                            (map #(count (.getName (io/file %)))))]
                                            (when (not (empty? fnames))
                                              (apply max fnames)))

     ;; :remote-to-local-filesize remote-to-local-filesizes
     }
    ))

#_ (defn runner []
  (fn [cmd] (->> cmd
                 (clojure.java.shell/sh "bash" "-c")
                 :out)))

#_
(compare-local-and-remote "test" (runner) "/tmp/spire")


(defn same-file-content [local remote]
  (->> (for [[f {:keys [md5sum]}] local]
         (when (= md5sum (get-in remote [f :md5sum]))
           f))
       (filterv identity)))

(defn compare [local-path remote-runner remote-path]
  {:local (local/path-full-info local-path)
   :remote (remote/path-full-info remote-runner remote-path)})

(defn add-transfer-set [{:keys [local remote] :as comparison}]
  (let [identical-content (->> (same-file-content local remote)
                               (filter identity)
                               ;;(map #(.getPath (io/file local-path %)))
                               (into #{}))
        local-to-remote (->> local
                             (map first)
                             ;;(map #(.getPath (io/file local-path %)))
                             (filter (complement identical-content))
                             (into #{}))
        remote-to-local (->> remote
                             (map first)
                             ;;(map #(.getPath (io/file local-path %)))
                             (filter (complement identical-content))
                             (into #{}))]
    (assoc comparison
           :identical-content identical-content
           :local-to-remote local-to-remote
           :remote-to-local remote-to-local)))

(defn compare-full-info [local-path remote-runner remote-path]
  (-> (compare local-path remote-runner remote-path)
      (add-transfer-set)))

(defn local-to-remote [{:keys [local-to-remote local]}]
  (let [sizes (->> local-to-remote
                   (select-keys local)
                   (map (fn [[k {:keys [size]}]] [k size]))
                   (into {}))
        total (apply + (vals sizes))]
    {:sizes sizes
     :total total}))

(defn remote-to-local [{:keys [remote-to-local remote]}]
  (let [sizes (->> remote-to-local
                   (select-keys remote)
                   (map (fn [[k {:keys [size]}]] [k size]))
                   (into {}))
        total (apply + (vals sizes))]
    {:sizes sizes
     :total total}))
