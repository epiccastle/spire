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
  default = "./iso/NetBSD-10.1-amd64.iso"
}

variable "root_password" {
  type      = string
  default   = "root-access-please"
  sensitive = true
}

source "qemu" "netbsd" {
  iso_url      = var.iso_path
  iso_checksum = "none"   # set to sha256:<hash> in production

  output_directory = "output-netbsd"
  vm_name           = "netbsd.qcow2"
  format            = "qcow2"

  disk_size        = "20000M"   # 20 GB
  disk_compression = true
  disk_interface   = "ide"        # NetBSD sysinst sees this as "wd0"
  net_device       = "virtio-net-pci"

  cpus        = 2
  memory      = 4096
  accelerator = "kvm"

  headless = true

  # Packer's built-in HTTP server serves the contents of http/ to the VM
  # during the boot_command phase.
  http_directory = "http"

  boot_wait = "10s"
  boot_command = [
    "<wait20s>",
    "a<enter><wait1s>", # english
    "b<enter><wait1s>", # US-English keyboard

    #"x<enter>", # exit installer
    "a<enter><wait1s>", #install to HDD
    "b<enter><wait1s>", #are you sure, yes!
    "a<enter><wait1s>", #first disk available
    "a<enter><wait1s>", #GPT partition table
    "a<enter><wait1s>", #correct geometry
    "b<enter><wait1s>", #default partition sizes
    "x<enter><wait1s>", #partition sizes ok
    "b<enter><wait2s>", #continue... partition
    "a<enter><wait1s>", #use bios console booter (b = use serial com0)
    "c<enter><wait1s>", #minimal installation
    "a<enter><wait15s>", #from cdrom source
    "<enter><wait1s>", # all done, continue
    "root-access-please<enter><wait5s>", # root password
    "root-access-please<enter><wait5s>",
    "g<enter><wait1s>", #enable sshd
    "a<enter><wait1s>", #configure network
    "a<enter><wait1s>", #vioif0
    "<enter><wait1s>", #autoconf network media type
    "a<enter><wait10s>", #yes, autoconf
    "netbsd<enter><wait1s>", #hostname
    "localdomain<enter><wait1s>", #domain
    "a<enter><wait1s>", #confirm ok
    "a<enter><wait1s>", #install in /etc

    # here we could o) add a user

    "x<enter>", #finish config
    "<enter><wait1s>", #continue
    "d<enter>", #reboot

    "<wait25s>", #wait for reboot

    "root<enter>",
    "root-access-please<enter>",

    "sed -i'' -E 's/^[[:blank:]]*#?[[:blank:]]*PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config<enter>",
    "echo 'SetEnv ENV=/etc/shrc PATH=/sbin:/usr/sbin:/bin:/usr/bin:/usr/pkg/bin:/usr/local/bin' >> /etc/ssh/sshd_config<enter>",
    "service sshd restart<enter>",
    "exit<enter>"
  ]

  # After sysinst finishes and the VM reboots from the new disk, Packer
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
  sources = ["source.qemu.netbsd"]

  # Quick sanity check that SSH and the installed OS are alive
  provisioner "shell" {
    inline = [
      "uname -a",
      "service sshd status || /etc/rc.d/sshd status",
      "cat /etc/os-release || true"
    ]
  }

  # Shrink the disk image for smaller qcow2 output
  provisioner "shell" {
    script = "scripts/shrink-image.sh"
  }
}
