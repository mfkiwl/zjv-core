package device

import chisel3._
import chisel3.util._
import bus._
import common._
import utils._

class AXI4DummyFlash(memByte: Int, name: String = "flash")
    extends AXI4LiteSlave(name = name)
    with projectConfig {

  val offset = log2Ceil(bitWidth)
  val wen = io.in.w.fire()

  val mem = Module(new SimMem)
  mem.io.clk := clock
  mem.io.raddr := raddr
  mem.io.waddr := waddr
  mem.io.wdata := io.in.w.bits.data
  mem.io.wmask := fullMask
  mem.io.wen := wen
  val rdata = mem.io.rdata
  io.in.r.bits.data := RegEnable(rdata, ren)
}
