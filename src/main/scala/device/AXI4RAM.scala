package device

import chisel3._
import chisel3.util._
import bus._
import config._
import utils._

class AXI4RAM(memByte: Int, name: String = "ram")
    extends AXI4Slave(name = name)
    with projectConfig {
  // val offsetBits = log2Up(memByte)
  // val offsetMask = (1 << offsetBits) - 1
  // def index(addr: UInt) = (addr & offsetMask.U) >> log2Ceil(xlen / 8)
  // def inRange(idx: UInt) = idx < (memByte / 8).U

  val offset = log2Ceil(bitWidth)
  val wIdx = waddr + (writeBeatCnt << offset)
  val rIdx = raddr + (readBeatCnt << offset)
  val wen = io.in.w.fire() // && inRange(wIdx)

  if (fpga) {
    val mem = Module(new FPGAMem)
    mem.io.clk := clock
    mem.io.raddr := rIdx // raddr
    mem.io.waddr := wIdx // waddr
    mem.io.wdata := io.in.w.bits.data
    mem.io.wmask := fullMask
    mem.io.wen := wen
    val rdata = mem.io.rdata
    io.in.r.bits.data := RegEnable(rdata, ren)
  } else {
    val mem = Module(new SimMem)
    mem.io.clk := clock
    mem.io.raddr := rIdx // raddr
    mem.io.waddr := wIdx // waddr
    mem.io.wdata := io.in.w.bits.data
    mem.io.wmask := fullMask
    mem.io.wen := wen
    val rdata = mem.io.rdata
    io.in.r.bits.data := RegEnable(rdata, ren)
  }

  // printf(p"[${GTimer()}]: AXI4RAM Debug Start----------\n")
  // printf(
  //   "waddr = %x, wIdx = %x, wdata = %x, wmask = %x, wen = %d, writeBeatCnt = %d\n",
  //   waddr,
  //   wIdx,
  //   io.in.w.bits.data,
  //   fullMask,
  //   wen,
  //   writeBeatCnt
  // )
  // printf(
  //   "raddr = %x, rIdx = %x, readBeatCnt = %d\n",
  //   raddr,
  //   rIdx,
  //   readBeatCnt
  // )
  // printf("----------AXI4RAM Debug Done----------\n")
}
