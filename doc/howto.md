# Spire How To

## Connect to multiple hosts in parallel

Pass a vector or sequence of host descriptors to `ssh-group`. Host descriptors can be a host string or a host config hashmap.

```clojure
(ssh-group ["root@host-1" "root@host-2"]
  ;; commands here
  )
```

or

```clojure
(ssh-group [{:username "root" :hostname "host-1"}
            {:username "root" :hostname "host-2"]]
  ;; commands here
  )
```

## Connect to a single host

Use the `ssh` macro. This can take a host string, or a host config hashmap.

```clojure
(ssh "root@localhost"
    ;; commands here
    )
```

or

```clojure
(ssh {:username "root" :hostname "localhost"}
    ;; commands here
    )
```

or

```clojure
(ssh {:host-string "root@localhost"}
    ;; commands here
    )
```


## Connect to an SSH server on a non-standard port

Append the port number to the host string:

```clojure
(ssh "root@localhost:2200"
    ;; commands here
    )
```

or specify the `:port` key in a host config hashmap

```clojure
(ssh {:username "root" :hostname "localhost" :port 2200}
    ;; commands here
    )
```

## Connect to an SSH server using a password stored in the program

If key authentication is not being used `spire` will try to use password authentication. If this is allowed by the server but not provided by the program, the program will prompt the user via the terminal to enter a password:

```shell
$ spire -e '(ssh "localhost" ...)'
Enter Password for user@locahost: ...type in password here...
```

To provide a password inside the program to be used, use the `:password` host config key

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :password "my-super-secret-password"}
  ;; commands here
  )
```

## Connect to an SSH server with a specific key located in a local file

Pass the key's file location into the `:identity` of a host config:

```clojure
(ssh {:host-string "root@localhost"
      :identity "/home/user/.ssh/id_rsa"}
  ;; commands here
  )
```

```shell
$ spire my-script.clj
Enter Passphrase for /home/user/.ssh/id_rsa: ...type in key passphrase here...
```

## Connect to an SSH server with a specific key contained in a var

You can provide the key _content_ to be used to authenticate with `:private-key`:

```clojure
(ssh {:host-string "root@localhost"
      :private-key (slurp "/home/user/.ssh/id_rsa")}
  ;; commands here
  )
```

```shell
$ spire my-script.clj
Enter Passphrase for inline key for root@localhost: ...type in key passphrase here...
```

## Connect to an SSH server with an encrypted private key in a file

If you specify an encrypted identity and no passphrase, spire will ask for your passphrase via the terminal. You can manually pass in both the key and the key's `:passphrase`

```clojure
(ssh {:host-string "root@localhost"
      :identity "/home/user/.ssh/id_rsa"
      :passphrase "this is my super secret key passphrase"}
  ;; commands here
  )
```

## Connect to an SSH server with an encrypted private key from a var

If you specify an encrypted identity and no passphrase, spire will ask for your passphrase via the terminal. You can manually pass in both the key and the key's `:passphrase`

```clojure
(ssh {:host-string "root@localhost"
      :private-key (slurp "/home/user/.ssh/id_rsa")
      :passphrase "this is my super secret key passphrase"}
  ;; commands here
  )
```

## Dangerously connect to an SSH server without checking the host key

Pass in `:strict-host-key-check` with a value of `false` to the host configuration:

```clojure
(ssh {:host-string "root@localhost"
      :strict-host-key-checking false}
  ;; commands here
  )
```

Note: This parameter will both allow you to connect to a new host with an unknown key, and connect to an existing host with a key that differs from the one stored locally in the host key storage.

Note: Connecting to a machine with this value set to false, stores the key in the known_hosts file.

## Automatically accept the provided host certificate and store it

Pass in `:accept-host-key` with a value of `"yes"` and your blueprint will automatically answer yes for that host if it is asked whether to accept a new host-key:

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :accept-host-key true}
  ;; commands here
  )
```

Note: This will not allow you to connect to the host if the host key has *changed*. You will need to remove your old host key with `ssh -R "username@hostname"` so the new key can be accepted and stored.

## Only automatically accept a new host key if the signature matches a known signature.

Pass in the known signature as the value of `:host-key-accept`:

```clojure
(ssh {:username "root"
      :hostname "localhost"
      :accept-host-key "e4:d6:ce:f8:c4:4f:b4:60:1a:47:fc:f0:27:c8:da:c7"}
  ;; commands here
  )
```

## Activate authentication agent forwarding to the remote machine

Set `:auth-forward` to `true` in the host config

```clojure
(ssh {:username "root"
      :hostname "remote-host"
      :agent-forwarding true}
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

Use the `*command-line-args*` var.

```clojure
(let [target (first *command-line-args*)]
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
  ;; some special rules for dev
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
