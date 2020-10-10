package rv64_nstage.fu

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams

class MemReq extends Bundle() with phvntomParams {  // write
  val addr  = Output(UInt(xlen.W))
  val data  = Output(UInt(xlen.W))
  val wen   = Output(Bool())
  val memtype = Output(UInt(xlen.W))
}

class MemResp extends Bundle() with phvntomParams {  // read
  val data = UInt(xlen.W)
}

class MemIO extends Bundle with phvntomParams {
  val req  = Flipped(Valid(new MemReq))
  val resp = Valid(new MemResp)
}

class SimMemLiteIO extends Bundle with phvntomParams {
  val clk = Input(Clock())
  val raddr = Input(UInt(xlen.W))
  val rdata = Output(UInt(xlen.W))
  val waddr = Input(UInt(xlen.W))
  val wdata = Input(UInt(xlen.W))
  val wmask = Input(UInt(xlen.W))
  val wen = Input(Bool())
}

class SimMem extends BlackBox {
  val io = IO(new SimMemLiteIO)
}
