module Mem (
    input clock,
    input clock_200,
    input reset,
    // AXI4
    output        m0_axi4_aw_ready, 
    input         m0_axi4_aw_valid, 
    input         m0_axi4_aw_id, 
    input  [31:0] m0_axi4_aw_addr, 
    input  [7:0]  m0_axi4_aw_len, 
    input  [2:0]  m0_axi4_aw_size, 
    input  [1:0]  m0_axi4_aw_burst,
    output        m0_axi4_w_ready, 
    input         m0_axi4_w_valid, 
    input  [63:0] m0_axi4_w_data, 
    input  [7:0]  m0_axi4_w_strb, 
    input         m0_axi4_w_last, 
    input         m0_axi4_b_ready, 
    output        m0_axi4_b_valid, 
    output        m0_axi4_b_id, 
    output [1:0]  m0_axi4_b_resp, 
    output        m0_axi4_ar_ready, 
    input         m0_axi4_ar_valid, 
    input         m0_axi4_ar_id, 
    input  [31:0] m0_axi4_ar_addr, 
    input  [7:0]  m0_axi4_ar_len, 
    input  [2:0]  m0_axi4_ar_size, 
    input  [1:0]  m0_axi4_ar_burst,
    input         m0_axi4_r_ready, 
    output        m0_axi4_r_valid, 
    output        m0_axi4_r_id, 
    output [63:0] m0_axi4_r_data, 
    output [1:0]  m0_axi4_r_resp, 
    output        m0_axi4_r_last,
    // DDR2
    inout  [15:0] ddr_dq,
    inout  [1:0]  ddr_dqs_n,
    inout  [1:0]  ddr_dqs_p,
    output [12:0] ddr_addr,
    output [2:0]  ddr_ba,
    output        ddr_ras_n,
    output        ddr_cas_n,
    output        ddr_we_n,
    output        ddr_ck_n,
    output        ddr_ck_p,
    output        ddr_cke,
    output        ddr_cs_n,
    output [1:0]  ddr_dm,
    output        ddr_odt
);

    wire [31:0]   ddr2_axi4_aw_addr;
    wire [7:0]    ddr2_axi4_aw_len;
    wire [2:0]    ddr2_axi4_aw_size;
    wire [1:0]    ddr2_axi4_aw_burst;
    wire          ddr2_axi4_aw_valid;
    wire          ddr2_axi4_aw_ready;
    wire [63:0]   ddr2_axi4_w_data;
    wire [7:0]    ddr2_axi4_w_strb;
    wire          ddr2_axi4_w_last;
    wire          ddr2_axi4_w_valid;
    wire          ddr2_axi4_w_ready;
    wire          ddr2_axi4_b_ready;
    wire [1:0]    ddr2_axi4_b_resp;
    wire          ddr2_axi4_b_valid;
    wire [31:0]   ddr2_axi4_ar_addr;
    wire [7:0]    ddr2_axi4_ar_len;
    wire [2:0]    ddr2_axi4_ar_size;
    wire [1:0]    ddr2_axi4_ar_burst;
    wire          ddr2_axi4_ar_valid;
    wire          ddr2_axi4_ar_ready;
    wire          ddr2_axi4_r_ready;
    wire [63:0]   ddr2_axi4_r_data;
    wire [1:0]    ddr2_axi4_r_resp;
    wire          ddr2_axi4_r_last;
    wire          ddr2_axi4_r_valid;

    wire ddr2_clock;
    wire ddr2_reset;

    assign m0_axi4_b_id = 1'b0;
    assign m0_axi4_r_id = 1'b0;

    axi_clock_converter_0 clock_conv (
        .s_axi_aclk(clock),
        .s_axi_aresetn(~ reset),
        .m_axi_aclk(ddr2_clock),
        .m_axi_aresetn(~ ddr2_reset),
        // Slave
        .s_axi_awaddr(m0_axi4_aw_addr[31:0] & 32'h07ffffff),
        .s_axi_awlen(m0_axi4_aw_len),
        .s_axi_awsize(m0_axi4_aw_size),
        .s_axi_awburst(m0_axi4_aw_burst),
        .s_axi_awlock(1'b0),
        .s_axi_awprot(3'h0),
        .s_axi_awcache(4'h0),
        .s_axi_awregion(4'h0),
        .s_axi_awqos(4'h0),
        .s_axi_awvalid(m0_axi4_aw_valid),
        .s_axi_awready(m0_axi4_aw_ready),
        .s_axi_wdata(m0_axi4_w_data),
        .s_axi_wstrb(m0_axi4_w_strb),
        .s_axi_wlast(m0_axi4_w_last),
        .s_axi_wready(m0_axi4_w_ready),
        .s_axi_wvalid(m0_axi4_w_valid),
        .s_axi_bresp(m0_axi4_b_resp),
        .s_axi_bready(m0_axi4_b_ready),
        .s_axi_bvalid(m0_axi4_b_valid),
        .s_axi_araddr(m0_axi4_ar_addr[31:0] & 32'h07ffffff),
        .s_axi_arlen(m0_axi4_ar_len),
        .s_axi_arsize(m0_axi4_ar_size),
        .s_axi_arburst(m0_axi4_ar_burst),
        .s_axi_arlock(1'b0),
        .s_axi_arprot(3'h0),
        .s_axi_arcache(4'h0),
        .s_axi_arregion(4'h0),
        .s_axi_arqos(4'h0),
        .s_axi_arvalid(m0_axi4_ar_valid),
        .s_axi_arready(m0_axi4_ar_ready),
        .s_axi_rdata(m0_axi4_r_data),
        .s_axi_rresp(m0_axi4_r_resp),
        .s_axi_rlast(m0_axi4_r_last),
        .s_axi_rready(m0_axi4_r_ready),
        .s_axi_rvalid(m0_axi4_r_valid),
        // Master
        .m_axi_awaddr(ddr2_axi4_aw_addr),
        .m_axi_awlen(ddr2_axi4_aw_len),
        .m_axi_awsize(ddr2_axi4_aw_size),
        .m_axi_awburst(ddr2_axi4_aw_burst),
        .m_axi_awlock(),
        .m_axi_awprot(),
        .m_axi_awcache(),
        .m_axi_awregion(),
        .m_axi_awqos(),
        .m_axi_awvalid(ddr2_axi4_aw_valid),
        .m_axi_awready(ddr2_axi4_aw_ready),
        .m_axi_wdata(ddr2_axi4_w_data),
        .m_axi_wstrb(ddr2_axi4_w_strb),
        .m_axi_wlast(ddr2_axi4_w_last),
        .m_axi_wready(ddr2_axi4_w_ready),
        .m_axi_wvalid(ddr2_axi4_w_valid),
        .m_axi_bresp(ddr2_axi4_b_resp),
        .m_axi_bready(ddr2_axi4_b_ready),
        .m_axi_bvalid(ddr2_axi4_b_valid),
        .m_axi_araddr(ddr2_axi4_ar_addr),
        .m_axi_arlen(ddr2_axi4_ar_len),
        .m_axi_arsize(ddr2_axi4_ar_size),
        .m_axi_arburst(ddr2_axi4_ar_burst),
        .m_axi_arlock(),
        .m_axi_arprot(),
        .m_axi_arcache(),
        .m_axi_arregion(),
        .m_axi_arqos(),
        .m_axi_arvalid(ddr2_axi4_ar_valid),
        .m_axi_arready(ddr2_axi4_ar_ready),
        .m_axi_rdata(ddr2_axi4_r_data),
        .m_axi_rresp(ddr2_axi4_r_resp),
        .m_axi_rlast(ddr2_axi4_r_last),
        .m_axi_rready(ddr2_axi4_r_ready),
        .m_axi_rvalid(ddr2_axi4_r_valid)
    );

    mig_7series_0 ddr2 (
        // DDR2
        .ddr2_dq(ddr_dq),
        .ddr2_dqs_n(ddr_dqs_n),
        .ddr2_dqs_p(ddr_dqs_p),
        .ddr2_addr(ddr_addr),
        .ddr2_ba(ddr_ba),
        .ddr2_ras_n(ddr_ras_n),
        .ddr2_cas_n(ddr_cas_n),
        .ddr2_we_n(ddr_we_n),
        .ddr2_ck_p(ddr_ck_p),
        .ddr2_ck_n(ddr_ck_n),
        .ddr2_cke(ddr_cke),
        .ddr2_cs_n(ddr_cs_n),
        .ddr2_dm(ddr_dm),
        .ddr2_odt(ddr_odt),
        // Clock
        .sys_clk_i(clock_200),
        .sys_rst(reset),
        .ui_clk(ddr2_clock),
        .ui_clk_sync_rst(ddr2_reset),
        // XADC
        .device_temp_i(32'b0),    
        .app_sr_req(1'b0),
        .app_ref_req(1'b0), 
        .app_zq_req(1'b0),
        // AXI
        .aresetn(~ reset),                                                 
        .s_axi_awid(1'b0),
        .s_axi_awaddr(ddr2_axi4_aw_addr),
        .s_axi_awlen(ddr2_axi4_aw_len),
        .s_axi_awsize(ddr2_axi4_aw_size),
        .s_axi_awburst(ddr2_axi4_aw_burst),
        .s_axi_awlock(1'b0),
        .s_axi_awcache(4'b0),
        .s_axi_awprot(3'b0),
        .s_axi_awqos(4'b0),
        .s_axi_awvalid(ddr2_axi4_aw_valid),
        .s_axi_awready(ddr2_axi4_aw_ready),
        .s_axi_wdata(ddr2_axi4_w_data),
        .s_axi_wstrb(ddr2_axi4_w_strb),
        .s_axi_wlast(ddr2_axi4_w_last),
        .s_axi_wvalid(ddr2_axi4_w_valid),
        .s_axi_wready(ddr2_axi4_w_ready),
        .s_axi_bid(),
        .s_axi_bresp(ddr2_axi4_b_resp),
        .s_axi_bvalid(ddr2_axi4_b_valid),
        .s_axi_bready(ddr2_axi4_b_ready),
        .s_axi_arid(1'b0),
        .s_axi_araddr(ddr2_axi4_ar_addr),
        .s_axi_arlen(ddr2_axi4_ar_len),
        .s_axi_arsize(ddr2_axi4_ar_size),
        .s_axi_arburst(ddr2_axi4_ar_burst),
        .s_axi_arlock(1'b0),
        .s_axi_arcache(4'b0),
        .s_axi_arprot(3'b0),
        .s_axi_arqos(4'b0),
        .s_axi_arvalid(ddr2_axi4_ar_valid),
        .s_axi_arready(ddr2_axi4_ar_ready),
        .s_axi_rid(),
        .s_axi_rdata(ddr2_axi4_r_data),
        .s_axi_rresp(ddr2_axi4_r_resp),
        .s_axi_rlast(ddr2_axi4_r_last),
        .s_axi_rvalid(ddr2_axi4_r_valid),
        .s_axi_rready(ddr2_axi4_r_ready),
        // Useless
        .init_calib_complete()             
    );  
  
endmodule
