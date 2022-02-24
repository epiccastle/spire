# Babashka pod usage

Spire can be used as a pod from within babashka.

To import spire using the pod registry:

```clojure
(ns my-project.core
  (:require [babashka.pods :as pods]))

(pods/load-pod 'epiccastle/spire "0.1.0-alpha.17")
```

You can import directly from a locally installed spire. First load the pod, refering to the name of the spire binary "spire":

```clojure
(ns my-project.core
  (:require [babashka.pods :as pods]))

(pods/load-pod "spire" {:transport :socket})
```

Once loaded the spire functionality will be available under the `pod.epiccastle.spire` namespace. Here is a list of the available namespaces:

- pod.epiccastle.spire.compare
- pod.epiccastle.spire.context
- pod.epiccastle.spire.facts
- pod.epiccastle.spire.local
- pod.epiccastle.spire.module.apt
- pod.epiccastle.spire.module.apt-key
- pod.epiccastle.spire.module.apt-repo
- pod.epiccastle.spire.module.attrs
- pod.epiccastle.spire.module.authorized-keys
- pod.epiccastle.spire.module.curl
- pod.epiccastle.spire.module.download
- pod.epiccastle.spire.module.get-file
- pod.epiccastle.spire.module.group
- pod.epiccastle.spire.module.mkdir
- pod.epiccastle.spire.module.rm
- pod.epiccastle.spire.module.service
- pod.epiccastle.spire.module.shell-session
- pod.epiccastle.spire.module.stat
- pod.epiccastle.spire.module.sudo
- pod.epiccastle.spire.module.sysctl
- pod.epiccastle.spire.module.upload
- pod.epiccastle.spire.module.user
- pod.epiccastle.spire.nio
- pod.epiccastle.spire.output.core
- pod.epiccastle.spire.output.default
- pod.epiccastle.spire.output.quiet
- pod.epiccastle.spire.output.silent
- pod.epiccastle.spire.pod.stream
- pod.epiccastle.spire.scp
- pod.epiccastle.spire.selmer
- pod.epiccastle.spire.sh
- pod.epiccastle.spire.shlex
- pod.epiccastle.spire.ssh
- pod.epiccastle.spire.state
- pod.epiccastle.spire.sudo
- pod.epiccastle.spire.transport
- pod.epiccastle.spire.utils

## Differences from spire

When a script uses the higher level functionality of namespaces such as `spire.transport` and the various modules then that script should function the same by simply changing the namespaces to the `pod.epiccastle.spire` namespaces.

If your script is calling lower level functionality, such as using the ssh or scp implementations directly, then there are some differences to be aware of. Some spire functions receive or return java objects. These exist on the heap of the spire process. As such they cannot be accessed directly from babashka. When using the pod intreface these values are replaced with keywords that are used to reference those objects.

A simple example, in spire:

```shell-session
$ spire -e '(spire.ssh/make-user-info {})'
#<spire.ssh.proxy$java.lang.Object$UserInfo$6dbda2e6@2c71e143 spire.ssh.proxy$java.lang.Object$UserInfo$6dbda2e6@7fb44cecfb00>
```

Via the pod interface:

```shell-session
bb -e '(babashka.pods/load-pod "spire" {:transport :socket}) (pod.epiccastle.spire.ssh/make-user-info {})'
:pod.epiccastle.spire.ssh/user-info-8rena72i4jq86t9s
```

These keywords can also be passed to the functions that otherwise would take the java instances.

Here's an example implementing an ssh connection directly. When we call `transport/connect` we get a session keyword back. We pass this to the `ssh/ssh-exec-proc` call for its session. Notice that the `ssh/ssh-exec-proc` call returns a hashmap. In that hashmap you can see the `:channel` value is also a keyword.

```clojure
(ns my-project.core
  (:require [babashka.pods :as pods]))

(pods/load-pod "spire" {:transport :socket})

(require '[pod.epiccastle.spire.transport :as transport]
         '[pod.epiccastle.spire.ssh :as ssh])

;; execute a command via ssh using low level calls
(let [session (transport/connect {:hostname "localhost"})
      {:keys [out] :as result} (ssh/ssh-exec-proc session "pwd" {})]
  (prn 'session session)
  (prn 'result result)

  ;; read the bytes from the stdout stream and copy to standard out
  (print "the remote process returns: ")
  (clojure.java.io/copy out *out*)

  ;; close the ssh connection
  (transport/disconnect session))
```

Running this on my machine gives:

```shell-session
$ bb bb-example.clj
session :pod.epiccastle.spire.transport/session-8rena72i4jq86t9s
result {:channel :pod.epiccastle.spire.ssh/channel-exec-8rxh4t8zwyxqy9tl, :out #object[babashka.impl.proxy.proxy$java.io.PipedInputStream$ff19274a 0x8e402c8 "babashka.impl.proxy.proxy$java.io.PipedInputStream$ff19274a@8e402c8"], :in #object[babashka.impl.proxy.proxy$java.io.PipedOutputStream$ff19274a 0xc75404e "babashka.impl.proxy.proxy$java.io.PipedOutputStream$ff19274a@c75404e"], :err #object[babashka.impl.proxy.proxy$java.io.PipedInputStream$ff19274a 0x4a47929d "babashka.impl.proxy.proxy$java.io.PipedInputStream$ff19274a@4a47929d"]}
the remote process returns: /home/crispin
```

## Running the pod test suite

In order to run the pod test suite you will need to have bb installed. The tests are developed to access the local machine and may perform unwanted changes. Please only run on a clean test system.

To execute, run `make test-pod`. By default this will run all the tests that don't require sudo.

```shell-session
$ make tests-pod
...
Ran 96 tests containing 363 assertions.
0 failures, 0 errors.
{:test 96, :pass 363, :fail 0, :error 0, :type :summary}
```

The following environment variables control the test suite execution:

- `TEST_USER`: the username to connect ssh clients as. Defaults to present user.
- `TEST_HOST`: the hostname to connect to. Defaults to localhost.
- `TEST_RUN_SUDO`: if set to anything then include sudo tests.
- `TEST_SUDO_PASSWORD`: use this password for the sudo access.

For example, to run the sudo tests aswell:

```shell-session
$ TEST_RUN_SUDO=true TEST_SUDO_PASSWORD="my-sudo-password" make tests-pod
...
Ran 118 tests containing 427 assertions.
0 failures, 0 errors.
{:test 118, :pass 427, :fail 0, :error 0, :type :summary}
```

Note: it is normal for the `test-pod.test-sudo` to print some tracebacks to stderr.
