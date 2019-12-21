(ns spire.compare
  (:require [spire.local :as local]
            [spire.remote :as remote]
            [clojure.java.io :as io]))


(defn same-files [local-md5s remote-md5s]
  (->> (for [[f md5] (filter #(= (:type %) :file) local-md5s)]
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


#_ (defn runner []
  (fn [cmd] (->> cmd
                 (clojure.java.shell/sh "bash" "-c")
                 :out)))

(defn same-file-content [local remote]
  (->> (for [[f {:keys [md5sum]}] (filter #(= (:type (second %)) :file) local)]
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
                             (filter #(= (:type (second %)) :file))
                             (map first)
                             ;;(map #(.getPath (io/file local-path %)))
                             (filter (complement identical-content))
                             (into #{}))
        remote-to-local (->> remote
                             (filter #(= (:type (second %)) :file))
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
