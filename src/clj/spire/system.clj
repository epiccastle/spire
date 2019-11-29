(ns spire.system
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            )
  )

(def apt-command "DEBIAN_FRONTEND=noninteractive apt-get")

(defn process-values [result func]
  (->> result
       (map (fn [[k v]] [k (func v)]))
       (into {})))

(defn process-apt-update-line [line]
  (let [[method remain] (string/split line #":" 2)
        method (-> method string/lower-case keyword)
        [url dist component size] (-> remain (string/split #"\s+" 5) rest (->> (into [])))
        size (some->> size (re-find #"\[(.+)\]") second)
        result {:method method
                :url url
                :dist dist
                :component component}]
    (if size
      (assoc result :size size)
      result)))

(defn apt-get [& args]
  #_(transport/psh (string/join " " (concat [apt-command] args)) "" "")

  (transport/pipelines
   (fn [host-string username hostname session]
     (let [{:keys [exit out] :as result}
           (ssh/ssh-exec session (string/join " " (concat [apt-command] args)) "" "UTF-8" {})]
       (if (zero? exit)
         (let [data (-> out
                        (string/split #"\n")
                        (->>
                         (filter #(re-find #"^\w+:\d+\s+" %))
                         (mapv process-apt-update-line))
                        )
               changed? (some #(= % :get) (map :method data))
               ]
           (assoc result
                  :result (if changed? :changed :ok)
                  :out-lines (string/split out #"\n")
                  :data data
                  ))
         (assoc result :result :failed))))))

(defmulti apt* (fn [state & args] state))

(defmethod apt* :update [_]
  (transport/pipelines
   (fn [_ _ _ session]
     (let [{:keys [exit out] :as result}
           (ssh/ssh-exec session "apt-get update" "" "UTF-8" {})]
       (if (zero? exit)
         (let [data (-> out
                        (string/split #"\n")
                        (->>
                         (filter #(re-find #"^\w+:\d+\s+" %))
                         (mapv process-apt-update-line))
                        )
               changed? (some #(= % :get) (map :method data))
               ]
           (assoc result
                  :result (if changed? :changed :ok)
                  :out-lines (string/split out #"\n")
                  :data data
                  ))
         (assoc result :result :failed))))))

(defmethod apt* :upgrade [_]
  (transport/pipelines
   (fn [_ _ _ session]
     (let [{:keys [exit out] :as result}
           (ssh/ssh-exec session "apt-get upgrade" "" "UTF-8" {})]
       (if (zero? exit)
         (assoc result
                :result :ok
                :out-lines (string/split out #"\n")
                )
         (assoc result :result :failed))))
   ))

(defmethod apt* :dist-upgrade [_]
  (apt-get "dist-upgrade" "-y"))

(defmethod apt* :autoremove [_]
  (apt-get "autoremove" "-y"))

(defmethod apt* :clean [_]
  (apt-get "clean" "-y"))

(defmethod apt* :install [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "install" "-y" package-or-packages)
    (apt-get "install" "-y" (string/join " " package-or-packages))))

(defmethod apt* :remove [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "remove" "-y" package-or-packages)
    (apt-get "remove" "-y" (string/join " " package-or-packages))))

(defmethod apt* :purge [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "purge" "-y" package-or-packages)
    (apt-get "purge" "-y" (string/join " " package-or-packages))))

(defmethod apt* :download [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "download" "-y" package-or-packages)
    (apt-get "download" "-y" (string/join " " package-or-packages))))

(defn apt [& args]
  (pr (concat '(apt) args))
  (.flush *out*)
  (apply apt* args)
    )


#_ (apt* :download ["iputils-ping" "traceroute"])
#_ (apt* :autoremove)

(defn hostname [hostname]
  (spit "/etc/hostname" hostname)
  (shell/sh "hostname" hostname))
