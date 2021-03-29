module single_port_ram_bpu (
	input  clk,
	input  rst,
	input  we,
	input  [7:0] addr,
	input  [38:0]  din,
	output [38:0]  dout
);

// xpm_memory_spram: Single Port RAM
// Xilinx Parameterized Macro, Version 2016.2
xpm_memory_spram #(
	// Common module parameters
	.MEMORY_SIZE(39 * 256),
	.MEMORY_PRIMITIVE("auto"),
	.USE_MEM_INIT(0),
	.WAKEUP_TIME("disable_sleep"),
	.MESSAGE_CONTROL(0),

	// Port A module parameters
	.WRITE_DATA_WIDTH_A(39),
	.READ_DATA_WIDTH_A(39),
	.READ_RESET_VALUE_A("0"),
	.READ_LATENCY_A(1),
	.WRITE_MODE_A("write_first")
) xpm_mem (
	// Common module ports
	.sleep          ( 1'b0  ),

	// Port A module ports
	.clka           ( clk   ),
	.rsta           ( rst   ),
	.ena            ( 1'b1  ),
	.regcea         ( 1'b0  ),
	.wea            ( we    ),
	.addra          ( addr  ),
	.dina           ( din   ),
	.injectsbiterra ( 1'b0  ), // do not change
	.injectdbiterra ( 1'b0  ), // do not change
	.douta          ( dout  ),
	.sbiterra       (       ), // do not change
	.dbiterra       (       )  // do not change
);

endmodule
