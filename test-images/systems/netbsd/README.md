NetBSD system
=============

Run `make` to build a NetBSD 10.1 (amd64) QEMU disk image using HashiCorp
Packer. Make sure to install packer.

This mirrors the FreeBSD build system in `../freebsd`: Packer boots the
NetBSD install ISO under QEMU, drives the installer non-interactively,
then connects over SSH to provision and shrink the resulting image.

If the ISO download 404s, get a fresh one:

Open https://cdn.netbsd.org/pub/NetBSD/ in a browser, find the latest
release under `images/` (the `*-amd64.iso` file), copy the URL, and paste
it into the Makefile's `ISO_URL` variable.

Building
========

```shell
make
```

Packer will print progress. You can watch the machine build with VNC:

```
==> qemu.netbsd: view the screen of the VM, connect via VNC without a password to
==> qemu.netbsd: vnc://127.0.0.1:5972
```

```shell
vncviewer localhost:5972
```

The VNC port is different every run.

> **Warning:** The `boot_command` in `netbsd.pkr.hcl` drops into a shell
> from the sysinst main menu and then runs `sysinst -f install.conf` to
> automate the install.  The exact keystrokes to reach that shell are
> tuned for the NetBSD 10.1 sysinst menus; if a future release reorders
> the menu items the key letters (`e` for the Utility menu, `a` for
> "Run /bin/sh") will need updating.  Watch a build over VNC if the
> automatic install stalls.

Connecting to the finished image
================================

```shell
qemu-system-x86_64 \
  -drive file=output-netbsd/netbsd.qcow2,format=qcow2,if=ide \
  -m 4096 -cpu host -enable-kvm \
  -netdev user,id=net0,hostfwd=tcp::2222-:22 \
  -device virtio-net-pci,netdev=net0 \
  -nographic
ssh -p 2222 root@localhost   # password: root-access-please
```

Files
=====

http/install.conf
-----------------
`sysinst` definition file (answer file) passed via `sysinst -f`.  It
selects the `wd0` disk, a full install, enables DHCP on `vioif0`, and
turns on sshd with root login.

http/postinstall.sh
--------------------
Fetched and run from the installer ramdisk after the sets are extracted.
Writes `/etc/rc.conf`, `sshd_config`, sets the root password, and writes
the `ssh-ready` flag.

scripts/shrink-image.sh
------------------------
Runs over SSH after the installed VM boots. Zeros free space so qemu-img
can compress the qcow2 output efficiently.

netbsd.pkr.hcl
--------------
The Packer template.
