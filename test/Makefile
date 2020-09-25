INCLUDE_DIR := common
ISA_DIR 	:= isa

SRC_DIR     := $(CURDIR)
DEST_DIR    ?= $(SRC_DIR)/../build/test

include $(SRC_DIR)/isa/rv64i/Makefile
## rv64i_tests

RISCV_PREFIX   := riscv64-unknown-elf
LINK_SCRIPT    := -T$(SRC_DIR)/common/zjv.ld 
INCLUDE_DIR    := -I$(SRC_DIR)/common
RISCV_ISA      := -march=rv64g -mabi=lp64
CXX_FLAGS      := $(RISCV_ISA) -nostdlib -nostartfiles 

ifeq (, $(shell which $(RISCV_PREFIX)-gcc))
$(error "No riscv-toolchain in $$PATH")
endif

default: all

define compile_template

isa/$(1)/%.S:
	echo $$<

$$($(1)_tests): isa/$(1)/*.S
	$$(RISCV_PREFIX)-gcc $$(CXX_FLAGS) $$(INCLUDE_DIR) $$(LINK_SCRIPT) $$< -o $$(DEST_DIR)/$(1)
.PHONY: $(1)

tests += $$($(1)_tests)

endef

$(eval $(call compile_template,rv64i))

$(DEST_DIR):
	mkdir -p $(DEST_DIR)

all: $(DEST_DIR) $(tests)