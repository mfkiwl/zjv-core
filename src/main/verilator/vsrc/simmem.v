`define XLEN 64
import "DPI-C" function void SimMemAccess (
    input  longint  iaddr,
    output longint  idata,
    input  longint  itype,
    input  longint  daddr,
    output longint  drdata,
    input  longint  dwdata,
    input  longint  dtype,
    input  bit      dwen
);

module SimMem (
  input  clk,
  input  [`XLEN-1:0] iaddr,
  output [`XLEN-1:0] idata,
  input  [`XLEN-1:0] itype,
  input  [`XLEN-1:0] daddr,
  output [`XLEN-1:0] drdata,
  input  [`XLEN-1:0] dwdata,
  input  [`XLEN-1:0] dtype,
  input  dwen
);

  always @(posedge clk) begin
    SimMemAccess(iaddr, idata, itype, daddr, drdata, dwdata, dtype, dwen);
  end

endmodule