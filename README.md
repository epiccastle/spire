# spire

Pragmatic Provisioning Using Clojure

A Clojure domain specific language tailored to idempotently orchestrate machines in parallel over SSH.

[![CircleCI](https://circleci.com/gh/epiccastle/spire/tree/master.svg?style=shield)](https://circleci.com/gh/epiccastle/spire/tree/master)
[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://app.slack.com/client/T03RZGPFR/CUHG31VR6)


[Official Website](https://epiccastle.io/spire/)

## Overview

This is a **work in progress**. Consider everything liable to change at any time.

Spire is a single executable binary you can use to provision and configure remote Unix systems. It runs a Clojure _domain specific language_ that is:

* **Idempotent** - Consists of a suite of idempotent operations that are easy to reason about.
* **Just SSH** - Machines don't need anything on them other than to have ssh running and have the standard Unix tools.
* **Fast** - Fast startup. Fast provisioning. For both low bandwidth and high latency connections.
* **Simple** - Separate, uncomplected parts that you can roll together in many different ways.
* **Pragmatic** - Operations performed in order. No graph building or ideological purity. Just do this. Then this. Then this.
* **Easy Installation** - Distributed as a single executable binary.
* **Multiple Platforms** - Can be used to provision any UNIX like system, from FreeBSD on a Rasberry Pi to Ubuntu on EC2 to a MacOS laptop. **[WIP]**
* **Clojure Power** - Access to all the Clojure tools and data processing.
* **Open Source** - Access to the source code for ultimate peace of mind.

## Quickstart

```shell-session
$ curl -O https://raw.githubusercontent.com/epiccastle/spire/master/scripts/install
$ bash install
$ spire -e '(ssh "localhost" (get-fact))'
```

## Installation

For Linux and MacOS:

```shell-session
$ curl -O https://raw.githubusercontent.com/epiccastle/spire/master/scripts/install
$ bash install
```

## Usage

```shell-session
$ spire FILE
```

## Tutorial

[Build your own VPS based VPN proxy service with spire and wireguard.](doc/tutorial.md)

## How To

[Find examples to solve common problems.](doc/howto.md)

## Module Reference

[Built in modules.](https://epiccastle.io/spire/modules.html)

## Discussion

[Higher level discussion of the use of spire](doc/discussion.md)

## Source Code

You can find the source code to Spire on github [here.](https://github.com/epiccastle/spire)

## Build

Install graalvm-ce-java11-20.1.0-dev in your home directory. Add the native image bundle to it. Then:

```shell-session
$ make clean all
```

Or specify a path to the graal directory to build with a different graal:

```shell-session
$ make clean all GRAALVM=/path/to/my/graal
```

After a long wait it should write a binary executable `spire`. Test it.

```shell-session
$ ./spire --help
```

Install it.

```shell-session
$ mv spire ~/bin/
$ spire --help
```

**Note:**  graalvm-ce-java8-20.1.0-dev should also work. Earlier versions of graalvm, up to and including 20.0.0 *do not work*.

## Running as a jar

```shell-session
$ make jar
$ java -jar spire-0.1.0-standalone.jar FILE
```

## Running with lein

Use trampoline to run with leiningen so libspire.so can read the correct terminal settings:

```shell-session
$ lein trampoline run -- --help
```

## Running tests

The test suite consists of some unit tests and some module tests that issue a full ssh connection to `localhost`. These tests will test both the ssh functionality and module functionality just on the running OS. By running this test suite on different architectures, compatability of the modules can be ascertained. The lack of external connections in the test makes setting up test environments easier.

```shell-session
$ lein test
```

## Platforms

### Native Client

This is the binary spire program you run on your local machine.

* [x] Linux 64 bit
* [x] MacOS 64 bit
* [ ] Windows 64 bit

### Target Systems

This is the operating systems you will be provisioning, that the spire modules and operations will work against.

* [x] Ubuntu Linux
* [x] FreeBSD
* [x] MacOS

### Bugs

There will be many bugs at this early stage. Especially on different target machines. Please open tickets for any issues you find.

## License

Copyright © 2019-2020 Crispin Wellington

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
