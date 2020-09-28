// `define XLEN 64
// import "DPI-C" function void SimMemLiteAccess (
//     input  longint  raddr,
//     output longint  rdata,
//     input  longint  waddr,
//     input  longint  wdata,
//     input  longint  wmask,
//     input  bit      wen
// );

// module SimMemLite (
//   input  clk,
//   input  [`XLEN-1:0] raddr,
//   output [`XLEN-1:0] rdata,
//   input  [`XLEN-1:0] waddr,  
//   input  [`XLEN-1:0] wdata,
//   input  [`XLEN-1:0] wmask,
//   input  wen
// );

//   always @(posedge clk) begin
//     SimMemLiteAccess(raddr, rdata, waddr, wdata, wmask, wen);
//   end

// endmodule