package mem

import chisel3._
import chisel3.util._
import rv64_nstage.core._

class SRAMWrapperIO(val depth: Int = 256, val dataWidth: Int = 64) extends Bundle with phvntomParams {
  val depthLength = log2Ceil(depth)
  val AA = Input(UInt(depthLength.W))
  val AB = Input(UInt(depthLength.W))
  val DB = Input(UInt(dataWidth.W))
  val CENA = Input(Bool())
  val CENB = Input(Bool())
  val CLKA = Input(Clock())
  val CLKB = Input(Clock())
  val QA = Output(UInt(dataWidth.W))
}

class S011HD2P_X128Y2D53(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))
}

class S011HD2P_X128Y2D54(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))
}

class S011HD2P_X128Y2D64(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))
}
