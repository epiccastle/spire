# spire

Pragmatic Provisioning

A Clojure domain specific language tailored to idempotently orchestrate machines in parallel over SSH.

## Overview

This is a *work in progress*. Consider everything liable to change at any time.

Spire is a single executable binary you can use to provision and configure remote Unix systems. It runs a Clojure _domain specific language_ that:

    * *Idempotent* Consists of a suite of idempotent operations that are easy to reason about.
    * *Just SSH* Machines don't need anything on them other than to have ssh running and have the standard Unix tools.
    * *Fast* Fast startup. Fast provisioning. For both low bandwidth and low latency connections.
    * *Simple* Separate, uncomplected parts that you can roll together in many different ways.
    * *Pragmatic* Operations performed in order. No graph building or ideological purity. Just do this. Then this. Then this.
    * *Easy Installation* Distributed as a single executable binary.
    * *Multiple Platforms* Can be used to provision any UNIX like system, from a Rasberry Pi to an Ubuntu VPS. *[WIP]*
    * *Clojure Power* Access to all the Clojure tools and data processing.
    * *Open Source* Access to the source code for ultimate peace of mind.

## Build

Install graalvm-ce-19.2.1 in your home directory. Add the native image bundle to it. Then:

    $ make clean all

Or specify a path to the graal directory to build with a different graal:

    $ make clean all GRAALVM_HOME=/path/to/my/graal

After a long wait it should write a binary executable `spire`. Test it.

    $ spire -h

## Installation

Download from http://example.com/FIXME.

## Usage

FIXME: explanation

    $ java -jar spire-0.1.0-standalone.jar [args]

## Running with lein

Use trampoline to run with leiningen so libspire.so can read the correct terminal settings:

    $ lein trampoline run -- -h

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

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
