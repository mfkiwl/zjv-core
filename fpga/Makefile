include Makefile.check

default: generate_bitstream

# STANDALONE ?= false

# vivado project name
PRJ ?= myproject
PRJ_FULL = $(PRJ)-$(BOARD)
VIVADO_FLAG = -nolog -nojournal -notrace

#--------------------------------------------------------------------
# Project building and implementation
#--------------------------------------------------------------------

PRJ_ROOT = board/$(BOARD)/build/$(PRJ_FULL)
# XPR_FILE = $(PRJ_ROOT)/$(PRJ_FULL).xpr
# $(XPR_FILE):
# 	make -C .. 
# 	vivado $(VIVADO_FLAG) -mode batch -source board/$(BOARD)/mk.tcl -tclargs $(PRJ_FULL)

# generate_project: $(XPR_FILE)

generate_bitstream:
	vivado $(VIVADO_FLAG) -mode batch -source generate_bit.tcl -tclargs $(PRJ_ROOT) $(BOARD)

# bootgen:
# 	cd boot && hsi $(VIVADO_FLAG) -source mk.tcl -tclargs $(PRJ_FULL)

# vivado: $(XPR_FILE)
# 	vivado $(VIVADO_FLAG) $(XPR_FILE) &

.PHONY: default generate_bitstream
