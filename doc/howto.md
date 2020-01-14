# Spire How To

## Connect to multiple hosts in parallel

Pass a vector or sequence of host descriptors to `ssh-group`. Host descriptors can be a host string or a host config hashmap.

```clojure
(ssh-group ["root@host-1" "root@host-2"]
  ...commands...
  )
```

or

```clojure
(ssh-group [{:username "root" :hostname "host-1"}
            {:username "root" :hostname "host-2"]]
  ...commands...
  )
```

## Connect to a single host

Use the `ssh` macro. This can take a host string, or a host config hashmap.

```clojure
(ssh "root@localhost" ...commands...)
```

or

```clojure
(ssh {:username "root" :hostname "localhost"} ...commands...)
```

## Connect to an SSH server on a non-standard port

Append the port number to the host string:

```clojure
(ssh "root@localhost:2200" ...commands...)
```

or specify the `:port` key in a host config hashmap

```clojure
(ssh {:username "root" :hostname "localhost" :port 2200} ...commands...)
```

## Connect to an SSH server using a password stored in the program

If key authentication is not being used `spire` will try to use password authentication. If this is allowed by the server but not provided by the program, the program will prompt the user via the terminal to enter a password:

```shell
$ spire -e '(ssh "localhost" ...)'
...password text here...
```

To provide a password inside the program to be used, use the `:password` host config key

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :password "my-super-secret-password"}
  ...commands...)
```

## Connect to an SSH server with a specific key located in a local file

Pass the key's file location into the `:identity-file` of a host config:

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :identity-file "/home/user/.ssh/id_rsa"}
  ...commands...)
```

## Connect to an SSH server with a specific key contained in a var

You can provide the key _content_ to be used to authenticate with `:identity`:

```clojure
(ssh {:username "root"
      :password "localhost"
      :identity (slurp "/home/user/.ssh/id_rsa")}
  ...commands...)
```

## Connect to an SSH server with an encrypted private key

If you specify an encrypted identity and no passphrase, spire will ask for your passphrase via the terminal:

```shell
$ spire
...asking for passphrase...
```

You can manually pass in both the key and the key's `:passphrase`

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :identity (slurp "/home/user/.ssh/id_rsa")
      :passphrase "this is my super secret key passphrase"}
  ...commands...)
```

## Dangerously connect to an SSH server without checking the host key

Pass in `:strict-host-key-check` with a value of `"no"` to the host configuration:

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :strict-host-key-check "no"}
  ...commands...)
```

Note: This parameter will both allow you to connect to a new host with an unknown key, and connect to an existing host with a key that differs from the one stored locally in the host key storage.

## Automatically accept the provided host certificate and store it

Pass in `:host-key-accept` with a value of `"yes"` and your blueprint will automatically answer yes for that host if it is asked whether to accept a new host-key:

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :host-key-accept "no"}
  ...commands...)
```

Note: This will not allow you to connect to the host if the host key has *changed*. You will need to remove your old host key with `ssh -R "username@hostname"` so the new key can be accepted and stored.

## Only automatically accept a new host key if the signature matches a known signature.

Pass in the known signature as the value of `:host-key-accept`:

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :host-key-accept "aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99"}
  ...commands...)
```

## Activate authentication agent forwarding to the remote machine

Set `:auth-forward` to `true` in the host config

```clojure
(ssh {:username "root"
      :hostname "remote-host"
      :auth-forward true}
  (shell {:cmd "ssh -T git@github.com"}))
```

## Accessing the present host config definition when running in parallel

The function `(get-host-config)` will return the present executing host config hash-map:

```clojure
(ssh-group ["root@host-1" "root@host-2"]
  (let [config (get-host-config)]
    ;; config will reference host-1 or host-2, whichever we are running inside
    (hostname (:hostname config))
    ))
```

You can get a specific element of the host config by supplying a value lookup path to it:

```clojure
(ssh-group ["root@host-1" "root@host-2"]
  (hostname (get-host-config [:hostname])))
```

## Get the facts describing the target system

You can get all facts for the target system by calling `(get-fact)`

```clojure
(ssh-group ["root@host-1" "root@host-2"]
  (get-fact))
```

You can pass a key path to lookup into `get-fact`:

```clojure
(ssh-group ["root@host-1" "root@host-2"]
  (get-fact [:system]))
```

## Get the facts describing a different system to your present target.

You can look up another system's facts with a few methods:

...TODO explain ...

## Provide a custom host key for parallel execution result sets

`ssh-group` returns the result set as a hashmap. This hashmap by default is keyed with the host string.

```clojure
(ssh-group ["root@host-1" "root@host-2"]
  (get-fact [:system :os]))
```

```shell
$ spire example.clj
{"root@host-1" {...}
 "root@host-2" {...}}
```

You can provide your own key to refer to this machine by with the `:key` host config argument:

```clojure
(ssh-group [{:username "root"
             :hostname "host-1"
             :key :host-1}
            {:username "root"
             :hostname "host-2"
             :key :host-2}]
  (get-fact [:system :os]))
```

```shell
$ spire example.clj
{:host-1 {...}
 :host-2 {...}}
```

## Run some modules on only one type of operating system

Use the `on-os` macro:

```clojure
(ssh-group all-hosts
  (on-os
    :linux (apt :update)
    :freebsd (pkg :update)))
```

## Run some modules on only a specificy linux distribution

Use the `on-distro` macro:


```clojure
(ssh-group all-hosts
  (on-distro
    :ubuntu (apt :update)
    :centos (yum :update)))
```

## Create a standalone script

Use a shebang line on your file

```clojure
#!/usr/bin/env spire

;; clojure code here
```

Set the executable bit and launch it.

```shell
$ chmod a+x my-file.clj
$ ./my-file.clj
```

## Read the launch arguments

Use the `get-argv` function.

```clojure
(let [target (first (get-argv))]
  (ssh target
    (get-fact)))
```

```shell
$ spire my-file.clj root@localhost
```

## Read a local environment variable

The `System` namespace is available. Use its `getenv` function:

```clojure
(when (= "dev" (System/getenv "SPIRE_ENV"))
  ;; some speical rules for dev
  )
```

## Run a shell command on the remote machines

The `shell` module provides all you need

```clojure
(ssh "root@localhost"
  (shell {:cmd "ps aux"}))
```

## Debug a blueprint by printing a result and then continuing

Use `debug` to probe the return values of a module and print them on the output.

```clojure
(ssh "root@localhost"
  (debug (shell {:cmd "ps aux"})))
```
