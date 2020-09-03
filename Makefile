# Copyright (C) 2020 by phantom
# Email: admin@phvntom.tech
# This file is under MIT License, see http://phvntom.tech/LICENSE.txt

WORK_DIR	:=	$(CURDIR)/build
SRC_DIR		:=	$(CURDIR)/src

TARGET_CORE ?= rv32_1stage
VSRC_DIR := $(WORK_DIR)/verilog/$(TARGET_CORE)

# DiffTest
SPIKE_SRC_DIR 	:= $(CURDIR)/riscv-isa-sim
SPIKE_DEST_DIR 	:= $(WORK_DIR)/spike
libfdt 			:= $(SPIKE_DEST_DIR)/libfdt.a
libfesvr 		:= $(SPIKE_DEST_DIR)/libfesvr.a
libriscv 		:= $(SPIKE_DEST_DIR)/libriscv.a
libsoftfloat 	:= $(SPIKE_DEST_DIR)/libsoftfloat.a

# Verilator
VERILATOR_VSRC_DIR	:=	$(SRC_DIR)/main/verilator/vsrc
VERILATOR_CSRC_DIR	:=	$(SRC_DIR)/main/verilator/csrc
VERILATOR_DEST_DIR	:=	$(WORK_DIR)/verilator
VERILATOR_CXXFLAGS	:=	-O3 -std=c++11 -g -I$(VERILATOR_CSRC_DIR) -I$(VERILATOR_DEST_DIR)/build -I$(SPIKE_SRC_DIR) -I$(SPIKE_DEST_DIR) -I$(SPIKE_SRC_DIR)/softfloat
VERILATOR_LDFLAGS 	:=	-lpthread -ldl
VERILATOR_SOURCE	:=	$(VERILATOR_CSRC_DIR)/emulator.cpp \
						$(VERILATOR_VSRC_DIR)/SimDTM.v \
						$(VERILATOR_CSRC_DIR)/SimDTM.cc
#$(sort $(wildcard $(VERILATOR_CSRC_DIR)/*.cpp))

VERILATOR_FLAGS := --cc --exe --top-module Top 	\
				  --assert --x-assign unique    \
				  --output-split 20000 -O3    	\
				  -I$(VERILATOR_VSRC_DIR) 	  	\
				  -CFLAGS "$(VERILATOR_CXXFLAGS)" \
				  -LDFLAGS "$(libfesvr) $(libriscv) $(libsoftfloat) $(libfdt) $(VERILATOR_LDFLAGS) "

.PHONY: clean build generate_verilog

all: generate_verilog

generate_verilog: $(VSRC_DIR)/Top.v

$(VSRC_DIR)/Top.v:
	mkdir -p $(WORK_DIR)
	sbt "test:runMain $(TARGET_CORE).elaborate"

generate_emulator: $(VERILATOR_DEST_DIR)/emulator
	$^ ./test/rv32mi-p-csr

$(SPIKE_DEST_DIR)/Makefile: $(SPIKE_SRC_DIR)/configure
	mkdir -p $(SPIKE_DEST_DIR)
	cd $(SPIKE_DEST_DIR) && $< --enable-sodor --enable-commitlog

$(libriscv): $(SPIKE_DEST_DIR)/Makefile
	$(MAKE) -C $(SPIKE_DEST_DIR) $(notdir $(libfesvr)) $(notdir $(libriscv)) $(notdir $(libsoftfloat)) $(notdir $(libfdt)) 


$(VERILATOR_DEST_DIR)/emulator: $(VSRC_DIR)/Top.v $(libriscv)
	mkdir -p $(VERILATOR_DEST_DIR)
	verilator $(VERILATOR_FLAGS) -o $(VERILATOR_DEST_DIR)/emulator -Mdir $(VERILATOR_DEST_DIR)/build $^ $(VERILATOR_SOURCE)
	$(MAKE) -C $(VERILATOR_DEST_DIR)/build -f $(VERILATOR_DEST_DIR)/build/VTop.mk

how_verilator_work:
	mkdir -p $(VERILATOR_DEST_DIR)/Hello
	verilator --cc --exe -Wall -o $(VERILATOR_DEST_DIR)/Hello/Hello -Mdir $(VERILATOR_DEST_DIR)/Hello $(VERILATOR_VSRC_DIR)/Hello.v $(VERILATOR_CSRC_DIR)/Hello.cpp
	$(MAKE) -C $(VERILATOR_DEST_DIR)/Hello -f $(VERILATOR_DEST_DIR)/Hello/VHello.mk
	$(VERILATOR_DEST_DIR)/Hello/Hello

clean:
	rm -rf build
