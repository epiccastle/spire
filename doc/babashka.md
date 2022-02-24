# Babashka

Spire can be used as a pod from within babashka.

You can import directly from a locally installed spire. First load the pod, refering to the name of the spire binary "spire":

```
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
 - pod.epiccastle.spire.module.shell
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
