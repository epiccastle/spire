(ns spire.facts
  (:require [clojure.string :as string]
            [eyre.core :as eyre]
            [spire.state :as state]))

(defonce state (atom {}))

#_(defn runner [script shell]
  (let [session (state/get-connection)
        {:keys [exec-fn exec]} (state/get-shell-context)]
    (if (= :local exec)
      (exec-fn nil (name shell) script "UTF-8" {})
      (exec-fn session script "" "UTF-8" {}))))

(defn fetch-facts
  "return all the system facts"
  []
  (eyre/gather
    (fn [script]
      (let [{:keys [exec-fn]} state/*shell-context*]
        (exec-fn state/*connection* script "" "utf-8" nil))))
  #_(fetch-shell-facts (fetch-shell)))

(defn update-facts! []
  (let [facts (fetch-facts)]
    (swap! state update (:host-string (state/get-host-config)) merge facts)))

(defn get-fact [& [path default]]
  (let [host-string (:host-string (state/get-host-config))]
    (if (@state host-string)
      (get-in @state (concat [host-string] path default))
      (get-in (update-facts!) (concat [host-string] path default))))
  )
#_
(transport/ssh "localhost"
         (get-facts))

(defn os []
  (get-fact [:system :os]))

(defn md5 []
  (or
   (get-fact [:paths :md5sum])
   (get-fact [:paths :md5])))

(defmacro on-os [ & pairs]
  (let [os (gensym)]
    `(let [~os (get-fact [:system :os])]
       (cond
         ~@(apply concat
                  (for [[pred form] (partition 2 pairs)]
                    [
                     (cond
                       (and (keyword? pred) (= pred :else))
                       `:else

                       (keyword? pred)
                       `(= ~pred ~os)

                       :else
                       `(~pred ~os))

                     form]))))))

(defmacro on-shell [ & pairs]
  (let [shell (gensym)]
    `(let [~shell (get-fact [:system :shell])]
       (cond
         ~@(apply concat
                  (for [[pred form] (partition 2 pairs)]
                    [
                     (cond
                       (and (keyword? pred) (= pred :else))
                       `:else

                       (keyword? pred)
                       `(= ~pred ~shell)

                       :else
                       `(~pred ~shell))

                     form]))))))

(defmacro on-distro [ & pairs]
  (let [shell (gensym)]
    `(let [~shell (get-fact [:system :distro])]
       (cond
         ~@(apply concat
                  (for [[pred form] (partition 2 pairs)]
                    [
                     (cond
                       (and (keyword? pred) (= pred :else))
                       `:else

                       (keyword? pred)
                       `(= ~pred ~shell)

                       :else
                       `(~pred ~shell))

                     form]))))))

(defn check-bins-present
  "Ensure all the binaries specified are present.
  Binaries are specified as keywords. They are looked up in facts under :paths
  "
  [bins]
  (let [paths (get-fact [:paths])
        not-present
        (->> bins
             (map #(when (not (paths %)) (name %)))
             (filter identity)
             )]
    (when (seq not-present)
      {:exit 1
       :out ""
       :err (format "missing commands: %s" (string/join ", " not-present))
       :result :failed})))
