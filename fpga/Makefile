default: generate_project generate_bitstream

VIVADO_FLAG = -nolog -nojournal -notrace

#--------------------------------------------------------------------
# Project building and implementation
#--------------------------------------------------------------------

generate_bitstream:
	vivado $(VIVADO_FLAG) -mode batch -source generate_bit.tcl -tclargs $(PRJ_ROOT) $(BOARD)

generate_project:
	rm -rf ZJV4FPGA
	rm -rf ZJV
	mkdir ZJV
	cp -R vsrc/* ZJV
	vivado $(VIVADO_FLAG) -mode batch -source ZJV.tcl
	rm -rf ZJV

clean:
	rm -rf ZJV ZJV4FPGA .Xil

.PHONY: default generate_bitstream generate_project
