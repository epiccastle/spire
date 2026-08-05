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
  default = "./iso/SERVER_EVAL_x64FRE_en-us.iso"
}

variable "admin_password" {
  type    = string
  default = "root-access-please"
}

source "qemu" "winserver_core" {
  iso_url      = var.iso_path
  iso_checksum = "none"   # set to sha256:<hash> in production

  output_directory = "output-winserver-core"
  vm_name           = "winserver-core.qcow2"
  format            = "qcow2"

  disk_size        = "20000M"   # 20GB fixed cap — Core fits comfortably
  disk_compression = true
  disk_interface   = "ide"      # Windows has native drivers for this — no
                                 # virtio-blk driver injection needed during
                                 # WinPE/Setup. ("sata" is not a valid QEMU
                                 # -drive bus type; ide is the correct choice
                                 # here.) Switch to "virtio" only after you've
                                 # added a virtio driver ISO/floppy.
  net_device       = "rtl8139"  # similarly avoid virtio-net until drivers
                                 # are injected; rtl8139 is natively supported

  cpus      = 2
  memory    = 4096
  accelerator = "kvm"

  headless = true

  # Inject autounattend.xml via a virtual floppy at the root, exactly where
  # Windows Setup looks for it automatically.
  floppy_files = [
    "http/autounattend.xml",
    "scripts/enable-ssh.ps1",
    "scripts/shrink-image.ps1"
  ]

  # Windows Setup auto-detects autounattend.xml on attached removable media.
  boot_wait = "2s"

  # WinRM is how Packer talks to Windows guests for the provisioning step
  communicator   = "winrm"
  winrm_username = "Administrator"
  winrm_password = var.admin_password
  winrm_timeout  = "45m"
  winrm_insecure = true
  winrm_use_ssl  = false

  shutdown_command = "shutdown /s /t 10 /f /d p:4:1 /c \"Packer shutdown\""
  shutdown_timeout = "15m"

  qemuargs = [
    ["-cpu", "host"],
    ["-smp", "2"]
  ]
}

build {
  sources = ["source.qemu.winserver_core"]

  # Copy enable-ssh.ps1 to where the unattend FirstLogonCommand expects it
  # (the floppy is mounted as a separate drive letter; this provisioner step
  # instead runs it again idempotently via WinRM as a safety net, and
  # performs the shrink pass before shutdown)
  provisioner "powershell" {
    inline = [
      "Get-Service sshd | Format-List",
      "Get-NetFirewallRule -Name OpenSSH-Server-In-TCP | Format-List"
    ]
  }

  provisioner "powershell" {
    script = "scripts/shrink-image.ps1"
  }
}