# Install OpenSSH Server capability
Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0

# Start sshd and set to auto-start on boot
Start-Service sshd
Set-Service -Name sshd -StartupType 'Automatic'

# Firewall rule (often auto-created by the capability, but ensure it exists)
if (!(Get-NetFirewallRule -Name "OpenSSH-Server-In-TCP" -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -DisplayName 'OpenSSH Server (sshd)' `
        -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22
}

# Optional: make PowerShell the default shell for SSH sessions instead of cmd.exe
New-ItemProperty -Path "HKLM:\SOFTWARE\OpenSSH" -Name DefaultShell `
    -Value "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -PropertyType String -Force

# Mark first-logon setup complete so Packer's WinRM provisioner can take over
New-Item -Path C:\ -Name "ssh-ready.flag" -ItemType File -Force