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
  default = "./iso/install79.iso"
}

variable "root_password" {
  type      = string
  default   = "root-access-please"
  sensitive = true
}

source "qemu" "openbsd" {
  iso_url      = var.iso_path
  iso_checksum = "none"

  output_directory = "output-openbsd"
  vm_name           = "openbsd.qcow2"
  format            = "qcow2"

  disk_size        = "10000M"
  disk_compression = true
  disk_interface   = "virtio"
  net_device       = "virtio-net-pci"

  cpus        = 1
  memory      = 1024
  accelerator = "kvm"

  headless = false

  http_directory = "http"

  boot_wait = "20s"
  boot_command = [
    "a<enter><wait2s>", #autoinstall
    "http://{{ .HTTPIP }}:{{ .HTTPPort }}/install.conf<enter>"
  ]

  communicator   = "ssh"
  ssh_username   = "root"
  ssh_password   = var.root_password
  ssh_timeout    = "30m"
}

build {
  sources = ["source.qemu.openbsd"]


}
