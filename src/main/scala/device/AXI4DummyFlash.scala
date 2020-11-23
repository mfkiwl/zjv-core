package device

import chisel3._
import chisel3.util._
import bus._
import common._
import utils._

class AXI4DummyFlash(memByte: Int, name: String = "flash")
    extends AXI4LiteSlave(name = name)
    with projectConfig {


  val wen = false.B

  val mem = Module(new SimMem)
  mem.io.clk := clock
  mem.io.raddr := Cat(raddr(xlen-1, 3), Fill(3, 0.U))
  mem.io.waddr := waddr
  mem.io.wdata := io.in.w.bits.data
  mem.io.wmask := fullMask
  mem.io.wen := wen

  val offset = raddr(2, 0) << 3

  



  val rdata = Fill(2, (mem.io.rdata >> offset)(31, 0) )
  io.in.r.bits.data := RegEnable(rdata, ren)
}
