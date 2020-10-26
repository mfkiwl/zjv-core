default: generate_project

BOARD ?= nexys-a7

# vivado project name
PRJ ?= phvntom
PRJ_FULL = $(PRJ)
VIVADO_FLAG = -nolog -nojournal -notrace

#--------------------------------------------------------------------
# Project building and implementation
#--------------------------------------------------------------------

PRJ_ROOT = board/$(BOARD)/build/$(PRJ_FULL)
XPR_FILE = $(PRJ_ROOT)/$(PRJ_FULL).xpr
$(XPR_FILE):
	make -C .. 
	vivado $(VIVADO_FLAG) -mode batch -source generate_project.tcl -tclargs $(PRJ_ROOT) $(BOARD)

generate_project: $(XPR_FILE)

generate_bitstream:
	vivado $(VIVADO_FLAG) -mode batch -source generate_bit.tcl -tclargs $(PRJ_ROOT) $(BOARD)

vivado: $(XPR_FILE)
	vivado $(VIVADO_FLAG) $(XPR_FILE) &

clean:
	rm -rf $(PRJ_ROOT)

.PHONY: default generate_bitstream generate_project
