mount /dev/vtbd0s1a /mnt
sed -i'' -E 's/^[[:blank:]]*#?[[:blank:]]*PermitRootLogin.*/PermitRootLogin yes/' /mnt/etc/ssh/sshd_config
sed -i'' -E 's/^[[:blank:]]*#?[[:blank:]]*PasswordAuthentication.*/PasswordAuthentication yes/' /mnt/etc/ssh/sshd_config
echo "PermitRootLogin yes" >> /mnt/etc/ssh/sshd_config
echo "PasswordAuthentication yes" >> /mnt/etc/ssh/sshd_config
sync
umount /mnt
