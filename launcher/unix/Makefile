default: default.target

export PNUTS_TOP = ../..
export PNUTS_BUILD_DIR = $(shell pwd)

include defs.mk
include rules.mk

all:
	$(MAKE) default

install:
	mkdir -p $(INSTALL_DIR)
	install -m 755 $(PNUTS_EXECUTABLE_FILES) $(INSTALL_DIR)
