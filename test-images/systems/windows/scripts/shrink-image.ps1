# Run component cleanup to reduce WinSxS size
Dism.exe /Online /Cleanup-Image /StartComponentCleanup /ResetBase

# Clear Windows Update cache
Stop-Service wuauserv -Force
Remove-Item -Path "C:\Windows\SoftwareDistribution\*" -Recurse -Force -ErrorAction SilentlyContinue
Start-Service wuauserv

# Zero free space so qemu-img can compress effectively
$volume = (Get-Volume -DriveLetter C)
Write-Host "Free space before zero-fill: $($volume.SizeRemaining / 1GB) GB"

# sdelete is the standard tool for this; if not present, skip silently
if (Get-Command sdelete.exe -ErrorAction SilentlyContinue) {
    sdelete.exe -z C:
} else {
    Write-Host "sdelete not found, skipping free-space zero-fill. Image will compress less efficiently."
}
