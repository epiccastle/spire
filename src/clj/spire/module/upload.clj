(ns spire.module.upload
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn preflight [opts]
  nil)

(defn process-result [opts result]
  result
  )

(defn md5-local-dir [src]
  (->> (file-seq (io/file src))
       (filter #(.isFile %))
       (map (fn [f] [(.getName f) (digest/md5 f)]))
       (into {})))

#_ (md5-local-dir "test/")
#_ (file-seq (io/file "test/"))

(defn md5-remote-dir [run dest]
  (some-> (run (format "find \"%s\" -type f -exec md5sum {} \\;" dest))
          string/split-lines
          (->> (map #(string/split % #"\s+" 2))
               (map reverse)
               (into {}))))

(defn md5-file [run dest]
  (some-> (run (format "%s -b \"%s\"" "md5sum" dest))
          (string/split #"\s+")
          first))

(utils/defmodule upload [{:keys [src dest owner group mode attrs] :as opts}]
  [host-string session]
  (or
   (preflight opts)
   (->> (let [run (fn [command]
                    (let [{:keys [out exit]}
                          (ssh/ssh-exec session command "" "UTF-8" {})]
                      (when (zero? exit)
                        (string/trim out))))
              local-md5 (digest/md5 src)
              remote-md5 (some-> (run (format "%s -b \"%s\"" "md5sum" dest))
                                 (string/split #"\s+")
                                 first)]
          (let [copied?
                (if (= local-md5 remote-md5)
                  false
                  (do
                    (scp/scp-to session src dest :progress-fn (fn [& args] (output/print-progress host-string args)))
                    true))
                passed-attrs? (or owner group mode attrs)]
            (if (not passed-attrs?)
              ;; just copied
              [copied? nil]

              ;; run attrs
              [copied? (attrs/set-attrs
                        session
                        {:path dest
                         :owner owner
                         :group group
                         :mode mode
                         :attrs attrs})])))
        (process-result opts))))







(type (io/file "spire"))
