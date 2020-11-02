# Copyright (C) 2020 by phantom
# Email: admin@phvntom.tech
# This file is under MIT License, see http://phvntom.tech/LICENSE.txt

WORK_DIR	:=	$(CURDIR)/build
SRC_DIR		:=	$(CURDIR)/src

TARGET_CORE ?= rv64_nstage.core
VSRC_DIR := $(WORK_DIR)/verilog/$(TARGET_CORE)
N ?= 6

# Test ELF 
TEST_SRC_DIR  := $(CURDIR)/zjv-soc-test
TEST_DST_DIR  := $(WORK_DIR)/test
TEST_ELF_LIST := $(sort $(wildcard $(TEST_DST_DIR)/*))


# DiffTest
SPIKE_SRC_DIR 	:= $(CURDIR)/riscv-isa-sim
SPIKE_DEST_DIR 	:= $(WORK_DIR)/spike
libfdt 			:= $(SPIKE_DEST_DIR)/libfdt.a
libfesvr 		:= $(SPIKE_DEST_DIR)/libfesvr.a
libriscv 		:= $(SPIKE_DEST_DIR)/libriscv.a
libsoftfloat 	:= $(SPIKE_DEST_DIR)/libsoftfloat.a
libspike        := $(libfdt) $(libfesvr) $(libriscv) $(libsoftfloat)

# Verilator
VERILATOR_SRC_DIR   :=  $(SRC_DIR)/main/verilator
VERILATOR_VSRC_DIR	:=	$(VERILATOR_SRC_DIR)/vsrc
VERILATOR_CSRC_DIR	:=	$(VERILATOR_SRC_DIR)/csrc
VERILATOR_DEST_DIR	:=	$(WORK_DIR)/verilator
VERILATOR_CXXFLAGS	:=	-O3 -std=c++11 -g -I$(VERILATOR_CSRC_DIR) -I$(VERILATOR_DEST_DIR)/build -I$(SPIKE_SRC_DIR) -I$(SPIKE_SRC_DIR)/softfloat -I$(SPIKE_DEST_DIR)
VERILATOR_LDFLAGS 	:=	-lpthread -ldl -L$(SPIKE_DEST_DIR) -lfesvr -lriscv -lfdt -lsoftfloat
VERILATOR_SOURCE 	:= $(sort $(wildcard $(VERILATOR_CSRC_DIR)/*.cpp)) $(sort $(wildcard $(VERILATOR_VSRC_DIR)/*.v))

VERILATOR_FLAGS := --cc --exe --top-module Top 	\
				  --threads $(N) \
				  --assert --x-assign unique    \
				  --output-split 20000 -O3    	\
				  -I$(VERILATOR_VSRC_DIR) 	  	\
				  -CFLAGS "$(VERILATOR_CXXFLAGS) -DZJV_DEBUG" \
				  -LDFLAGS "$(libfesvr) $(libriscv) $(libsoftfloat) $(libfdt) $(VERILATOR_LDFLAGS)"


ifneq (,$(VTRACE))
VERILATOR_FLAGS += --debug --trace
endif

.PHONY: clean build generate_verilog generate_emulator

all: generate_verilog

difftest_target = $(foreach elf,$(TEST_ELF_LIST),$(addsuffix _dt,$(elf)))

$(difftest_target): %_dt: $(VERILATOR_DEST_DIR)/emulator %
	$^
	@sleep 1

generate_verilog: $(VSRC_DIR)/Top.v

$(VSRC_DIR)/Top.v: $(SRC_DIR)/main/scala
	mkdir -p $(WORK_DIR)
	sbt "runMain $(TARGET_CORE).elaborate"

generate_emulator: $(VERILATOR_DEST_DIR)/emulator $(difftest_target)

$(SPIKE_DEST_DIR)/Makefile: $(SPIKE_SRC_DIR)/configure
	mkdir -p $(SPIKE_DEST_DIR)
	cd $(SPIKE_DEST_DIR) && $< --enable-commitlog --enable-zjv-device

$(libspike): $(SPIKE_DEST_DIR)/Makefile
	$(MAKE) -C $(SPIKE_DEST_DIR) $(notdir $(libfesvr)) $(notdir $(libriscv)) $(notdir $(libsoftfloat)) $(notdir $(libfdt)) 


$(VERILATOR_DEST_DIR)/emulator: $(VSRC_DIR)/Top.v $(libspike) $(VERILATOR_SOURCE)
	mkdir -p $(VERILATOR_DEST_DIR)
	verilator $(VERILATOR_FLAGS) -o $(VERILATOR_DEST_DIR)/emulator -Mdir $(VERILATOR_DEST_DIR)/build $^
	$(MAKE) -C $(VERILATOR_DEST_DIR)/build -f $(VERILATOR_DEST_DIR)/build/VTop.mk

generate_testcase:
	mkdir -p $(TEST_DST_DIR)
	$(MAKE) -C $(TEST_SRC_DIR) DEST_DIR=$(TEST_DST_DIR)

how_verilator_work:
	mkdir -p $(VERILATOR_DEST_DIR)/Hello
	verilator --cc --exe -Wall -o $(VERILATOR_DEST_DIR)/Hello/Hello -Mdir $(VERILATOR_DEST_DIR)/Hello $(VERILATOR_SRC_DIR)/Hello/Hello.v $(VERILATOR_SRC_DIR)/Hello/Hello.cpp
	$(MAKE) -C $(VERILATOR_DEST_DIR)/Hello -f $(VERILATOR_DEST_DIR)/Hello/VHello.mk
	$(VERILATOR_DEST_DIR)/Hello/Hello

clean:
	rm -rf build
