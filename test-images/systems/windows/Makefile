ISO_URL := https://software-static.download.prss.microsoft.com/sg/download/888969d5-f34g-4e03-ac9d-1f9786c66749/SERVER_EVAL_x64FRE_en-us.iso

.PHONY: build all clean clean-build run

all: build

run:
	qemu-system-x86_64 -enable-kvm -m 2G \
	-machine type=pc,accel=kvm \
	-drive file=output-winserver-core/winserver-core.qcow2,format=qcow2,if=ide,cache=writeback,discard=ignore \
	-smp 2 -cpu host \
	-netdev user,id=user.0,hostfwd=tcp::22001-:22 \
	-device rtl8139,netdev=user.0 \
	-daemonize -vnc :1

kill:
	-kill $$(ps auxwww|grep qemu|grep tcp::22001-|awk '{print $$2}')

iso/SERVER_EVAL_x64FRE_en-us.iso:
	mkdir -p iso
	curl -fL --retry 3 --retry-delay 5 -o "iso/SERVER_EVAL_x64FRE_en-us.iso" "$ISO_URL"

output-winserver-core: iso/SERVER_EVAL_x64FRE_en-us.iso
	packer init windows-server-core.pkr.hcl
	packer build windows-server-core.pkr.hcl
	#debug version
	#PACKER_LOG=1 packer build windows-server-core.pkr.hcl 2>&1 | tee packer-build.log

build: output-winserver-core

clean:
	-rm packer-build.log

clean-build:
	-rm -rf output-winserver-core/
