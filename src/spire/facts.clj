(ns spire.facts
  (:require [clojure.string :as string]))

(defn ip-entry-process [[headline link & lines]]
  (let [[f b] (string/split headline #">")
        [num device flags] (string/split f #":\s+")
        device (keyword device)
        flags (-> flags
                  string/trim
                  (string/replace #"<" "")
                  (string/split #",")
                  (->> (map keyword)
                       (into #{})))
        opts (-> b
                 string/trim
                 (string/split #"\s+")
                 (->> (partition 2)
                      (map (fn [[k v]] [(keyword k)
                                        (try
                                          (Integer/parseInt v)
                                          (catch java.lang.NumberFormatException _
                                            (keyword v)))]))
                      (into {}))
                 )
        link (-> link
                 (string/split #"/")
                 second
                 (string/split #"\s+")
                 (->> (partition 2)
                      (map (fn [[k v]] [(keyword k)
                                        (try
                                          (Integer/parseInt v)
                                          (catch java.lang.NumberFormatException _
                                            v))]))
                      (into {}))
                 )
        addresses (-> lines
                      (->> (map string/trim)
                           (partition 2)
                           (map (fn [[overview flags]]
                                  (let [result (-> overview
                                                   (string/split #"\s+")
                                                   (->> (partition 2)
                                                        (map (fn [[k v]] [(keyword k)
                                                                          (try
                                                                            (Integer/parseInt v)
                                                                            (catch java.lang.NumberFormatException _
                                                                              v))]))
                                                        (into {})))
                                        flags (-> flags
                                                  string/trim
                                                  (string/split #"\s+")
                                                  (->> (partition 2)
                                                       (map (fn [[k v]] [(keyword k)
                                                                         (try
                                                                           (Integer/parseInt v)
                                                                           (catch java.lang.NumberFormatException _
                                                                             (keyword v)))
                                                                         ]))
                                                       (into {})))
                                        result (merge result flags)]
                                    result)))
                           (into [])))
        ]
    [(keyword device) {:flags flags
                       :options opts
                       :link link
                       :addresses addresses}]
    )
  )


(defn ipaddress-process
  "process 'ip address' output"
  [out]
  (-> out
      (string/split #"\n")
      (->> (partition-by #(re-find #"^\d+:" %))
           (partition 2)
           (map #(apply concat %))
           (map ip-entry-process)
           (into {})))
  )
