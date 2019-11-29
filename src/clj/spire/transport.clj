(ns spire.transport
  (:require [spire.ssh :as ssh]
            [spire.output :as output]
            [spire.state :as state]
            [spire.ssh-agent :as ssh-agent]
            [spire.known-hosts :as known-hosts]
            [clojure.set :as set])
  (:import [com.jcraft.jsch JSch]))

;; all the open ssh connections
;; keys => [username hostname]
;; value => session
(defonce state
  (atom {}))

(defn connect [host-string]
  (let [[username hostname] (ssh/split-host-string host-string)
        agent (JSch.)
        session (ssh/make-session agent hostname {:username username})
        irepo (ssh-agent/make-identity-repository)
        user-info (ssh/make-user-info)
        ]
    (when (System/getenv "SSH_AUTH_SOCK")
      (.setIdentityRepository session irepo))
    (doto session
      (.setHostKeyRepository (known-hosts/make-host-key-repository))
      (.setUserInfo user-info)
      (.connect))

    (swap! state assoc [username hostname] session)
    session))

(defn disconnect [host-string]
  (let [[username hostname] (ssh/split-host-string host-string)]
    (swap! state
           (fn [s]
             (-> s
                 (get [username hostname])
                 .disconnect)
             (dissoc s [username hostname])))))

(defmacro ssh [host-string & body]
  `(try
     (connect ~host-string)
     (binding [state/*sessions* ~[host-string]]
       ~@body)
     (finally
       (disconnect ~host-string))))

(defn flush-out []
  (.flush *out*))

(defmacro ssh-group [host-strings & body]
  `(try
     (doseq [host-string ~host-strings]
       (connect host-string))
     (binding [state/*sessions* ~host-strings]
       ~@body
       #_(for [form body]
           `(do (pr '~form)
                (flush-out)
                ~form)))
     (finally
       (doseq [host-string ~host-strings]
         (disconnect host-string)))))

(defmacro ssh-parallel [host-strings & body]
  `(try
     (doseq [host-string ~host-strings]
       (connect host-string))
     (binding [state/*sessions* ~host-strings]
       (let [futs (for [host-string# ~host-strings]
                    (future
                      (on [host-string#]
                          ~@body
                          )))]
         (doall
          (map deref futs))
         ))
     (finally
       (doseq [host-string ~host-strings]
         (disconnect host-string)))))

(defmacro on [host-strings & body]
  `(let [present-sessions# (into #{} @state/*sessions*)
         sessions# (into #{} ~host-strings)
         subset# (into [] (clojure.set/intersection present-sessions# sessions#))]
     (binding [state/*sessions* subset#]
       ~@body)))

(defn psh [cmd in out & [opts]]
  (let [opts (or opts {})
        ;;_ (println *sessions*)
        channel-futs
        (->> state/*sessions*
             (map
              (fn [host-string]
                (let [[username hostname] (ssh/split-host-string host-string)
                      session (get @state [username hostname])]
                  (println username hostname)
                  [host-string
                   {:username username
                    :hostname hostname
                    :session session
                    :fut (future
                           ;;(println host-string)
                           (ssh/ssh-exec session cmd in out opts)

                           #_(let [{:keys [result] :as data} (ssh/ssh-exec session cmd in out opts)]
                               (println result)
                               (println data)
                               (print
                                (str " "
                                     (utils/colour
                                      (case result
                                        :ok :green
                                        :changed :yellow
                                        :failed :red
                                        :blue))
                                     "[" host-string "]"
                                     (utils/colour)))

                               (flush-out)
                               data))}])))
             (into {}))]
    channel-futs
    #_(let [result (->> channel-futs
                      (map (fn [{:keys [host-string fut] :as exec}]
                             [host-string @fut]))
                      (into {}))]
      (println)
      result)))

(defn pipelines [form func]
  (let [channel-futs
        (->> state/*sessions*
             (map
              (fn [host-string]
                (let [[username hostname] (ssh/split-host-string host-string)
                      session (get @state [username hostname])]
                  [host-string
                   {:username username
                    :hostname hostname
                    :session session
                    :fut (future
                           (let [{:keys [result] :as data}
                                 (func host-string username hostname session)]
                             (output/print-result form result (str "[" host-string "]"))
                             #_(print
                              (str " "
                                   (utils/colour
                                    (case result
                                      :ok :green
                                      :changed :yellow
                                      :failed :red
                                      :blue))
                                   "[" host-string "]"
                                   (utils/colour)))
                             #_(flush-out)
                             data))}])))
             (into {}))]
    (let [result (->> channel-futs
                      (map (fn [[host-string {:keys [fut]}]]
                             [host-string @fut]))
                      (into {}))]
        result)))
