(ns test-pod.runner
  (:require [clojure.test :as test]
            [babashka.pods :as pods]))

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[test-pod.test-utils]
         '[test-pod.test-remote]
         '[test-pod.test-local]
         '[test-pod.test-facts]
         '[test-pod.test-nio]
         '[test-pod.test-transport]
         '[test-pod.test-shlex]
         '[test-pod.test-sh]
         '[test-pod.test-ssh]
         '[test-pod.test-scp]

         )

(test/run-tests 'test-pod.test-utils
                'test-pod.test-remote
                'test-pod.test-local
                'test-pod.test-facts
                'test-pod.test-nio
                'test-pod.test-transport
                'test-pod.test-shlex
                'test-pod.test-sh
                'test-pod.test-ssh
                'test-pod.test-scp

                )
