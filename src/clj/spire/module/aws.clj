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

(defn make-command [module command opts]
  (let [flags (->> opts
                   (mapv (fn [[k v]]
                           [(make-option-flag k)
                            (make-option-value v)
                         ])))]
    (format "%s %s %s" (name module) (name command)
            (string/join " " (flatten flags))
            )
    )
  )

#_ (make-command :ec2 :get-password-data {:instance-id :i-0a55f23607bb7479a})

(utils/defmodule aws* [module command opts]
  [host-string session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (or (preflight module command opts)
      (exec-fn session

               ;; command
               (make-command module command opts)

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
