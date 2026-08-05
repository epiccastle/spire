#!/bin/sh

mkdir -p /tmp/bsdinstall_etc
NAMESERVERS=$(grep 'option domain-name-servers' /tmp/dhclient.lease.vtnet0 \
  | sed -E 's/.*option domain-name-servers[[:space:]]+//; s/;.*//' \
  | tr ',' '\n' \
  | tr -d ' ' \
  | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' \
  | sort -u)

> /tmp/bsdinstall_etc/resolv.conf
for ns in $NAMESERVERS; do
    echo "nameserver $ns" >> /tmp/bsdinstall_etc/resolv.conf
done

mkdir -p /mnt/etc
cp /tmp/bsdinstall_etc/resolv.conf /mnt/etc/resolv.conf
