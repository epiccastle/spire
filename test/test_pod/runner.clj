(ns test-pod.runner
  (:require [clojure.test :as test]
            [babashka.pods :as pods]))

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[test-pod.test-utils]
         '[test-pod.test-remote]
         '[test-pod.test-local]
         )

(test/run-tests 'test-pod.test-utils
                'test-pod.test-remote
                'test-pod.test-local
                )
