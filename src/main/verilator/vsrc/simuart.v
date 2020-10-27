`define XLEN 64
import "DPI-C" function void uart_putc (
    input  byte  waddr,
    input  byte  wdata
);

import "DPI-C" function void uart_getc (
    input  byte  raddr,
    output byte  rdata
);

import "DPI-C" function void uart_irq (
    output byte  irq
);

module SimUART (
  input  clk,
  input  wen,
  input  [7:0] waddr,  
  input  [7:0] wdata,
  input  ren,
  input  [7:0] raddr,
  output [7:0] rdata,
  output irq
);

  wire [7:0] irq_byte;
  assign irq = irq_byte[0];

  always @(posedge clk) begin
    if (wen) uart_putc(waddr, wdata);
    if (ren) uart_getc(raddr, rdata);
    uart_irq(irq_byte);
  end



endmodule