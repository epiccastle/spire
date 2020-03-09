# Discussion

## Connections

Spire connects to remote machines with the `ssh` or `ssh-group` commands. Each takes one or more _connection descriptions_ that define how to connect. There are two types of connection descriptions: A compact _host string_ format, or a more powerful hashmap format.

### Connection Descriptions

#### Host Strings

A host string is of the format `username@hostname:port`. The `username` and `port` sections are optional. If no username is specified, the present user's username is used. If no port is specified then a default port of 22 is used.

#### Connection Hashmaps

A connection hashmap can be used instead of a host string. The hashmap can contain some subset of the following keys:

* :host-string
* :username
* :hostname
* :port
* :password
* :identity
* :passphrase
* :private-key
* :public-key
* :agent-forwarding
* :strict-host-key-checking
* :accept-host-key
* :key

##### host-string

This is a convenience setting for when you wish to add extra options to an existing host string config. It allows you to specify some subset of `:username`, `:hostname` and `:port` in a single setting.

##### username

The username to connect as.

##### hostname

A machine hostname or an IP number to connect to.

##### port

The TCP port to use to initiate the ssh connection.

##### password

Authenticate with the remote server using a plain text password. The password to use is specified as the value for this key. If password authentication is in use and this setting is not provided, `spire` will prompt for a password to be entered at the terminal.

##### identity

Authenticate with the remote server using a private ssh key identity stored in a file. The value should be the path to the private key file.

##### passphrase

If the identity specified is encrypted, decrypt it with this passphrase. If it is encrypted and no passphrse is given `spire` will ask for a passphrase on the terminal.

##### private-key

Authenticate with the remote server using a private ssh key identity. The identity to use is specified as the value for this key.

Note: This value is *not* a filename but the contents of the identity file itself.

##### public-key

Presently, spire does not use the public key field, but it is passed to the underlying JSch ssh implementation for it's use. You may need to supply this if you are extending spire itself with new JSch functionality.

##### agent-forwarding

Set this value to `true` to enable SSH authentication agent forwarding on the connection. This requires a local SSH agent to be running. Default value is `false`.

##### strict-host-key-checking

Setting this value as `false` will allow a connection to establish to any remote host without checking its host key for validity. Default value when unspecified is `true`, ie. Check the remote host's host key.

##### accept-host-key

This value controls the automatic acceptance of an unknown remote host key. If set to `true`, any host key will be accepted and added to the `known_hosts` file. If set to a string, `spire` will compare the remote host key's fingerprint with that specified in the string. If they match, the key will be added to the known_hosts file and the connection will be established.

##### key

Specify a custom key to key the return value in group connections. The default is to key the return values by the host-string.

### ssh

The `ssh` macro initiates a connection to a single remote host via ssh and then executes the body of the form in an implicit do block. It takes the form:

```clojure
(ssh connection-config
    ...body...)
```

`connection-config` can be a host-string or a hashmap defining the connection.

Once connected, each form in `body` will be executed in turn.

#### Return Value

The result of the evaluation of the final form in body is returned by `ssh` unaltered.

### ssh-group

The `ssh-group` macro initiates ssh connections to more than one remote host. Once the connections are established it then spawns a thread for each connection and executes the body of the form in each thread. It takes the form:

```clojure
(ssh-group [connection-conf-1 connection-conf-2 ... connection-conf-n]
    ...body...)
```

Each `connection-config` can be a host-string or a hashmap defining the connection.

If one thread/connection experiences a failure, its execution will stop, but the others will continue.

#### Return Value

`ssh-group` will take the return value from the last form evaluated by each thread and collate them together into a hashmap. The values for each connection will be stored under a key. This key will be the host-string by default, but you can override this return value key by specifying a custome `:key` in the `connection-conf` hashmap passed in to `ssh-group`.

### Nested Connections

### Agent Forwarding

## Output

## Threading Model

## Facts

## Environment

## Command Line Arguments
