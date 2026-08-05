# Makefile for test/systems
# Orchestrates targets across all subdirectories with a Makefile.

SUBDIRS := $(dir $(wildcard */Makefile))

.PHONY: all build clean clean-build run $(SUBDIRS)

build clean run kill: $(SUBDIRS)

clean-build: clean build

$(SUBDIRS):
	@$(MAKE) -C $@ $(MAKECMDGOALS)

.DEFAULT_GOAL := build
