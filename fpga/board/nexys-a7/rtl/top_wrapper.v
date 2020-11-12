`timescale 1ns / 1ps

module mycpu_top(
    input         clock, 
    input         resetn,
    input UART_CTS,
    output UART_RTS,
    input UART_RXD_OUT,
    output UART_TXD_IN
    );

  wire [63:0]M00_AXI_araddr;
  wire [1:0]M00_AXI_arburst;
  wire [3:0]M00_AXI_arcache;
  wire [7:0]M00_AXI_arlen;
  wire [0:0]M00_AXI_arlock;
  wire [2:0]M00_AXI_arprot;
  wire [3:0]M00_AXI_arqos;
  wire [0:0]M00_AXI_arready;
  wire [3:0]M00_AXI_arregion;
  wire [2:0]M00_AXI_arsize;
  wire [0:0]M00_AXI_arvalid;
  wire [63:0]M00_AXI_awaddr;
  wire [1:0]M00_AXI_awburst;
  wire [3:0]M00_AXI_awcache;
  wire [7:0]M00_AXI_awlen;
  wire [0:0]M00_AXI_awlock;
  wire [2:0]M00_AXI_awprot;
  wire [3:0]M00_AXI_awqos;
  wire [0:0]M00_AXI_awready;
  wire [3:0]M00_AXI_awregion;
  wire [2:0]M00_AXI_awsize;
  wire [0:0]M00_AXI_awvalid;
  wire [0:0]M00_AXI_bready;
  wire [1:0]M00_AXI_bresp;
  wire [0:0]M00_AXI_bvalid;
  wire [63:0]M00_AXI_rdata;
  wire [0:0]M00_AXI_rlast;
  wire [0:0]M00_AXI_rready;
  wire [1:0]M00_AXI_rresp;
  wire [0:0]M00_AXI_rvalid;
  wire [63:0]M00_AXI_wdata;
  wire [0:0]M00_AXI_wlast;
  wire [0:0]M00_AXI_wready;
  wire [7:0]M00_AXI_wstrb;
  wire [0:0]M00_AXI_wvalid;
  wire [63:0]M01_AXI_araddr;
  wire [1:0]M01_AXI_arburst;
  wire [3:0]M01_AXI_arcache;
  wire [7:0]M01_AXI_arlen;
  wire [0:0]M01_AXI_arlock;
  wire [2:0]M01_AXI_arprot;
  wire [3:0]M01_AXI_arqos;
  wire [0:0]M01_AXI_arready;
  wire [3:0]M01_AXI_arregion;
  wire [2:0]M01_AXI_arsize;
  wire [0:0]M01_AXI_arvalid;
  wire [63:0]M01_AXI_awaddr;
  wire [1:0]M01_AXI_awburst;
  wire [3:0]M01_AXI_awcache;
  wire [7:0]M01_AXI_awlen;
  wire [0:0]M01_AXI_awlock;
  wire [2:0]M01_AXI_awprot;
  wire [3:0]M01_AXI_awqos;
  wire [0:0]M01_AXI_awready;
  wire [3:0]M01_AXI_awregion;
  wire [2:0]M01_AXI_awsize;
  wire [0:0]M01_AXI_awvalid;
  wire [0:0]M01_AXI_bready;
  wire [1:0]M01_AXI_bresp;
  wire [0:0]M01_AXI_bvalid;
  wire [63:0]M01_AXI_rdata;
  wire [0:0]M01_AXI_rlast;
  wire [0:0]M01_AXI_rready;
  wire [1:0]M01_AXI_rresp;
  wire [0:0]M01_AXI_rvalid;
  wire [63:0]M01_AXI_wdata;
  wire [0:0]M01_AXI_wlast;
  wire [0:0]M01_AXI_wready;
  wire [7:0]M01_AXI_wstrb;
  wire [0:0]M01_AXI_wvalid;
  wire [63:0]S00_AXI_araddr;
  wire [1:0]S00_AXI_arburst;
  wire [3:0]S00_AXI_arcache;
  wire [7:0]S00_AXI_arlen;
  wire [0:0]S00_AXI_arlock;
  wire [2:0]S00_AXI_arprot;
  wire [3:0]S00_AXI_arqos;
  wire [0:0]S00_AXI_arready;
  wire [2:0]S00_AXI_arsize;
  wire [0:0]S00_AXI_arvalid;
  wire [63:0]S00_AXI_awaddr;
  wire [1:0]S00_AXI_awburst;
  wire [3:0]S00_AXI_awcache;
  wire [7:0]S00_AXI_awlen;
  wire [0:0]S00_AXI_awlock;
  wire [2:0]S00_AXI_awprot;
  wire [3:0]S00_AXI_awqos;
  wire [0:0]S00_AXI_awready;
  wire [2:0]S00_AXI_awsize;
  wire [0:0]S00_AXI_awvalid;
  wire [0:0]S00_AXI_bready;
  wire [1:0]S00_AXI_bresp;
  wire [0:0]S00_AXI_bvalid;
  wire [63:0]S00_AXI_rdata;
  wire [0:0]S00_AXI_rlast;
  wire [0:0]S00_AXI_rready;
  wire [1:0]S00_AXI_rresp;
  wire [0:0]S00_AXI_rvalid;
  wire [63:0]S00_AXI_wdata;
  wire [0:0]S00_AXI_wlast;
  wire [0:0]S00_AXI_wready;
  wire [7:0]S00_AXI_wstrb;
  wire [0:0]S00_AXI_wvalid;
  wire [63:0]S01_AXI_araddr;
  wire [1:0]S01_AXI_arburst;
  wire [3:0]S01_AXI_arcache;
  wire [7:0]S01_AXI_arlen;
  wire [0:0]S01_AXI_arlock;
  wire [2:0]S01_AXI_arprot;
  wire [3:0]S01_AXI_arqos;
  wire [0:0]S01_AXI_arready;
  wire [2:0]S01_AXI_arsize;
  wire [0:0]S01_AXI_arvalid;
  wire [63:0]S01_AXI_awaddr;
  wire [1:0]S01_AXI_awburst;
  wire [3:0]S01_AXI_awcache;
  wire [7:0]S01_AXI_awlen;
  wire [0:0]S01_AXI_awlock;
  wire [2:0]S01_AXI_awprot;
  wire [3:0]S01_AXI_awqos;
  wire [0:0]S01_AXI_awready;
  wire [2:0]S01_AXI_awsize;
  wire [0:0]S01_AXI_awvalid;
  wire [0:0]S01_AXI_bready;
  wire [1:0]S01_AXI_bresp;
  wire [0:0]S01_AXI_bvalid;
  wire [63:0]S01_AXI_rdata;
  wire [0:0]S01_AXI_rlast;
  wire [0:0]S01_AXI_rready;
  wire [1:0]S01_AXI_rresp;
  wire [0:0]S01_AXI_rvalid;
  wire [63:0]S01_AXI_wdata;
  wire [0:0]S01_AXI_wlast;
  wire [0:0]S01_AXI_wready;
  wire [7:0]S01_AXI_wstrb;
  wire [0:0]S01_AXI_wvalid;
  wire ctsn;
  wire ip2intc_irpt;

ext_device_wrapper axi(
    .ACLK(clock),
    .ARESETN(resetn),
    .M00_AXI_araddr(M00_AXI_araddr),
    .M00_AXI_arburst(M00_AXI_arburst),
    .M00_AXI_arcache(M00_AXI_arcache),
    .M00_AXI_arlen(M00_AXI_arlen),
    .M00_AXI_arlock(M00_AXI_arlock),
    .M00_AXI_arprot(M00_AXI_arprot),
    .M00_AXI_arqos(M00_AXI_arqos),
    .M00_AXI_arready(M00_AXI_arready),
    .M00_AXI_arregion(M00_AXI_arregion),
    .M00_AXI_arsize(M00_AXI_arsize),
    .M00_AXI_arvalid(M00_AXI_arvalid),
    .M00_AXI_awaddr(M00_AXI_awaddr),
    .M00_AXI_awburst(M00_AXI_awburst),
    .M00_AXI_awcache(M00_AXI_awcache),
    .M00_AXI_awlen(M00_AXI_awlen),
    .M00_AXI_awlock(M00_AXI_awlock),
    .M00_AXI_awprot(M00_AXI_awprot),
    .M00_AXI_awqos(M00_AXI_awqos),
    .M00_AXI_awready(M00_AXI_awready),
    .M00_AXI_awregion(M00_AXI_awregion),
    .M00_AXI_awsize(M00_AXI_awsize),
    .M00_AXI_awvalid(M00_AXI_awvalid),
    .M00_AXI_bready(M00_AXI_bready),
    .M00_AXI_bresp(M00_AXI_bresp),
    .M00_AXI_bvalid(M00_AXI_bvalid),
    .M00_AXI_rdata(M00_AXI_rdata),
    .M00_AXI_rlast(M00_AXI_rlast),
    .M00_AXI_rready(M00_AXI_rready),
    .M00_AXI_rresp(M00_AXI_rresp),
    .M00_AXI_rvalid(M00_AXI_rvalid),
    .M00_AXI_wdata(M00_AXI_wdata),
    .M00_AXI_wlast(M00_AXI_wlast),
    .M00_AXI_wready(M00_AXI_wready),
    .M00_AXI_wstrb(M00_AXI_wstrb),
    .M00_AXI_wvalid(M00_AXI_wvalid),
    .M01_AXI_araddr(M01_AXI_araddr),
    .M01_AXI_arburst(M01_AXI_arburst),
    .M01_AXI_arcache(M01_AXI_arcache),
    .M01_AXI_arlen(M01_AXI_arlen),
    .M01_AXI_arlock(M01_AXI_arlock),
    .M01_AXI_arprot(M01_AXI_arprot),
    .M01_AXI_arqos(M01_AXI_arqos),
    .M01_AXI_arready(M01_AXI_arready),
    .M01_AXI_arregion(M01_AXI_arregion),
    .M01_AXI_arsize(M01_AXI_arsize),
    .M01_AXI_arvalid(M01_AXI_arvalid),
    .M01_AXI_awaddr(M01_AXI_awaddr),
    .M01_AXI_awburst(M01_AXI_awburst),
    .M01_AXI_awcache(M01_AXI_awcache),
    .M01_AXI_awlen(M01_AXI_awlen),
    .M01_AXI_awlock(M01_AXI_awlock),
    .M01_AXI_awprot(M01_AXI_awprot),
    .M01_AXI_awqos(M01_AXI_awqos),
    .M01_AXI_awready(M01_AXI_awready),
    .M01_AXI_awregion(M01_AXI_awregion),
    .M01_AXI_awsize(M01_AXI_awsize),
    .M01_AXI_awvalid(M01_AXI_awvalid),
    .M01_AXI_bready(M01_AXI_bready),
    .M01_AXI_bresp(M01_AXI_bresp),
    .M01_AXI_bvalid(M01_AXI_bvalid),
    .M01_AXI_rdata(M01_AXI_rdata),
    .M01_AXI_rlast(M01_AXI_rlast),
    .M01_AXI_rready(M01_AXI_rready),
    .M01_AXI_rresp(M01_AXI_rresp),
    .M01_AXI_rvalid(M01_AXI_rvalid),
    .M01_AXI_wdata(M01_AXI_wdata),
    .M01_AXI_wlast(M01_AXI_wlast),
    .M01_AXI_wready(M01_AXI_wready),
    .M01_AXI_wstrb(M01_AXI_wstrb),
    .M01_AXI_wvalid(M01_AXI_wvalid),
    .S00_AXI_araddr(S00_AXI_araddr),
    .S00_AXI_arburst(S00_AXI_arburst),
    .S00_AXI_arcache(S00_AXI_arcache),
    .S00_AXI_arlen(S00_AXI_arlen),
    .S00_AXI_arlock(S00_AXI_arlock),
    .S00_AXI_arprot(S00_AXI_arprot),
    .S00_AXI_arqos(S00_AXI_arqos),
    .S00_AXI_arready(S00_AXI_arready),
    .S00_AXI_arsize(S00_AXI_arsize),
    .S00_AXI_arvalid(S00_AXI_arvalid),
    .S00_AXI_awaddr(S00_AXI_awaddr),
    .S00_AXI_awburst(S00_AXI_awburst),
    .S00_AXI_awcache(S00_AXI_awcache),
    .S00_AXI_awlen(S00_AXI_awlen),
    .S00_AXI_awlock(S00_AXI_awlock),
    .S00_AXI_awprot(S00_AXI_awprot),
    .S00_AXI_awqos(S00_AXI_awqos),
    .S00_AXI_awready(S00_AXI_awready),
    .S00_AXI_awsize(S00_AXI_awsize),
    .S00_AXI_awvalid(S00_AXI_awvalid),
    .S00_AXI_bready(S00_AXI_bready),
    .S00_AXI_bresp(S00_AXI_bresp),
    .S00_AXI_bvalid(S00_AXI_bvalid),
    .S00_AXI_rdata(S00_AXI_rdata),
    .S00_AXI_rlast(S00_AXI_rlast),
    .S00_AXI_rready(S00_AXI_rready),
    .S00_AXI_rresp(S00_AXI_rresp),
    .S00_AXI_rvalid(S00_AXI_rvalid),
    .S00_AXI_wdata(S00_AXI_wdata),
    .S00_AXI_wlast(S00_AXI_wlast),
    .S00_AXI_wready(S00_AXI_wready),
    .S00_AXI_wstrb(S00_AXI_wstrb),
    .S00_AXI_wvalid(S00_AXI_wvalid),
    .S01_AXI_araddr(S01_AXI_araddr),
    .S01_AXI_arburst(S01_AXI_arburst),
    .S01_AXI_arcache(S01_AXI_arcache),
    .S01_AXI_arlen(S01_AXI_arlen),
    .S01_AXI_arlock(S01_AXI_arlock),
    .S01_AXI_arprot(S01_AXI_arprot),
    .S01_AXI_arqos(S01_AXI_arqos),
    .S01_AXI_arready(S01_AXI_arready),
    .S01_AXI_arsize(S01_AXI_arsize),
    .S01_AXI_arvalid(S01_AXI_arvalid),
    .S01_AXI_awaddr(S01_AXI_awaddr),
    .S01_AXI_awburst(S01_AXI_awburst),
    .S01_AXI_awcache(S01_AXI_awcache),
    .S01_AXI_awlen(S01_AXI_awlen),
    .S01_AXI_awlock(S01_AXI_awlock),
    .S01_AXI_awprot(S01_AXI_awprot),
    .S01_AXI_awqos(S01_AXI_awqos),
    .S01_AXI_awready(S01_AXI_awready),
    .S01_AXI_awsize(S01_AXI_awsize),
    .S01_AXI_awvalid(S01_AXI_awvalid),
    .S01_AXI_bready(S01_AXI_bready),
    .S01_AXI_bresp(S01_AXI_bresp),
    .S01_AXI_bvalid(S01_AXI_bvalid),
    .S01_AXI_rdata(S01_AXI_rdata),
    .S01_AXI_rlast(S01_AXI_rlast),
    .S01_AXI_rready(S01_AXI_rready),
    .S01_AXI_rresp(S01_AXI_rresp),
    .S01_AXI_rvalid(S01_AXI_rvalid),
    .S01_AXI_wdata(S01_AXI_wdata),
    .S01_AXI_wlast(S01_AXI_wlast),
    .S01_AXI_wready(S01_AXI_wready),
    .S01_AXI_wstrb(S01_AXI_wstrb),
    .S01_AXI_wvalid(S01_AXI_wvalid),
    .ctsn(~UART_CTS),
    .ip2intc_irpt(ip2intc_irpt),
    .rtsn(~UART_RTS),
    .sin(UART_RXD_OUT),
    .sout(UART_TXD_IN)
    );

SimTop mycpu(
  .clock(clock),
  .reset(~resetn),
  .io_immio_aw_ready(S00_AXI_awready),
  .io_immio_aw_valid(S00_AXI_awvalid),
  .io_immio_aw_bits_id(),
  .io_immio_aw_bits_addr(S00_AXI_awaddr),
  .io_immio_aw_bits_len(S00_AXI_awlen),
  .io_immio_aw_bits_size(S00_AXI_awsize),
  .io_immio_aw_bits_burst(S00_AXI_awburst),
  .io_immio_aw_bits_lock(S00_AXI_awlock),
  .io_immio_aw_bits_cache(S00_AXI_awcache),
  .io_immio_aw_bits_prot(S00_AXI_awprot),
  .io_immio_aw_bits_qos(S00_AXI_awqos),
  .io_immio_aw_bits_user(),
  .io_immio_w_ready(S00_AXI_wready),
  .io_immio_w_valid(S00_AXI_wvalid),
  .io_immio_w_bits_data(S00_AXI_wdata),
  .io_immio_w_bits_strb(S00_AXI_wstrb),
  .io_immio_w_bits_last(S00_AXI_wlast),
  .io_immio_w_bits_user(io_mmio_wuser),
  .io_immio_b_ready(S00_AXI_bready),
  .io_immio_b_valid(S00_AXI_bvalid),
  .io_immio_b_bits_id(),
  .io_immio_b_bits_resp(S00_AXI_bresp),
  .io_immio_b_bits_user(),
  .io_immio_ar_ready(S00_AXI_arready),
  .io_immio_ar_valid(S00_AXI_arvalid),
  .io_immio_ar_bits_id(),
  .io_immio_ar_bits_addr(S00_AXI_araddr),
  .io_immio_ar_bits_len(S00_AXI_arlen),
  .io_immio_ar_bits_size(S00_AXI_arsize),
  .io_immio_ar_bits_burst(S00_AXI_arburst),
  .io_immio_ar_bits_lock(S00_AXI_arlock),
  .io_immio_ar_bits_cache(S00_AXI_arcache),
  .io_immio_ar_bits_prot(S00_AXI_arprot),
  .io_immio_ar_bits_qos(S00_AXI_arqos),
  .io_immio_ar_bits_user(),
  .io_immio_r_ready(S00_AXI_rready),
  .io_immio_r_valid(S00_AXI_rvalid),
  .io_immio_r_bits_id(),
  .io_immio_r_bits_data(S00_AXI_rdata),
  .io_immio_r_bits_resp(S00_AXI_rresp),
  .io_immio_r_bits_last(S00_AXI_rlast),
  .io_immio_r_bits_user(),
  .io_dmmio_aw_ready(S01_AXI_awready),
  .io_dmmio_aw_valid(S01_AXI_awvalid),
  .io_dmmio_aw_bits_id(),
  .io_dmmio_aw_bits_addr(S01_AXI_awaddr),
  .io_dmmio_aw_bits_len(S01_AXI_awlen),
  .io_dmmio_aw_bits_size(S01_AXI_awsize),
  .io_dmmio_aw_bits_burst(S01_AXI_awburst),
  .io_dmmio_aw_bits_lock(S01_AXI_awlock),
  .io_dmmio_aw_bits_cache(S01_AXI_awcache),
  .io_dmmio_aw_bits_prot(S01_AXI_awprot),
  .io_dmmio_aw_bits_qos(S01_AXI_awqos),
  .io_dmmio_aw_bits_user(),
  .io_dmmio_w_ready(S01_AXI_wready),
  .io_dmmio_w_valid(S01_AXI_wvalid),
  .io_dmmio_w_bits_data(S01_AXI_wdata),
  .io_dmmio_w_bits_strb(S01_AXI_wstrb),
  .io_dmmio_w_bits_last(S01_AXI_wlast),
  .io_dmmio_w_bits_user(io_mmio_wuser),
  .io_dmmio_b_ready(S01_AXI_bready),
  .io_dmmio_b_valid(S01_AXI_bvalid),
  .io_dmmio_b_bits_id(),
  .io_dmmio_b_bits_resp(S01_AXI_bresp),
  .io_dmmio_b_bits_user(),
  .io_dmmio_ar_ready(S01_AXI_arready),
  .io_dmmio_ar_valid(S01_AXI_arvalid),
  .io_dmmio_ar_bits_id(),
  .io_dmmio_ar_bits_addr(S01_AXI_araddr),
  .io_dmmio_ar_bits_len(S01_AXI_arlen),
  .io_dmmio_ar_bits_size(S01_AXI_arsize),
  .io_dmmio_ar_bits_burst(S01_AXI_arburst),
  .io_dmmio_ar_bits_lock(S01_AXI_arlock),
  .io_dmmio_ar_bits_cache(S01_AXI_arcache),
  .io_dmmio_ar_bits_prot(S01_AXI_arprot),
  .io_dmmio_ar_bits_qos(S01_AXI_arqos),
  .io_dmmio_ar_bits_user(),
  .io_dmmio_r_ready(S01_AXI_rready),
  .io_dmmio_r_valid(S01_AXI_rvalid),
  .io_dmmio_r_bits_id(),
  .io_dmmio_r_bits_data(S01_AXI_rdata),
  .io_dmmio_r_bits_resp(S01_AXI_rresp),
  .io_dmmio_r_bits_last(S01_AXI_rlast),
  .io_dmmio_r_bits_user(),
  .io_clint_aw_ready(M00_AXI_awready),
  .io_clint_aw_valid(M00_AXI_awvalid),
  .io_clint_aw_bits_id(),
  .io_clint_aw_bits_addr(M00_AXI_awaddr),
  .io_clint_aw_bits_len(M00_AXI_awlen),
  .io_clint_aw_bits_size(M00_AXI_awsize),
  .io_clint_aw_bits_burst(M00_AXI_awburst),
  .io_clint_aw_bits_lock(M00_AXI_awlock),
  .io_clint_aw_bits_cache(M00_AXI_awcache),
  .io_clint_aw_bits_prot(M00_AXI_awprot),
  .io_clint_aw_bits_qos(M00_AXI_awqos),
  .io_clint_aw_bits_user(),
  .io_clint_w_ready(M00_AXI_wready),
  .io_clint_w_valid(M00_AXI_wvalid),
  .io_clint_w_bits_data(M00_AXI_wdata),
  .io_clint_w_bits_strb(M00_AXI_wstrb),
  .io_clint_w_bits_last(M00_AXI_wlast),
  .io_clint_w_bits_user(),
  .io_clint_b_ready(M00_AXI_bready),
  .io_clint_b_valid(M00_AXI_bvalid),
  .io_clint_b_bits_id(),
  .io_clint_b_bits_resp(M00_AXI_bresp),
  .io_clint_b_bits_user(),
  .io_clint_ar_ready(M00_AXI_arready),
  .io_clint_ar_valid(M00_AXI_arvalid),
  .io_clint_ar_bits_id(),
  .io_clint_ar_bits_addr(M00_AXI_araddr),
  .io_clint_ar_bits_len(M00_AXI_arlen),
  .io_clint_ar_bits_size(M00_AXI_arsize),
  .io_clint_ar_bits_burst(M00_AXI_arburst),
  .io_clint_ar_bits_lock(M00_AXI_arlock),
  .io_clint_ar_bits_cache(M00_AXI_arcache),
  .io_clint_ar_bits_prot(M00_AXI_arprot),
  .io_clint_ar_bits_qos(M00_AXI_arqos),
  .io_clint_ar_bits_user(),
  .io_clint_r_ready(M00_AXI_rready),
  .io_clint_r_valid(M00_AXI_rvalid),
  .io_clint_r_bits_id(),
  .io_clint_r_bits_data(M00_AXI_rdata),
  .io_clint_r_bits_resp(M00_AXI_rresp),
  .io_clint_r_bits_last(M00_AXI_rlast),
  .io_clint_r_bits_user(),
  .io_plic_aw_ready(M01_AXI_awready),
  .io_plic_aw_valid(M01_AXI_awvalid),
  .io_plic_aw_bits_id(),
  .io_plic_aw_bits_addr(M01_AXI_awaddr),
  .io_plic_aw_bits_len(M01_AXI_awlen),
  .io_plic_aw_bits_size(M01_AXI_awsize),
  .io_plic_aw_bits_burst(M01_AXI_awburst),
  .io_plic_aw_bits_lock(M01_AXI_awlock),
  .io_plic_aw_bits_cache(M01_AXI_awcache),
  .io_plic_aw_bits_prot(M01_AXI_awprot),
  .io_plic_aw_bits_qos(M01_AXI_awqos),
  .io_plic_aw_bits_user(),
  .io_plic_w_ready(M01_AXI_wready),
  .io_plic_w_valid(M01_AXI_wvalid),
  .io_plic_w_bits_data(M01_AXI_wdata),
  .io_plic_w_bits_strb(M01_AXI_wstrb),
  .io_plic_w_bits_last(M01_AXI_wlast),
  .io_plic_w_bits_user(),
  .io_plic_b_ready(M01_AXI_bready),
  .io_plic_b_valid(M01_AXI_bvalid),
  .io_plic_b_bits_id(),
  .io_plic_b_bits_resp(M01_AXI_bresp),
  .io_plic_b_bits_user(),
  .io_plic_ar_ready(M01_AXI_arready),
  .io_plic_ar_valid(M01_AXI_arvalid),
  .io_plic_ar_bits_id(),
  .io_plic_ar_bits_addr(M01_AXI_araddr),
  .io_plic_ar_bits_len(M01_AXI_arlen),
  .io_plic_ar_bits_size(M01_AXI_arsize),
  .io_plic_ar_bits_burst(M01_AXI_arburst),
  .io_plic_ar_bits_lock(M01_AXI_arlock),
  .io_plic_ar_bits_cache(M01_AXI_arcache),
  .io_plic_ar_bits_prot(M01_AXI_arprot),
  .io_plic_ar_bits_qos(M01_AXI_arqos),
  .io_plic_ar_bits_user(),
  .io_plic_r_ready(M01_AXI_rready),
  .io_plic_r_valid(M01_AXI_rvalid),
  .io_plic_r_bits_id(),
  .io_plic_r_bits_data(M01_AXI_rdata),
  .io_plic_r_bits_resp(M01_AXI_rresp),
  .io_plic_r_bits_last(M01_AXI_rlast),
  .io_plic_r_bits_user(),
  .io_uart_irq(ip2intc_irpt)
);


endmodule