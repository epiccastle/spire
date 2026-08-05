PARTITIONS=DEFAULT
COMPONENTS="base debug"
BSDINSTALL_LOG="/tmp/install.log"

#!/bin/sh
sysrc ifconfig_DEFAULT=DHCP
sysrc sshd_enable=YES
echo "PermitRootLogin yes" >> /etc/ssh/sshd_config
echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config
echo "root-access-please" | pw usermod root -h 0
shutdown -r now
