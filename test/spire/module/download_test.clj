(ns spire.module.download-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [spire.module.download :refer :all]
            [spire.module.attrs :refer :all]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.state :as state]
            [spire.config :as config]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]))

(clojure.lang.RT/loadLibrary "spire")

(defn no-scp [& args]
  (assert false "second copy should be skipped"))

(defn pwd []
  (let [{:keys [exit err out]} (shell/sh "pwd")]
    (assert (zero? exit))
    (string/trim out)))

#_ (pwd)

(deftest download-test
  (testing "download test"
    (let [test-dir (str (io/file (pwd) "test/files"))]
      (test-utils/with-temp-file-names [tf]
        (test-utils/makedirs tf)
        (transport/ssh
         "localhost"
         ;; copy
         (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                (download {:src test-dir :dest tf :recurse true})))
         )))))
