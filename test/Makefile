SRC_DIR     := $(CURDIR)
DEST_DIR    ?= $(CURDIR)/../build/test

## XXX_test_src XXX_test_target
include $(CURDIR)/rv64ui/Makefile
include $(CURDIR)/uart/Makefile
include $(CURDIR)/rv64mi/Makefile

RISCV_PREFIX   := riscv64-unknown-elf
LINK_SCRIPT    := -T$(CURDIR)/common/zjv.ld 
INCLUDE_DIR    := -I$(CURDIR)/common
RISCV_ISA      := -march=rv64g -mabi=lp64
CXX_FLAGS      := $(RISCV_ISA) -nostdlib -nostartfiles 

ifeq (, $(shell which $(RISCV_PREFIX)-gcc))
$(error "No riscv-toolchain in $$PATH")
endif

default: all

define compile_template

$$($(1)_test_target): %: $$(SRC_DIR)/$(1)/%.S
	$$(RISCV_PREFIX)-gcc $$(CXX_FLAGS) $$(INCLUDE_DIR) $$(LINK_SCRIPT) $$< -o $$(DEST_DIR)/$(1)_$$@

.PHONY: $(1)
tests += $$($(1)_test_target)

endef

$(DEST_DIR):
	mkdir -p $(DEST_DIR)

# $(eval $(call compile_template,rv64ui))
$(eval $(call compile_template,rv64mi))
# $(eval $(call compile_template,uart))

all: $(DEST_DIR) $(tests)