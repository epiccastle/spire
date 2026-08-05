packer {
  required_plugins {
    qemu = {
      version = ">= 1.1.0"
      source  = "github.com/hashicorp/qemu"
    }
  }
}

variable "iso_path" {
  type    = string
  default = "./iso/FreeBSD-15.1-RELEASE-amd64-disc1.iso"
}

variable "root_password" {
  type      = string
  default   = "root-access-please"
  sensitive = true
}

source "qemu" "freebsd" {
  iso_url      = var.iso_path
  iso_checksum = "none"   # set to sha256:<hash> in production

  output_directory = "output-freebsd"
  vm_name           = "freebsd.qcow2"
  format            = "qcow2"

  disk_size        = "20000M"   # 20 GB
  disk_compression = true
  disk_interface   = "virtio"     # FreeBSD GENERIC kernel has virtio-blk
  net_device       = "virtio-net-pci"  # correct QEMU device model name

  cpus        = 2
  memory      = 4096
  accelerator = "kvm"

  headless = true

  # Packer's built-in HTTP server serves the contents of http/ to the VM
  # during the boot_command phase.
  http_directory = "http"

  boot_wait = "5s"
  boot_command = [
    "<esc><wait>",
    "boot -s<enter>",
    "<wait15s>",
    "/bin/sh<enter><wait>",
    "mdmfs -s 100m md /tmp<enter><wait>",
    "dhclient -l /tmp/dhclient.lease.vtnet0 vtnet0<enter><wait5>",
    "fetch -o /tmp/install.sh http://{{ .HTTPIP }}:{{ .HTTPPort }}/install.sh<enter><wait5>",
    "export BSDINSTALL_PKG_REPOS_DIR=/usr/freebsd-packages/repos/<enter>",
    "bsdinstall script /tmp/install.sh<enter>"
    ]

  # After bsdinstall finishes and the VM reboots from the new disk, Packer
  # connects via SSH for the provisioning step.
  communicator   = "ssh"
  ssh_username   = "root"
  ssh_password   = var.root_password
  ssh_timeout    = "30m"

  shutdown_command = "shutdown -p now"
  shutdown_timeout = "10m"

  qemuargs = [
    ["-cpu", "host"],
    ["-smp", "2"]
  ]
}

build {
  sources = ["source.qemu.freebsd"]

  # Quick sanity check that SSH and the installed OS are alive
  provisioner "shell" {
    inline = [
      "test -f /root/ssh-ready || echo 'WARNING: ssh-ready flag missing'",
      "freebsd-version",
      "service sshd status"
    ]
  }

  # Shrink the disk image for smaller qcow2 output
  provisioner "shell" {
    script = "scripts/shrink-image.sh"
  }
}
