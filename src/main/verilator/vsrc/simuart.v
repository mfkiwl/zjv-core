`define XLEN 64
import "DPI-C" function void uart_putc (
    input  byte  waddr,
    input  byte  wdata
);

import "DPI-C" function void uart_getc (
    input  byte  raddr,
    // output longint  rdata     
    output byte  rdata
);

module SimUART (
  input  clk,
  input  wen,
  input  [7:0] waddr,  
  input  [7:0] wdata,
  input  ren,
  input  [7:0] raddr,  
  // output [`XLEN-1:0] rdata
  output [7:0] rdata
);

  always @(posedge clk) begin
    if (wen) uart_putc(waddr, wdata);
    if (ren) uart_getc(raddr, rdata);
  end

endmodule