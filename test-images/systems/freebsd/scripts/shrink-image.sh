#!/bin/sh
# Shrink the FreeBSD disk image before Packer exports it.
# Run via the shell provisioner after SSH is up.

set -e

# Clear pkg cache and any downloaded packages
rm -rf /var/cache/pkg/* 2>/dev/null || true
rm -rf /var/db/pkg/repo-* 2>/dev/null || true

# Remove temporary / stale build artifacts
rm -rf /tmp/* /var/tmp/* 2>/dev/null || true

# Truncate log files
find /var/log -type f -exec truncate -s 0 {} \; 2>/dev/null || true

# Zero free space so qemu-img convert can compress effectively
echo "Zeroing free space for better qcow2 compression ..."
dd if=/dev/zero of=/zerofile bs=1M 2>/dev/null || true
sync
rm -f /zerofile

sync
echo "Shrink complete."
