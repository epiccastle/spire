FreeBSD system
==============

Run `make` to build a FreeBSD 15.1-RELEASE (amd64) QEMU disk image using
HashiCorp Packer. Make sure to install packer.

If the ISO download 404s, get a fresh one:

Open https://www.freebsd.org/where/ in a browser, pick the amd64 disc1 ISO
for the latest release, copy the download URL, and paste it into the
Makefile's `ISO_URL` variable.

Building
========

```shell
make
```

Packer will print progress. You can watch the machine build with VNC:

```
==> qemu.freebsd: view the screen of the VM, connect via VNC without a password to
==> qemu.freebsd: vnc://127.0.0.1:5972
```

```shell
vncviewer localhost:5972
```

The VNC port is different every run.

Connecting to the finished image
================================

```shell
qemu-system-x86_64 \
  -drive file=output-freebsd/freebsd.qcow2,format=qcow2,if=virtio \
  -m 4096 -cpu host -enable-kvm \
  -netdev user,id=net0,hostfwd=tcp::2222-:22 \
  -device virtio-net-pci,netdev=net0 \
  -nographic
ssh -p 2222 root@localhost   # password: freebsd
```

Files
=====

http/install.sh
----------------
Unattended install script for `bsdinstall(8)`.

Passed to the FreeBSD installer via `bsdinstall script`. It lays out a GPT
partition table (512K boot + 2 GB swap + UFS root), installs the kernel and
base distributions from the ISO, and enables SSH with root password login.

> **Note on the disk device name:** The script assumes the QEMU virtio-blk
> disk appears as `vtbd0`. If you switch to an IDE or SATA disk_interface,
> update `PARTITIONS` in install.sh to `ada0` accordingly.

scripts/shrink-image.sh
------------------------
Runs over SSH after the VM boots from the installed disk. Zeros free space so
qemu-img can compress the qcow2 output efficiently.

freebsd.pkr.hcl
----------------
The Packer template.
