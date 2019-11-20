(ns spire.transfer
  (:require [spire.shell :as shell]
            [spire.probe :as probe]
            [spire.utils :as utils]
            [digest :as digest]
            [edamame.core :as edamame]
            [clojure.java.io :as io]
            ))

(defn ssh-line [host-string line]
  (let [proc (shell/proc ["ssh" host-string])
        snapshot? true ;;(string/ends-with? version "-SNAPSHOT")
        commands  (probe/commands proc)
        local-spire (utils/which-spire)
        local-spire-digest (digest/md5 (io/as-file local-spire))
        spire-dest (str "/tmp/spire-" local-spire-digest)
        ]
    ;; sync binary
    (utils/push commands proc host-string local-spire spire-dest)
    (shell/feed-from-string proc (format "%s --server\n" spire-dest))
    (shell/feed-from-string proc (str line "\n"))
    (let [[_ out] (shell/capture-until (:out-reader proc) "---end-stdout---\n")
          [_ err] (shell/capture-until (:err-reader proc) "---end-stderr---\n")]
      {:out (edamame/parse-string out)
       :err (when (pos? (count err))
              (edamame/parse-string (subs err 6)))})))

(defmacro ssh [host-string & body]
  `(ssh-line ~host-string '(vector ~@body)))
