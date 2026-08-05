ISO_URL := https://cdn.openbsd.org/pub/OpenBSD/7.9/amd64/install79.iso
ISO_FILE := iso/install79.iso

.PHONY: build all clean clean-build run

all: build

run:
	qemu-system-x86_64 -enable-kvm -m 1G \
	-machine type=pc,accel=kvm \
	-drive file=output-openbsd/openbsd.qcow2,format=qcow2,if=virtio,cache=writeback,discard=ignore \
	-smp 2 -cpu host \
	-netdev user,id=user.0,hostfwd=tcp::22002-:22 \
	-device virtio-net-pci,netdev=user.0 \
	-daemonize -vnc :2

kill:
	-kill $$(ps auxwww|grep qemu|grep tcp::22002-|awk '{print $$2}')

$(ISO_FILE):
	mkdir -p iso
	curl -fL --retry 3 --retry-delay 5 -o "$(ISO_FILE)" "$(ISO_URL)"

build: $(ISO_FILE)
	packer init openbsd.pkr.hcl
	packer build openbsd.pkr.hcl
	#debug version
	#PACKER_LOG=1 packer build openbsd.pkr.hcl 2>&1 | tee packer-build.log

clean:
	-rm -f packer-build.log

clean-build:
	-rm -rf output-openbsd/
