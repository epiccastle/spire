MacOS system
============

A very difficult system to bundle. You can run legacy version under qemu. Use one of the following
projects to set it up:

- https://github.com/foxlet/macOS-Simple-KVM
- https://github.com/kholia/OSX-KVM

I had more luck with macOS-Simple-KVM. I had lots of issues running thinks on my Ryzen 9 9 9950X,
but had success using a Genuine Intel digital ocean image to run the Catalina installer and build
a disk image, which I copied down and run under qemu.

Enable sshd on the machine and enable root login with the password "root-access-please".
