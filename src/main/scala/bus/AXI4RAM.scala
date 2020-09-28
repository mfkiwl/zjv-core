package bus

import chisel3._
import chisel3.util._
import rv64_3stage.SimMemLite

class AXI4RAM(memByte: Int) extends AXI4Slave {
  val offsetBits = log2Up(memByte)
  val offsetMask = (1 << offsetBits) - 1
  def index(addr: UInt) = (addr & offsetMask.U) >> log2Ceil(xlen / 8)
  def inRange(idx: UInt) = idx < (memByte / 8).U

  val wIdx = index(waddr) + writeBeatCnt
  val rIdx = index(raddr) + readBeatCnt
  val wen = io.in.w.fire() && inRange(wIdx)

  val mem = Module(new SimMemLite)
  mem.io.clk := clock
  mem.io.raddr := rIdx
  mem.io.waddr := wIdx
  mem.io.wdata := io.in.w.bits.data
  mem.io.wmask := fullMask
  mem.io.wen := wen
  val rdata = mem.io.rdata
  io.in.r.bits.data := RegEnable(rdata, ren)
}
