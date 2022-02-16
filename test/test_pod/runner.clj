(ns test-pod.runner
  (:require [clojure.test :as test]
            [babashka.pods :as pods]))

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require

 '[test-pod.test-utils]
 '[test-pod.test-remote]
 '[test-pod.test-local]
 '[test-pod.test-compare]
 '[test-pod.test-facts]
 '[test-pod.test-nio]
 '[test-pod.test-transport]
 '[test-pod.test-shlex]
 '[test-pod.test-sh]
 '[test-pod.test-ssh]
 '[test-pod.test-scp]
 '[test-pod.test-sudo]
 '[test-pod.test-upload]
 '[test-pod.test-download]
 '[test-pod.test-shell]
 '[test-pod.test-apt]
 '[test-pod.test-apt-key]
 '[test-pod.test-apt-repo]
 '[test-pod.test-attrs]
 '[test-pod.test-authorized-keys]
 '[test-pod.test-curl]
 '[test-pod.test-group]
 '[test-pod.test-line-in-file]
 '[test-pod.test-mkdir]
 '[test-pod.test-rm]
 '[test-pod.test-service]
 '[test-pod.test-stat]
 '[test-pod.test-sysctl]
 '[test-pod.test-user]

)

(test/run-tests
 'test-pod.test-utils
 'test-pod.test-remote
 'test-pod.test-local
 'test-pod.test-compare
 'test-pod.test-facts
 'test-pod.test-nio
 'test-pod.test-transport
 'test-pod.test-shlex
 'test-pod.test-sh

 'test-pod.test-ssh
 'test-pod.test-scp

 'test-pod.test-upload
 'test-pod.test-download
 'test-pod.test-shell
 'test-pod.test-apt
 'test-pod.test-apt-key
 'test-pod.test-apt-repo
 'test-pod.test-attrs
 'test-pod.test-authorized-keys
 'test-pod.test-curl
 'test-pod.test-group
 'test-pod.test-line-in-file
 'test-pod.test-mkdir
 'test-pod.test-rm
 'test-pod.test-stat
 'test-pod.test-sysctl

 'test-pod.test-user
 'test-pod.test-service
 'test-pod.test-sudo
 )
