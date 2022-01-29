(ns test-pod.test-download
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils :refer [bash]]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.download :as download]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(defn setup []
  (bash "sudo rm /tmp/test.txt")
  (bash "echo foo > /tmp/test.txt")
  (bash "rm -rf /tmp/scp-dest")
  (bash "mkdir /tmp/scp-dest ")
  )

(deftest download-file
  (binding [state/output-module :silent]

    (setup)
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (download/download {:src "/tmp/test.txt"
                         :dest "/tmp/scp-dest/"})
     (is (.exists (io/file "/tmp/scp-dest/test.txt")))
     (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt"))))

    (setup)
    (transport/local
     (download/download {:src "/tmp/test.txt"
                     :dest "/tmp/scp-dest/"})
     (is (.exists (io/file "/tmp/scp-dest/test.txt")))
     (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt"))))))


(defn setup2 []
  (bash "rm -rf /tmp/scp-src /tmp/scp-dest")
  (bash "mkdir -p /tmp/scp-dest /tmp/scp-src /tmp/scp-src/a /tmp/scp-src/a/b /tmp/scp-src/a/c")
  (bash "echo a>/tmp/scp-src/a/a.txt")
  (bash "echo c>/tmp/scp-src/a/c/c.txt")
  )

(deftest download-directory
  (binding [state/output-module :silent]

    (setup2)
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (download/download {:src "/tmp/scp-src"
                         :dest "/tmp/scp-dest/"
                         :recurse true})
     (is (= "a\n" (slurp "/tmp/scp-dest/scp-src/a/a.txt")))
     (is (= "c\n" (slurp "/tmp/scp-dest/scp-src/a/c/c.txt")))
     )

    (setup2)
    (transport/local
     (download/download {:src "/tmp/scp-src"
                     :dest "/tmp/scp-dest/"
                     :recurse true})
     (is (= "a\n" (slurp "/tmp/scp-dest/scp-src/a/a.txt")))
     (is (= "c\n" (slurp "/tmp/scp-dest/scp-src/a/c/c.txt")))
     )
    )
  )
