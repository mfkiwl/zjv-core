module MemMappedIO (
    input  clock,
    input  reset,
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
    // UART
    input  uart_RX,
    output uart_TX,
    output meip
    // BRAM's result will be in rdata or wdata
    // TODO SPI SD Card
);

    assign m0_axi4_r_id = 1'b0;
    assign m0_axi4_b_id = 1'b0;

    // AW Channel
    wire [95:0]  s_axi4_aw_addr;
    wire [23:0]  s_axi4_aw_len;
    wire [8:0]   s_axi4_aw_size;
    wire [5:0]   s_axi4_aw_burst;
    wire [2:0]   s_axi4_aw_ready;
    wire [2:0]   s_axi4_aw_valid;
    // W Channel
    wire [191:0] s_axi4_w_data;
    wire [23:0]  s_axi4_w_strb;
    wire [2:0]   s_axi4_w_last;
    wire [2:0]   s_axi4_w_valid;
    wire [2:0]   s_axi4_w_ready;
    // B Channel
    wire [5:0]   s_axi4_b_resp;
    wire [2:0]   s_axi4_b_ready;
    wire [2:0]   s_axi4_b_valid;
    // AR Channel
    wire [95:0]  s_axi4_ar_addr;
    wire [23:0]  s_axi4_ar_len;
    wire [8:0]   s_axi4_ar_size;
    wire [5:0]   s_axi4_ar_burst;
    wire [2:0]   s_axi4_ar_ready;
    wire [2:0]   s_axi4_ar_valid;
    // R Channel
    wire [191:0] s_axi4_r_data;
    wire [5:0]   s_axi4_r_resp;
    wire [2:0]   s_axi4_r_last;
    wire [2:0]   s_axi4_r_ready;
    wire [2:0]   s_axi4_r_valid;

    UART uart_wrapper (
        .clock(clock),
        .reset(reset),
        // AXI
        .uart_axi4_aw_ready(s_axi4_aw_ready[0]),
        .uart_axi4_aw_valid(s_axi4_aw_valid[0]),
        .uart_axi4_aw_addr(s_axi4_aw_addr[31:0]),
        .uart_axi4_aw_len(s_axi4_aw_len[7:0]),
        .uart_axi4_aw_size(s_axi4_aw_size[2:0]),
        .uart_axi4_aw_burst(s_axi4_aw_burst[1:0]),
        .uart_axi4_w_ready(s_axi4_w_ready[0]),
        .uart_axi4_w_valid(s_axi4_w_valid[0]),
        .uart_axi4_w_data(s_axi4_w_data[63:0]),
        .uart_axi4_w_strb(s_axi4_w_strb[7:0]),
        .uart_axi4_w_last(s_axi4_w_last[0]),
        .uart_axi4_b_ready(s_axi4_b_ready[0]),
        .uart_axi4_b_valid(s_axi4_b_valid[0]),
        .uart_axi4_b_resp(s_axi4_b_resp[1:0]),
        .uart_axi4_ar_ready(s_axi4_ar_ready[0]),
        .uart_axi4_ar_valid(s_axi4_ar_valid[0]),
        .uart_axi4_ar_addr(s_axi4_ar_addr[31:0]),
        .uart_axi4_ar_len(s_axi4_ar_len[7:0]),
        .uart_axi4_ar_size(s_axi4_ar_size[2:0]),
        .uart_axi4_ar_burst(s_axi4_ar_burst[1:0]),
        .uart_axi4_r_ready(s_axi4_r_ready[0]),
        .uart_axi4_r_valid(s_axi4_r_valid[0]),
        .uart_axi4_r_data(s_axi4_r_data[63:0]),
        .uart_axi4_r_resp(s_axi4_r_resp[1:0]),
        .uart_axi4_r_last(s_axi4_r_last[0]),
        // UART
        .uart_TX(uart_TX),
        .uart_RX(uart_RX),
        .meip(meip)
    );

    BlockRAM bram_wrapper (
        .clock(clock),
        .reset(reset),
        // AXI4
       .bram_axi4_aw_ready(s_axi4_aw_ready[1]),
       .bram_axi4_aw_valid(s_axi4_aw_valid[1]),
       .bram_axi4_aw_addr(s_axi4_aw_addr[63:32]),
       .bram_axi4_aw_len(s_axi4_aw_len[15:8]),
       .bram_axi4_aw_size(s_axi4_aw_size[5:3]),
       .bram_axi4_aw_burst(s_axi4_aw_burst[3:2]),
       .bram_axi4_w_ready(s_axi4_w_ready[1]),
       .bram_axi4_w_valid(s_axi4_w_valid[1]),
       .bram_axi4_w_data(s_axi4_w_data[127:64]),
       .bram_axi4_w_strb(s_axi4_w_strb[15:8]),
       .bram_axi4_w_last(s_axi4_w_last[1]),
       .bram_axi4_b_ready(s_axi4_b_ready[1]),
       .bram_axi4_b_valid(s_axi4_b_valid[1]),
       .bram_axi4_b_resp(s_axi4_b_resp[3:2]),
       .bram_axi4_ar_ready(s_axi4_ar_ready[1]),
       .bram_axi4_ar_valid(s_axi4_ar_valid[1]),
       .bram_axi4_ar_addr(s_axi4_ar_addr[63:32]),
       .bram_axi4_ar_len(s_axi4_ar_len[15:8]),
       .bram_axi4_ar_size(s_axi4_ar_size[5:3]),
       .bram_axi4_ar_burst(s_axi4_ar_burst[3:2]),
       .bram_axi4_r_ready(s_axi4_r_ready[1]),
       .bram_axi4_r_valid(s_axi4_r_valid[1]),
       .bram_axi4_r_data(s_axi4_r_data[127:64]),
       .bram_axi4_r_resp(s_axi4_r_resp[3:2]),
       .bram_axi4_r_last(s_axi4_r_last[1])
    );

    axi_crossbar_0 mmio_xbar (
        .aclk(clock),
        .aresetn(~ reset),
        // Slave * 1
        .s_axi_awaddr(m0_axi4_aw_addr),
        .s_axi_awlen(m0_axi4_aw_len),
        .s_axi_awsize(m0_axi4_aw_size),
        .s_axi_awburst(m0_axi4_aw_burst),
        .s_axi_awlock(1'b0),
        .s_axi_awcache(4'b0),
        .s_axi_awprot(3'b0),
        .s_axi_awqos(4'b0),
        .s_axi_awvalid(m0_axi4_aw_valid),
        .s_axi_awready(m0_axi4_aw_ready),
        .s_axi_wdata(m0_axi4_w_data),
        .s_axi_wstrb(m0_axi4_w_strb),
        .s_axi_wlast(m0_axi4_w_last),
        .s_axi_wvalid(m0_axi4_w_valid),
        .s_axi_wready(m0_axi4_w_ready),
        .s_axi_bresp(m0_axi4_b_resp),
        .s_axi_bvalid(m0_axi4_b_valid),
        .s_axi_bready(m0_axi4_b_ready),
        .s_axi_araddr(m0_axi4_ar_addr),
        .s_axi_arlen(m0_axi4_ar_len),
        .s_axi_arsize(m0_axi4_ar_size),
        .s_axi_arburst(m0_axi4_ar_burst),
        .s_axi_arlock(1'b0),
        .s_axi_arcache(4'b0),
        .s_axi_arprot(3'b0),
        .s_axi_arqos(4'b0),
        .s_axi_arvalid(m0_axi4_ar_valid),
        .s_axi_arready(m0_axi4_ar_ready),
        .s_axi_rdata(m0_axi4_r_data),
        .s_axi_rresp(m0_axi4_r_resp),
        .s_axi_rlast(m0_axi4_r_last),
        .s_axi_rvalid(m0_axi4_r_valid),
        .s_axi_rready(m0_axi4_r_ready),
        // Master * 3
        .m_axi_awaddr(s_axi4_aw_addr),
        .m_axi_awlen(s_axi4_aw_len),
        .m_axi_awsize(s_axi4_aw_size),
        .m_axi_awburst(s_axi4_aw_burst),
        .m_axi_awlock(),
        .m_axi_awcache(),
        .m_axi_awprot(),
        .m_axi_awregion(),
        .m_axi_awqos(),
        .m_axi_awvalid(s_axi4_aw_valid),
        .m_axi_awready(s_axi4_aw_ready),
        .m_axi_wdata(s_axi4_w_data),
        .m_axi_wstrb(s_axi4_w_strb),
        .m_axi_wlast(s_axi4_w_last),
        .m_axi_wvalid(s_axi4_w_valid),
        .m_axi_wready(s_axi4_w_ready),
        .m_axi_bresp(s_axi4_b_resp),
        .m_axi_bvalid(s_axi4_b_valid),
        .m_axi_bready(s_axi4_b_ready),
        .m_axi_araddr(s_axi4_ar_addr),
        .m_axi_arlen(s_axi4_ar_len),
        .m_axi_arsize(s_axi4_ar_size),
        .m_axi_arburst(s_axi4_ar_burst),
        .m_axi_arlock(),
        .m_axi_arcache(),
        .m_axi_arprot(),
        .m_axi_arregion(),
        .m_axi_arqos(),
        .m_axi_arvalid(s_axi4_ar_valid),
        .m_axi_arready(s_axi4_ar_ready),
        .m_axi_rdata(s_axi4_r_data),
        .m_axi_rresp(s_axi4_r_resp),
        .m_axi_rlast(s_axi4_r_last),
        .m_axi_rready(s_axi4_r_ready),
        .m_axi_rvalid(s_axi4_r_valid)
    );

endmodule
