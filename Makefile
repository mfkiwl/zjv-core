# Copyright (C) 2020 by phantom
# Email: admin@phvntom.tech
# This file is under MIT License, see http://phvntom.tech/LICENSE.txt

WORK_DIR	:=	$(CURDIR)/build
SRC_DIR		:=	$(CURDIR)/src

TARGET_CORE ?= rv32_1stage
VSRC_DIR := $(WORK_DIR)/verilog/$(TARGET_CORE)

VERILATOR_VSRC_DIR	:=	$(SRC_DIR)/main/verilator/vsrc
VERILATOR_CSRC_DIR	:=	$(SRC_DIR)/main/verilator/csrc
VERILATOR_DEST_DIR	:=	$(WORK_DIR)/verilator
VERILATOR_CXXFLAGS	:=	-O3 -std=c++11 -g -I$(VERILATOR_CSRC_DIR) -I$(VERILATOR_DEST_DIR)/build -I$(CURDIR)/riscv-isa-sim
VERILATOR_LDFLAGS 	:=	-lpthread
VERILATOR_SOURCE	:=	$(VERILATOR_CSRC_DIR)/emulator.cpp \
						$(VERILATOR_VSRC_DIR)/SimDTM.v \
						$(VERILATOR_CSRC_DIR)/SimDTM.cc

#$(sort $(wildcard $(VERILATOR_CSRC_DIR)/*.cpp))

VERILATOR_FLAGS := --cc --exe --top-module Top 	\
				  --assert --x-assign unique    \
				  --output-split 20000 -O3    	\
				  -I$(VERILATOR_VSRC_DIR) 	  	\
				  -CFLAGS "$(VERILATOR_CXXFLAGS)" \
				  -LDFLAGS "$(WORK_DIR)/fesvr/libfesvr.a $(VERILATOR_LDFLAGS)"

.PHONY: clean build generate_verilog

all: generate_verilog

generate_verilog: $(VSRC_DIR)/Top.v

$(VSRC_DIR)/Top.v:
	mkdir -p $(WORK_DIR)
	sbt "test:runMain $(TARGET_CORE).elaborate"

generate_emulator: $(VERILATOR_DEST_DIR)/emulator

FESVR_DEST_DIR := $(WORK_DIR)/fesvr
libfesvr := $(WORK_DIR)/fesvr/libfesvr.a

$(FESVR_DEST_DIR)/Makefile: $(CURDIR)/riscv-isa-sim/configure
	mkdir -p $(FESVR_DEST_DIR)
	cd $(FESVR_DEST_DIR) && $< --enable-sodor

$(WORK_DIR)/fesvr/libfesvr.a: $(FESVR_DEST_DIR)/Makefile
	$(MAKE) -C $(FESVR_DEST_DIR) $(notdir $(libfesvr))


$(VERILATOR_DEST_DIR)/emulator: $(VSRC_DIR)/Top.v $(WORK_DIR)/fesvr/libfesvr.a
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
