module BlockRAM (
    input clock,
    input reset,
    // AXI
    output        bram_axi4_aw_ready, 
    input         bram_axi4_aw_valid, 
    input  [31:0] bram_axi4_aw_addr, 
    input  [7:0]  bram_axi4_aw_len, 
    input  [2:0]  bram_axi4_aw_size, 
    input  [1:0]  bram_axi4_aw_burst, 
    output        bram_axi4_w_ready, 
    input         bram_axi4_w_valid, 
    input  [63:0] bram_axi4_w_data, 
    input  [7:0]  bram_axi4_w_strb, 
    input         bram_axi4_w_last, 
    input         bram_axi4_b_ready, 
    output        bram_axi4_b_valid, 
    output [1:0]  bram_axi4_b_resp, 
    output        bram_axi4_ar_ready, 
    input         bram_axi4_ar_valid, 
    input  [31:0] bram_axi4_ar_addr, 
    input  [7:0]  bram_axi4_ar_len, 
    input  [2:0]  bram_axi4_ar_size, 
    input  [1:0]  bram_axi4_ar_burst, 
    input         bram_axi4_r_ready, 
    output        bram_axi4_r_valid, 
    output [63:0] bram_axi4_r_data, 
    output [1:0]  bram_axi4_r_resp, 
    output        bram_axi4_r_last
);

    wire bram_en;
    wire bram_clock;
    wire [15:0] bram_addr;
    wire [63:0] bram_rdata;
    wire [63:0] bram_wdata;
    wire [7:0]  bram_we_perbyte;

    BRAM_Storage bram_instance (
        .en(bram_en),
        .clock(bram_clock),
        .addr(bram_addr[15:3]),
        .rdata(bram_rdata),
        .wdata(bram_wdata),
        .we_perbyte(bram_we_perbyte)
    );

    axi_bram_ctrl_0 ctrl(
        .s_axi_aclk(clock),
        .s_axi_aresetn(~ reset),
        // AXI
        .s_axi_awready(bram_axi4_aw_ready),
        .s_axi_awvalid(bram_axi4_aw_valid),
        .s_axi_awaddr(bram_axi4_aw_addr[15:0]),
        .s_axi_awlen(bram_axi4_aw_len),
        .s_axi_awsize(bram_axi4_aw_size),
        .s_axi_awburst(bram_axi4_aw_burst),
        .s_axi_awlock(1'b0),
        .s_axi_awcache(4'b0),
        .s_axi_awprot(3'b0),
        .s_axi_wready(bram_axi4_w_ready),
        .s_axi_wvalid(bram_axi4_w_valid),
        .s_axi_wdata(bram_axi4_w_data),
        .s_axi_wstrb(bram_axi4_w_strb),
        .s_axi_wlast(bram_axi4_w_last),
        .s_axi_bready(bram_axi4_b_ready),
        .s_axi_bvalid(bram_axi4_b_valid),
        .s_axi_bresp(bram_axi4_b_resp),
        .s_axi_arready(bram_axi4_ar_ready),
        .s_axi_arvalid(bram_axi4_ar_valid),
        .s_axi_araddr(bram_axi4_ar_addr[15:0]),
        .s_axi_arlen(bram_axi4_ar_len),
        .s_axi_arsize(bram_axi4_ar_size),
        .s_axi_arburst(bram_axi4_ar_burst),
        .s_axi_arlock(1'b0),
        .s_axi_arcache(4'b0),
        .s_axi_arprot(3'b0),
        .s_axi_rready(bram_axi4_r_ready),
        .s_axi_rvalid(bram_axi4_r_valid),
        .s_axi_rdata(bram_axi4_r_data),
        .s_axi_rresp(bram_axi4_r_resp),
        .s_axi_rlast(bram_axi4_r_last),
        // BRAM
        .bram_addr_a(bram_addr),
        .bram_clk_a(bram_clock),
        .bram_rddata_a(bram_rdata),
        .bram_wrdata_a(bram_wdata),
        .bram_we_a(bram_we_perbyte),
        .bram_en_a(bram_en)
    );
    
endmodule

module BRAM_Storage(
    input en,
    input clock,
    input [12:0] addr,
    input [63:0] wdata,
    input [7:0] we_perbyte,
    output [63:0] rdata
    );

    reg [63:0] bram [8191:0];
    reg [63:0] outreg;
    wire[63:0] wmask;
    
    parameter BRAM_INIT_FILE = "bootloader.hex";
    initial begin
        if (BRAM_INIT_FILE != "")
            $readmemh(BRAM_INIT_FILE, bram);
    end
    
    // generate wmask according to we_perbyte
    assign wmask[7:0] = we_perbyte[0] ? 8'hff:8'h00;
    assign wmask[15:8] = we_perbyte[1] ? 8'hff:8'h00;
    assign wmask[23:16] = we_perbyte[2] ? 8'hff:8'h00; 
    assign wmask[31:24] = we_perbyte[3] ? 8'hff:8'h00;
    assign wmask[39:32] = we_perbyte[4] ? 8'hff:8'h00;
    assign wmask[47:40] = we_perbyte[5] ? 8'hff:8'h00;
    assign wmask[55:48] = we_perbyte[6] ? 8'hff:8'h00;
    assign wmask[63:56] = we_perbyte[7] ? 8'hff:8'h00;    
 
    always @(posedge clock)
         if (en)
                outreg <= (!we_perbyte) ? bram[addr]: 0;
            else
                outreg <= 0;

     always @(posedge clock)
         if (en)
             bram[addr] <= (~wmask & bram[addr]) | (wmask & wdata);
         else 
             bram[addr] <= bram[addr];

    assign rdata = outreg;
endmodule
