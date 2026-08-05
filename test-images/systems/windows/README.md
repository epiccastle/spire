Windows system
==============

Run make to build a Windows Server 2022 (Standard/Datacenter eval, en-US, x64) qemu disk image
using hashicorp Packer. Make sure to install packer.

If the iso download 404s, get a fresh one:

Open https://www.microsoft.com/en-us/evalcenter/evaluate-windows-server-2022 in a browser, start
the download, copy the resulting .iso URL from the download manager / Network tab, and paste it into
the Makefile.

Building
========

```shell
make
```

packer will print out details. You can watch the machine build with vnc:

```shell
...
==> qemu.winserver_core: view the screen of the VM, connect via VNC without a password to
==> qemu.winserver_core: vnc://127.0.0.1:5972
...
```

```shell
vncviewer localhost:5972
```

the vnc port is different every run.

Files
=====

http/autounattend.xml
---------------------
Unattended install + first-boot SSH setup

This drives Windows Setup with zero prompts: accepts EULA, partitions disk, selects the Server Core
image index (no Desktop Experience = smaller), sets a local Administrator password, and on first
logon runs a PowerShell script that installs and enables OpenSSH Server.

> ** Note ** Note on the Server Core image index: the /IMAGE/NAME value must exactly
> match an index name inside the ISO's install.wim. Verify with wiminfo or 7-Zip before
> building - eval ISOs typically expose both SERVERSTANDARD (Desktop Experience) and
> SERVERSTANDARDCORE (Core). Picking the Core one is what keeps the image small.

scripts/enable-ssh.ps1
----------------------
installs and starts sshd, opens firewall

scripts/shrink-image.ps1
------------------------
minimize disk footprint before export

windows-server-core.pkr.hcl
---------------------------
the Packer template

curl -v --ntlm -u 'Administrator:PackerTest123!' \
  -H "Content-Type: application/soap+xml;charset=UTF-8" \
  -X POST http://127.0.0.1:4118/wsman \
  -d '<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"/>'
