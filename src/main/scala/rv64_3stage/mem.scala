package rv64_3stage

import chisel3._
import chisel3.util._

import ControlConst._

class MemReq extends Bundle() with phvntomParams {  // write
  val addr = UInt(xlen.W)
  val data = UInt(xlen.W)
  val wen  = Output(Bool())
  val mask = Output(UInt((xlen/8).W))
}

class MemResp extends Bundle() with phvntomParams {  // read
  val data = UInt(xlen.W)
}

class MemIO extends Bundle with phvntomParams {
  val req   = Flipped(Valid(new MemReq))
  val resp  = Valid(new MemResp)
}