`define XLEN 64
import "DPI-C" function void SimMemAccess (
    input  longint  iaddr,
    output longint  idata,
    input  longint  imask,
    input  longint  daddr,
    output longint  drdata,
    input  longint  dwdata,
    input  longint  dmask,
    input  bit      dwen
);

module SimMem (
  input  clk,
  input  [`XLEN-1:0] iaddr,
  output [`XLEN-1:0] idata,
  input  [`XLEN-1:0] imask,
  input  [`XLEN-1:0] daddr,
  output [`XLEN-1:0] drdata,
  input  [`XLEN-1:0] dwdata,
  input  [`XLEN-1:0] dmask,
  input  dwen
);

  always @(posedge clk) begin
    SimMemAccess(iaddr, idata, imask, daddr, drdata, dwdata, dmask, dwen);
  end

endmodule