(ns spire.module.attrs
  (:require [spire.utils :as utils]
            [spire.ssh :as ssh]
            [clojure.java.io :as io])
  )

(defn make-script [{:keys [path owner group mode attrs]}]
  (utils/make-script
   "attrs.sh"
   {:FILE (some->> path utils/path-escape)
    :OWNER_ID (when (number? owner) owner)
    :OWNER_NAME (when-not (number? owner) owner)
    :GROUP_ID (when (number? group) group)
    :GROUP_NAME (when-not (number? group) group)
    :MODE_OCTAL (when (number? mode) (format "%o" mode))
    :MODE_FLAGS (when-not (number? mode) mode)
    :ATTRS attrs}))

(defn set-attrs [session opts]
  (ssh/ssh-exec session (make-script opts) "" "UTF-8" {}))



#_
(make-script "p" "o" "g" "m" "a")
