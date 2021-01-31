module SoC (
  input  clock_100,
  input  btn_resetn,
  // uart
  output uart_TX,
  input  uart_RX
);

  // (0x44000000L, 0x30000000L)  // MMIO Out of Tile : SPI UART BRAM
  //    (0x44000000L, 0x00001000L)  // UART 13bit (because of Vivado's UART IP Design)
  //    (0x44010000L, 0x00010000)  // BRAM 16bit
  //    (0X44020000L, 0x00010000)  // SPI 16bit
  // (0x38000000L, 0x00010000L)  // CLINT
  // (0x3c000000L, 0x04000000L)  // PLIC
  // (0x80000000L, undecidedL )  // SDRAM

  // CLK PLL
  // Description
  wire clock_30;
  wire clock_200;
  wire pll_locked;
  wire reset;
  assign reset = ~ pll_locked;
  clk_wiz_0 clk_gen(
    .clk_in1(clock_100),   //100MHz
    .clk_out1(clock_30),   //30MHz
    .clk_out2(clock_200),  //200MHz
    .resetn(btn_resetn),
    .locked(pll_locked)    // we use pll locked signal as resetn for ddr ctrl.
  );

  // CPU AXI Signals
  wire        cpu_io_mem_awready;
  wire        cpu_io_mem_awvalid;
  wire [31:0] cpu_io_mem_awaddr;
  wire [2:0]  cpu_io_mem_awprot;
  wire        cpu_io_mem_awid;
  wire        cpu_io_mem_awuser;
  wire [7:0]  cpu_io_mem_awlen;
  wire [2:0]  cpu_io_mem_awsize;
  wire [1:0]  cpu_io_mem_awburst;
  wire        cpu_io_mem_awlock;
  wire [3:0]  cpu_io_mem_awcache;
  wire [3:0]  cpu_io_mem_awqos;
  wire        cpu_io_mem_wready;
  wire        cpu_io_mem_wvalid;
  wire [63:0] cpu_io_mem_wdata;
  wire [7:0]  cpu_io_mem_wstrb;
  wire        cpu_io_mem_wlast;
  wire        cpu_io_mem_bready;
  wire        cpu_io_mem_bvalid;
  wire  [1:0] cpu_io_mem_bresp;
  wire        cpu_io_mem_bid;
  wire        cpu_io_mem_buser;
  wire        cpu_io_mem_arready;
  wire        cpu_io_mem_arvalid;
  wire [31:0] cpu_io_mem_araddr;
  wire [2:0]  cpu_io_mem_arprot;
  wire        cpu_io_mem_arid;
  wire        cpu_io_mem_aruser;
  wire [7:0]  cpu_io_mem_arlen;
  wire [2:0]  cpu_io_mem_arsize;
  wire [1:0]  cpu_io_mem_arburst;
  wire        cpu_io_mem_arlock;
  wire [3:0]  cpu_io_mem_arcache;
  wire [3:0]  cpu_io_mem_arqos;
  wire        cpu_io_mem_rready;
  wire        cpu_io_mem_rvalid;
  wire [1:0]  cpu_io_mem_rresp;
  wire [63:0] cpu_io_mem_rdata;
  wire        cpu_io_mem_rlast;
  wire        cpu_io_mem_rid;
  wire        cpu_io_mem_ruser;
  wire        cpu_io_mmio_awready;
  wire        cpu_io_mmio_awvalid;
  wire [31:0] cpu_io_mmio_awaddr;
  wire [2:0]  cpu_io_mmio_awprot;
  wire        cpu_io_mmio_wready;
  wire        cpu_io_mmio_wvalid;
  wire [63:0] cpu_io_mmio_wdata;
  wire [7:0]  cpu_io_mmio_wstrb;
  wire        cpu_io_mmio_bready;
  wire        cpu_io_mmio_bvalid;
  wire [1:0]  cpu_io_mmio_bresp;
  wire        cpu_io_mmio_arready;
  wire        cpu_io_mmio_arvalid;
  wire [31:0] cpu_io_mmio_araddr;
  wire [2:0]  cpu_io_mmio_arprot;
  wire        cpu_io_mmio_rready;
  wire        cpu_io_mmio_rvalid;
  wire [1:0]  cpu_io_mmio_rresp;
  wire [63:0] cpu_io_mmio_rdata;
  wire [7:0]  cpu_io_mmio_awlen;
  wire [2:0]  cpu_io_mmio_awsize;
  wire [1:0]  cpu_io_mmio_awburst;
  wire        cpu_io_mmio_awlock;
  wire [3:0]  cpu_io_mmio_awcache;
  wire [3:0]  cpu_io_mmio_awqos;
  wire        cpu_io_mmio_wlast;
  wire [7:0]  cpu_io_mmio_arlen;
  wire [2:0]  cpu_io_mmio_arsize;
  wire [1:0]  cpu_io_mmio_arburst;
  wire        cpu_io_mmio_arlock;
  wire [3:0]  cpu_io_mmio_arcache;
  wire [3:0]  cpu_io_mmio_arqos;
  wire        cpu_io_mmio_rlast;
  wire        cpu_io_mmio_awid;
  wire        cpu_io_mmio_bid;
  wire        cpu_io_mmio_arid;
  wire        cpu_io_mmio_rid;

  // UART Interrupt
  wire        io_meip;

  Mem axi4_mem (
    .clock(clock_30),
    .clock_200(clock_200),
    .reset(reset)
  );
  // TODO: Add DDR2 Driver
  assign cpu_io_mem_awready   = 0;
  assign cpu_io_mem_wready    = 0;
  assign cpu_io_mem_bvalid    = 0;
  assign cpu_io_mem_bid       = 0;
  assign cpu_io_mem_bresp     = 0;
  assign cpu_io_mem_arready   = 0;
  assign cpu_io_mem_rvalid    = 0;
  assign cpu_io_mem_rid       = 0;
  assign cpu_io_mem_rdata     = 0;
  assign cpu_io_mem_rresp     = 0;
  assign cpu_io_mem_rlast     = 0;

  // MMIO
  MemMappedIO axi4_mmio (
    .clock(clock_30),
    .reset(reset),
    // AXI
    .m0_axi4_aw_ready(cpu_io_mmio_awready),
    .m0_axi4_aw_valid(cpu_io_mmio_awvalid),
    .m0_axi4_aw_id(cpu_io_mmio_awid),
    .m0_axi4_aw_addr(cpu_io_mmio_awaddr),
    .m0_axi4_aw_len(cpu_io_mmio_awlen),
    .m0_axi4_aw_size(cpu_io_mmio_awsize),
    .m0_axi4_aw_burst(cpu_io_mmio_awburst),
    .m0_axi4_w_ready(cpu_io_mmio_wready),
    .m0_axi4_w_valid(cpu_io_mmio_wvalid),
    .m0_axi4_w_data(cpu_io_mmio_wdata),
    .m0_axi4_w_strb(cpu_io_mmio_wstrb),
    .m0_axi4_w_last(cpu_io_mmio_wlast),
    .m0_axi4_b_ready(cpu_io_mmio_bready),
    .m0_axi4_b_valid(cpu_io_mmio_bvalid),
    .m0_axi4_b_id(cpu_io_mmio_bid),
    .m0_axi4_b_resp(cpu_io_mmio_bresp),
    .m0_axi4_ar_ready(cpu_io_mmio_arready),
    .m0_axi4_ar_valid(cpu_io_mmio_arvalid),
    .m0_axi4_ar_id(cpu_io_mmio_arid),
    .m0_axi4_ar_addr(cpu_io_mmio_araddr),
    .m0_axi4_ar_len(cpu_io_mmio_arlen),
    .m0_axi4_ar_size(cpu_io_mmio_arsize),
    .m0_axi4_ar_burst(cpu_io_mmio_arburst),
    .m0_axi4_r_ready(cpu_io_mmio_rready),
    .m0_axi4_r_valid(cpu_io_mmio_rvaid),
    .m0_axi4_r_id(cpu_io_mmio_rid),
    .m0_axi4_r_data(cpu_io_mmio_rdata),
    .m0_axi4_r_resp(cpu_io_mmio_rresp),
    .m0_axi4_r_last(cpu_io_mmio_rlast),
    // UART
    .uart_RX(uart_RX),
    .uart_TX(uart_TX),
    .meip(io_meip)
  );

  zjv_fpga_zjv cpu (
    .clock(clock_30),
    .reset(reset),
    .io_mem_awready(cpu_io_mem_awready),
    .io_mem_awvalid(cpu_io_mem_awvalid),
    .io_mem_awaddr(cpu_io_mem_awaddr),
    .io_mem_awprot(cpu_io_mem_awprot),
    .io_mem_awid(cpu_io_mem_awid),
    .io_mem_awuser(cpu_io_mem_awuser),
    .io_mem_awlen(cpu_io_mem_awlen),
    .io_mem_awsize(cpu_io_mem_awsize),
    .io_mem_awburst(cpu_io_mem_awburst),
    .io_mem_awlock(cpu_io_mem_awlock),
    .io_mem_awcache(cpu_io_mem_awcache),
    .io_mem_awqos(cpu_io_mem_awqos),
    .io_mem_wready(cpu_io_mem_wready),
    .io_mem_wvalid(cpu_io_mem_wvalid),
    .io_mem_wdata(cpu_io_mem_wdata),
    .io_mem_wstrb(cpu_io_mem_wstrb),
    .io_mem_wlast(cpu_io_mem_wlast),
    .io_mem_bready(cpu_io_mem_bready),
    .io_mem_bvalid(cpu_io_mem_bvalid),
    .io_mem_bresp(cpu_io_mem_bresp),
    .io_mem_bid(cpu_io_mem_bid),
    .io_mem_buser(cpu_io_mem_buser),
    .io_mem_arready(cpu_io_mem_arready),
    .io_mem_arvalid(cpu_io_mem_arvalid),
    .io_mem_araddr(cpu_io_mem_araddr),
    .io_mem_arprot(cpu_io_mem_arprot),
    .io_mem_arid(cpu_io_mem_arid),
    .io_mem_aruser(cpu_io_mem_aruser),
    .io_mem_arlen(cpu_io_mem_arlen),
    .io_mem_arsize(cpu_io_mem_arsize),
    .io_mem_arburst(cpu_io_mem_arburst),
    .io_mem_arlock(cpu_io_mem_arlock),
    .io_mem_arcache(cpu_io_mem_arcache),
    .io_mem_arqos(cpu_io_mem_arqos),
    .io_mem_rready(cpu_io_mem_rready),
    .io_mem_rvalid(cpu_io_mem_rvalid),
    .io_mem_rresp(cpu_io_mem_rresp),
    .io_mem_rdata(cpu_io_mem_rdata),
    .io_mem_rlast(cpu_io_mem_rlast),
    .io_mem_rid(cpu_io_mem_rid),
    .io_mem_ruser(cpu_io_mem_ruser),
    .io_mmio_awready(cpu_io_mmio_awready),
    .io_mmio_awvalid(cpu_io_mmio_awvalid),
    .io_mmio_awaddr(cpu_io_mmio_awaddr),
    .io_mmio_awprot(cpu_io_mmio_awprot),
    .io_mmio_wready(cpu_io_mmio_wready),
    .io_mmio_wvalid(cpu_io_mmio_wvalid),
    .io_mmio_wdata(cpu_io_mmio_wdata),
    .io_mmio_wstrb(cpu_io_mmio_wstrb),
    .io_mmio_bready(cpu_io_mmio_bready),
    .io_mmio_bvalid(cpu_io_mmio_bvalid),
    .io_mmio_bresp(cpu_io_mmio_bresp),
    .io_mmio_arready(cpu_io_mmio_arready),
    .io_mmio_arvalid(cpu_io_mmio_arvalid),
    .io_mmio_araddr(cpu_io_mmio_araddr),
    .io_mmio_arprot(cpu_io_mmio_arprot),
    .io_mmio_rready(cpu_io_mmio_rready),
    .io_mmio_rvalid(cpu_io_mmio_rvalid),
    .io_mmio_rresp(cpu_io_mmio_rresp),
    .io_mmio_rdata(cpu_io_mmio_rdata),
    .io_mmio_awlen(cpu_io_mmio_awlen),
    .io_mmio_awsize(cpu_io_mmio_awsize),
    .io_mmio_awburst(cpu_io_mmio_awburst),
    .io_mmio_awlock(cpu_io_mmio_awlock),
    .io_mmio_awcache(cpu_io_mmio_awcache),
    .io_mmio_awqos(cpu_io_mmio_awqos),
    .io_mmio_wlast(cpu_io_mmio_wlast),
    .io_mmio_arlen(cpu_io_mmio_arlen),
    .io_mmio_arsize(cpu_io_mmio_arsize),
    .io_mmio_arburst(cpu_io_mmio_arburst),
    .io_mmio_arlock(cpu_io_mmio_arlock),
    .io_mmio_arcache(cpu_io_mmio_arcache),
    .io_mmio_arqos(cpu_io_mmio_arqos),
    .io_mmio_rlast(cpu_io_mmio_rlast),
    .io_mmio_awid(cpu_io_mmio_awid),
    .io_mmio_bid(cpu_io_mmio_bid),
    .io_mmio_arid(cpu_io_mmio_arid),
    .io_mmio_rid(cpu_io_mmio_rid),
    .io_meip(io_meip)
  );

endmodule
