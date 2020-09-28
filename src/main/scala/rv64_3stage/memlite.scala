package rv64_3stage

import chisel3._
import chisel3.util._

import ControlConst._

// class MemReq extends Bundle() with phvntomParams {  // write
//   val addr  = Output(UInt(xlen.W))
//   val data  = Output(UInt(xlen.W))
//   val wen   = Output(Bool())
//   val memtype = Output(UInt(xlen.W))
// }

// class MemResp extends Bundle() with phvntomParams {  // read
//   val data = UInt(xlen.W)
// }

// class MemIO extends Bundle with phvntomParams {
//   val req  = Flipped(Valid(new MemReq))
//   val resp = Valid(new MemResp)
// }

// class simpleMem extends Module with phvntomParams {
//   val io = IO(new Bundle() {
//     val iport = new MemIO
//     val dport = new MemIO
//   })

//   val mem = Module(new SimMem)

//   mem.io.clk := clock

//   //  IPORT
//   io.iport.resp.valid := io.iport.req.valid
//   mem.io.iaddr        := io.iport.req.bits.addr
//   mem.io.itype        := io.iport.req.bits.memtype
//   io.iport.resp.bits.data := mem.io.idata

//   //  DPORT
//   io.dport.resp.valid := io.dport.req.valid
//   mem.io.dtype        := io.dport.req.bits.memtype
//   mem.io.dwen         := io.dport.req.bits.wen
//   mem.io.daddr        := io.dport.req.bits.addr
//   mem.io.dwdata       := io.dport.req.bits.data
//   io.dport.resp.bits.data := mem.io.drdata
// }

class SimMemLiteIO extends Bundle with phvntomParams {
  val clk = Input(Clock())
  val raddr = Input(UInt(xlen.W))
  val rdata = Output(UInt(xlen.W))
  val waddr = Input(UInt(xlen.W))
  val wdata = Input(UInt(xlen.W))
  val wmask = Input(UInt(xlen.W))
  val wen = Input(Bool())
}

class SimMemLite extends BlackBox {
  val io = IO(new SimMemLiteIO)
}
