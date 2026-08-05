ISO_URL := https://download.freebsd.org/releases/ISO-IMAGES/15.1/FreeBSD-15.1-RELEASE-amd64-disc1.iso

.PHONY: build all clean clean-build run

all: build

run:
	qemu-system-x86_64 -enable-kvm -m 1G \
	-machine type=pc,accel=kvm \
	-drive file=output-freebsd/freebsd.qcow2,format=qcow2,if=virtio,cache=writeback,discard=ignore \
	-smp 2 -cpu host \
	-netdev user,id=user.0,hostfwd=tcp::22004-:22 \
	-device virtio-net-pci,netdev=user.0 \
	-daemonize -vnc :4

kill:
	-kill $$(ps auxwww|grep qemu|grep tcp::22004-|awk '{print $$2}')

iso/FreeBSD-15.1-RELEASE-amd64-disc1.iso:
	mkdir -p iso
	curl -fL --retry 3 --retry-delay 5 -o "iso/FreeBSD-15.1-RELEASE-amd64-disc1.iso" "$(ISO_URL)"

output-freebsd/freebsd.qcow2: iso/FreeBSD-15.1-RELEASE-amd64-disc1.iso
	packer init freebsd.pkr.hcl
	packer build freebsd.pkr.hcl
	#debug version
	#PACKER_LOG=1 packer build freebsd.pkr.hcl 2>&1 | tee packer-build.log

build: output-freebsd/freebsd.qcow2

clean:
	-rm -f packer-build.log

clean-build:
	-rm -rf output-freebsd/
