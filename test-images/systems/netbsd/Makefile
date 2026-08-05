ISO_URL := https://cdn.netbsd.org/pub/NetBSD/NetBSD-10.1/images/NetBSD-10.1-amd64.iso

.PHONY: build all clean clean-build run

all: build

run:
	qemu-system-x86_64 -enable-kvm -m 1G \
	-machine type=pc,accel=kvm \
	-drive file=output-netbsd/netbsd.qcow2,format=qcow2,if=virtio,cache=writeback,discard=ignore \
	-smp 2 -cpu host \
	-netdev user,id=user.0,hostfwd=tcp::22003-:22 \
	-device virtio-net-pci,netdev=user.0 \
	-daemonize -vnc :3

kill:
	-kill $$(ps auxwww|grep qemu|grep tcp::22003-|awk '{print $$2}')

iso/NetBSD-10.1-amd64.iso:
	mkdir -p iso
	curl -fL --retry 3 --retry-delay 5 -o "iso/NetBSD-10.1-amd64.iso" "$(ISO_URL)"

output-netbsd/netbsd.qcow2: iso/NetBSD-10.1-amd64.iso
	packer init netbsd.pkr.hcl
	packer build netbsd.pkr.hcl
	#debug version
	#PACKER_LOG=1 packer build netbsd.pkr.hcl 2>&1 | tee packer-build.log

build: output-netbsd/netbsd.qcow2

clean:
	-rm -f packer-build.log

clean-build:
	-rm -rf output-netbsd/
