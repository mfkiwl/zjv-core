module UART (
	input clock,
	input reset,
	// AXI4
    output        uart_axi4_aw_ready, 
    input         uart_axi4_aw_valid,
    input  [31:0] uart_axi4_aw_addr, 
    input  [7:0]  uart_axi4_aw_len, 
    input  [2:0]  uart_axi4_aw_size, 
    input  [1:0]  uart_axi4_aw_burst, 
    output        uart_axi4_w_ready, 
    input         uart_axi4_w_valid, 
    input  [63:0] uart_axi4_w_data, 
    input  [7:0]  uart_axi4_w_strb, 
    input         uart_axi4_w_last, 
    input         uart_axi4_b_ready, 
    output        uart_axi4_b_valid, 
    output [1:0]  uart_axi4_b_resp, 
    output        uart_axi4_ar_ready, 
    input         uart_axi4_ar_valid,
    input  [31:0] uart_axi4_ar_addr, 
    input  [7:0]  uart_axi4_ar_len, 
    input  [2:0]  uart_axi4_ar_size, 
    input  [1:0]  uart_axi4_ar_burst, 
    input         uart_axi4_r_ready, 
    output        uart_axi4_r_valid,
    output [63:0] uart_axi4_r_data, 
    output [1:0]  uart_axi4_r_resp, 
    output        uart_axi4_r_last,
    // UART
    output meip,
    input  uart_RX,
    output uart_TX
);

    wire [12:0]  lite_ar_addr;        
    wire         lite_ar_valid;       
    wire         lite_ar_ready;
    wire [31:0]  lite_r_data;         
    wire [1:0]   lite_r_resp;         
    wire         lite_r_valid;        
    wire         lite_r_ready;
    wire [12:0]  lite_aw_addr;       
    wire         lite_aw_valid;       
    wire         lite_aw_ready;    
    wire [31:0]  lite_w_data;         
    wire [3:0]   lite_w_strb;         
    wire         lite_w_valid;        
    wire         lite_w_ready;
    wire [1:0]   lite_b_resp;         
    wire         lite_b_valid;        
    wire         lite_b_ready;  

    wire [31:0]  uart_axi4_r_data_32;
    wire [31:0]  uart_axi4_w_data_32;
    wire [3:0]   uart_axi4_w_strb_4;
    assign uart_axi4_w_data_32 = uart_axi4_w_strb[7:4] ? uart_axi4_w_data[63:32] : uart_axi4_w_data[31:0];
    assign uart_axi4_w_strb_4 = uart_axi4_w_strb[7:4] ? uart_axi4_w_strb[7:4] : uart_axi4_w_strb[3:0];
    assign uart_axi4_r_data = {uart_axi4_r_data_32, uart_axi4_r_data_32};

    nasti_lite_bridge #(    
        .MAX_TRANSACTION(1),        // maximal number of parallel write transactions
        .ID_WIDTH(1),               // id width
        .ADDR_WIDTH(13),            // address width
        .NASTI_DATA_WIDTH(32),      // width of data on the nasti side
        .LITE_DATA_WIDTH(32),       // width of data on the nasti-lite side
        .USER_WIDTH(1)    
    ) axi4_to_axilite
    (
        .clk(clock),
        .rstn(~ reset),
        // Slave
        .lite_slave_ar_id(),
        .lite_slave_ar_addr(lite_ar_addr),
        .lite_slave_ar_valid(lite_ar_valid),
        .lite_slave_ar_ready(lite_ar_ready),
        .lite_slave_r_id (1'b0),
        .lite_slave_r_data(lite_r_data),
        .lite_slave_r_resp(lite_r_resp),
        .lite_slave_r_valid(lite_r_valid),
        .lite_slave_r_ready(lite_r_ready),
        .lite_slave_aw_id(),
        .lite_slave_aw_addr(lite_aw_addr),
        .lite_slave_aw_valid(lite_aw_valid),
        .lite_slave_aw_ready(lite_aw_ready),
        .lite_slave_w_data(lite_w_data),
        .lite_slave_w_strb(lite_w_strb),
        .lite_slave_w_valid(lite_w_valid),
        .lite_slave_w_ready(lite_w_ready),
        .lite_slave_b_id(1'b0),
        .lite_slave_b_resp(lite_b_resp),
        .lite_slave_b_valid(lite_b_valid),
        .lite_slave_b_ready(lite_b_ready),
        // Master
        .nasti_master_ar_id(1'b0),   
        .nasti_master_ar_addr(uart_axi4_ar_addr[12:0]),   
        .nasti_master_ar_len(uart_axi4_ar_len),   
        .nasti_master_ar_size(3'h2),   
        .nasti_master_ar_burst(uart_axi4_ar_burst),  
        .nasti_master_ar_valid(uart_axi4_ar_valid),   
        .nasti_master_ar_ready(uart_axi4_ar_ready),   
        .nasti_master_r_id(),   
        .nasti_master_r_data(uart_axi4_r_data_32),   
        .nasti_master_r_resp(uart_axi4_r_resp),   
        .nasti_master_r_last(uart_axi4_r_last),   
        .nasti_master_r_valid(uart_axi4_r_valid),   
        .nasti_master_r_ready(uart_axi4_r_ready),
        .nasti_master_aw_id(1'b0),
        .nasti_master_aw_addr(uart_axi4_aw_addr[12:0]),
        .nasti_master_aw_len(uart_axi4_aw_len),
        .nasti_master_aw_size(3'h2),
        .nasti_master_aw_burst(uart_axi4_aw_burst),
        .nasti_master_aw_valid(uart_axi4_aw_valid),
        .nasti_master_aw_ready(uart_axi4_aw_ready),
        .nasti_master_w_data(uart_axi4_w_data_32), 
        .nasti_master_w_strb(uart_axi4_w_strb_4),
        .nasti_master_w_last(uart_axi4_w_last),
        .nasti_master_w_valid(uart_axi4_w_valid),
        .nasti_master_w_ready(uart_axi4_w_ready),
        .nasti_master_b_id(),
        .nasti_master_b_resp(uart_axi4_b_resp),
        .nasti_master_b_valid(uart_axi4_b_valid),
        .nasti_master_b_ready(uart_axi4_b_ready)
);    

    // UART Least Significant Signals
    // UART M00 0x04010000, Length 13 bits because of Vivado's IP Design
    axi_uart16550_0 uart (
        .s_axi_aclk(clock),
        .s_axi_aresetn(~ reset),
        // AXI Lite
        .s_axi_awaddr(lite_aw_addr),
        .s_axi_awvalid(lite_aw_valid),
        .s_axi_awready(lite_aw_ready),
        .s_axi_wdata(lite_w_data),
        .s_axi_wstrb(lite_w_strb),
        .s_axi_wvalid(lite_w_valid),
        .s_axi_wready(lite_w_ready),
        .s_axi_bresp(lite_b_resp),
        .s_axi_bvalid(lite_b_valid),
        .s_axi_bready(lite_b_ready),
        .s_axi_araddr(lite_ar_addr),
        .s_axi_arvalid(lite_ar_valid),
        .s_axi_arready(lite_ar_ready),
        .s_axi_rdata(lite_r_data),
        .s_axi_rresp(lite_r_resp),
        .s_axi_rvalid(lite_r_valid),
        .s_axi_rready(lite_r_ready),
        // UART
        .baudoutn(),
        .sin(uart_RX),
        .sout(uart_TX),
        .ip2intc_irpt(meip),
        .freeze(1'b0),
        .rin(1'b1),
        .ctsn(),
        .dcdn(1'b1),
        .ddis(),
        .dsrn(1'b1),
        .dtrn(),
        .out1n(),
        .out2n(),
        .rtsn(),
        .rxrdyn(),
        .txrdyn()
    );

endmodule
