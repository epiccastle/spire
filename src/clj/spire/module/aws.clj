(ns spire.module.aws
  (:require [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [module command opts]
  (facts/check-bins-present #{:aws}))

(defn make-option-flag [opt-key]
  (->> opt-key
       name
       (str "--")))

#_ (make-option-flag :instance-id)

(defn make-option-value [opt-val]
  (->> opt-val
       name
       utils/string-quote))

;; TODO duplicate from shell. DRY it up
(defn make-env-string [env]
  (string/join
   " "
   (for [[k v] env] (format "%s=\"%s\"" (name k) (str v)))))

(defn make-command [module command opts]
  (let [flags (->> opts
                   (mapv (fn [[k v]]
                           [(make-option-flag k)
                            (make-option-value v)])))]
    (format "%s %s %s" (name module) (name command) (string/join " " (flatten flags)))))

(defn add-environment [command env]
  (format "export %s; %s" (make-env-string env) command))

#_ (make-command :ec2 :get-password-data {:instance-id :i-0a55f23607bb7479a})

(defn process-result-value [result-val]
  (cond
    (and (string? result-val)
         (re-matches #"\d\d\d\d\-\d\d\-\d\dT\d\d:\d\d:\d\d\.\d\d\d.+" result-val))
    (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSX") result-val)

    :else
    result-val))

#_ (process-result-value "2020-07-13T15:13:33.000Z")
#_ (process-result-value "i-0a55f23607bb7479a")

(defn process-result [data]
  (->> data
       (map (fn [[k v]]
              [k (process-result-value v)]))
       (into {})))

#_ (process-result
    {
    "InstanceId" "i-0a55f23607bb7479a",
    "PasswordData" "\r\ndata==\r\n",
    "Timestamp" "2020-07-13T15:13:33.000Z"
})

(utils/defmodule aws* [module command opts]
  [host-string session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (or (preflight module command opts)
      (exec-fn session

               ;; command
               (-> (make-command module command opts)
                   (add-environment
                    {:AWS_ACCESS_KEY_ID (get opts :aws-access-key-id
                                             (System/getenv "AWS_ACCESS_KEY_ID"))
                     :AWS_SECRET_ACCESS_KEY (get opts :aws-secret-access-key
                                                 (System/getenv "AWS_SECRET_ACCESS_KEY"))
                     :AWS_DEFAULT_REGION (get opts :region
                                              (System/getenv "AWS_DEFAULT_REGION"))}))

               ;; stdin
               ""

               ;; output format
               "UTF-8"

               ;; opts
               {}
               )
      )
  )

(defmacro aws
  ""
  [args]
  `(utils/wrap-report ~&form (aws* ~args)))

(def documentation
  {
   :module "aws"
   :blurb "Wrapper around aws cli command line tool"
   :description []
   :form "(aws module command options)"
   :args
   [{:arg "options"
     :desc "A hashmap of options. All available keys and their values are described below"}]

   :opts
   []})
