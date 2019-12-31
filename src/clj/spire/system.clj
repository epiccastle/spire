(ns spire.system
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.output :as output]
            [spire.state :as state]
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

#_ (defn apt-get [& args]
  (transport/pipelines
   (fn [_ session]
     (let [{:keys [exit out err] :as result}
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
                  :err-lines (string/split err #"\n")
                  :data data
                  ))
         (assoc result
                :result :failed
                :out-lines (string/split out #"\n")
                :err-lines (string/split err #"\n")
                ))))))

(defmulti apt* (fn [state & args] state))

#_(defmethod apt* :update [_]
  (transport/pipelines
   (fn [_ session]
     (let [{:keys [exit out err] :as result}
           (ssh/ssh-exec session "apt-get update" "" "UTF-8" {})]
       (if (zero? exit)
         (let [data (-> out
                        (string/split #"\n")
                        (->>
                         (filter #(re-find #"^\w+:\d+\s+" %))
                         (mapv process-apt-update-line))
                        )
               changed? (some #(= % :get) (map :method data))]
           (assoc result
                  :result (if changed? :changed :ok)
                  :out-lines (string/split out #"\n")
                  :err-lines (string/split err #"\n")
                  :update data))
         (assoc result
                :result :failed
                :out-lines (string/split out #"\n")
                :err-lines (string/split err #"\n")
                ))))))

#_ (defmethod apt* :upgrade [_]
  (transport/pipelines
   (fn [_ session]
     (let [{:keys [exit out] :as result}
           (ssh/ssh-exec session "apt-get upgrade" "" "UTF-8" {})]
       (if (zero? exit)
         (assoc result
                :result :ok
                :out-lines (string/split out #"\n")
                )
         (assoc result :result :failed))))))

#_ (defmethod apt* :dist-upgrade [_]
  (apt-get "dist-upgrade" "-y"))

#_ (defmethod apt* :autoremove [_]
  (apt-get "autoremove" "-y"))

#_ (defmethod apt* :clean [_]
  (apt-get "clean" "-y"))

#_ (defmethod apt* :install [_ package-or-packages]
  (let [package-string (if (string? package-or-packages)
                         package-or-packages
                         (string/join " " package-or-packages))]
    (transport/pipelines
     (fn [_ session]
       (let [{:keys [exit out] :as result}
             (ssh/ssh-exec session
                           (str "DEBIAN_FRONTEND=noninteractive apt-get install -y " package-string)
                           "" "UTF-8" {})]
         (if (zero? exit)
           (let [[_ upgraded installed removed]
                 (re-find #"(\d+)\s*\w*\s*upgrade\w*, (\d+)\s*\w*\s*newly instal\w*, (\d+) to remove" out)

                 upgraded (Integer/parseInt upgraded)
                 installed (Integer/parseInt installed)
                 removed (Integer/parseInt removed)
                 ]
             (assoc result
                    :result (if (and (zero? upgraded)
                                     (zero? installed)
                                     (zero? removed))
                              :ok
                              :changed)
                    :out-lines (string/split out #"\n")
                    :packages {:upgraded upgraded
                               :installed installed
                               :removed removed}))
           (assoc result :result :failed)))))))

#_ (defmethod apt* :remove [_ package-or-packages]
  (let [package-string (if (string? package-or-packages)
                         package-or-packages
                         (string/join " " package-or-packages))]
    (transport/pipelines
     (fn [_ session]
       (let [{:keys [exit out] :as result}
             (ssh/ssh-exec session
                           (str "DEBIAN_FRONTEND=noninteractive apt-get remove -y " package-string)
                           "" "UTF-8" {})]
         (if (zero? exit)
           (let [[_ upgraded installed removed]
                 (re-find #"(\d+)\s*\w*\s*upgrade\w*, (\d+)\s*\w*\s*newly instal\w*, (\d+) to remove" out)

                 upgraded (Integer/parseInt upgraded)
                 installed (Integer/parseInt installed)
                 removed (Integer/parseInt removed)]
             (assoc result
                    :result (if (and (zero? upgraded)
                                     (zero? installed)
                                     (zero? removed))
                              :ok
                              :changed)
                    :out-lines (string/split out #"\n")
                    :packages {:upgraded upgraded
                               :installed installed
                               :removed removed}))
           (assoc result :result :failed)))))))

#_ (defmethod apt* :purge [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "purge" "-y" package-or-packages)
    (apt-get "purge" "-y" (string/join " " package-or-packages))))

#_ (defmethod apt* :download [_ package-or-packages]
  (if (string? package-or-packages)
    (apt-get "download" "-y" package-or-packages)
    (apt-get "download" "-y" (string/join " " package-or-packages))))

#_ (defn apt [& args]
  (binding [state/*form* (concat '(apt) args)]
    (output/print-form state/*form*)
    (apply apt* args)))

#_ (apt* :download ["iputils-ping" "traceroute"])
#_ (apt* :autoremove)

#_ (defn hostname [hostname]
  (spit "/etc/hostname" hostname)
  (shell/sh "hostname" hostname))
