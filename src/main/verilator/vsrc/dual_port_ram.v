module dual_port_ram #(
	parameter DATA_WIDTH = 32,
	parameter DEPTH      = 1024,
	parameter LATENCY    = 1,
	parameter LATENCY_A  = LATENCY,
	parameter LATENCY_B  = LATENCY
) (
	input  clk,
	input  rst,
	input  wea,
	input  web,
	input  ena,
	input  enb,
	input  [$clog2(DEPTH)-1:0] addra,
	input  [$clog2(DEPTH)-1:0] addrb,
	input  [DATA_WIDTH-1:0] dina,
	input  [DATA_WIDTH-1:0] dinb,
	output [DATA_WIDTH-1:0] douta,
	output [DATA_WIDTH-1:0] doutb
);

// xpm_memory_tdpram: True Dual Port RAM
// Xilinx Parameterized Macro, Version 2016.2
xpm_memory_tdpram #(
	// Common module parameters
	.MEMORY_SIZE(DATA_WIDTH * DEPTH),
	.MEMORY_PRIMITIVE("auto"),
	.CLOCKING_MODE("common_clock"),
	.USE_MEM_INIT(0),
	.WAKEUP_TIME("disable_sleep"),
	.MESSAGE_CONTROL(0),

	// Port A module parameters
	.WRITE_DATA_WIDTH_A(DATA_WIDTH),
	.READ_DATA_WIDTH_A(DATA_WIDTH),
	.READ_RESET_VALUE_A("0"),
	.READ_LATENCY_A(LATENCY_A),
	.WRITE_MODE_A("read_first"),

	// Port B module parameters
	.WRITE_DATA_WIDTH_B(DATA_WIDTH),
	.READ_DATA_WIDTH_B(DATA_WIDTH),
	.READ_RESET_VALUE_B("0"),
	.READ_LATENCY_B(LATENCY_B),
	.WRITE_MODE_B("read_first")
) xpm_mem (
	// Common module ports
	.sleep          ( 1'b0  ),

	// Port A module ports
	.clka           ( clk   ),
	.rsta           ( rst   ),
	.ena            ( ena   ),
	.regcea         ( 1'b0  ),
	.wea            ( wea   ),
	.addra          ( addra ),
	.dina           ( dina  ),
	.injectsbiterra ( 1'b0  ), // do not change
	.injectdbiterra ( 1'b0  ), // do not change
	.douta          ( douta ),
	.sbiterra       (       ), // do not change
	.dbiterra       (       ), // do not change

	// Port B module ports
	.clkb           ( clk   ),
	.rstb           ( rst   ),
	.enb            ( enb   ),
	.regceb         ( 1'b0  ),
	.web            ( web   ),
	.addrb          ( addrb ),
	.dinb           ( dinb  ),
	.injectsbiterrb ( 1'b0  ), // do not change
	.injectdbiterrb ( 1'b0  ), // do not change
	.doutb          ( doutb ),
	.sbiterrb       (       ), // do not change
	.dbiterrb       (       )  // do not change
);

endmodule
