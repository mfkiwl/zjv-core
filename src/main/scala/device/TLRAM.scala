package device

import chisel3._
import chisel3.util._
import bus._
import config._
import utils._

class TLRAM(memByte: Int, name: String = "ram")
    extends TLSlave(name = name)
    with projectConfig {
  // val offsetBits = log2Up(memByte)
  // val offsetMask = (1 << offsetBits) - 1
  // def index(addr: UInt) = (addr & offsetMask.U) >> log2Ceil(xlen / 8)
  // def inRange(idx: UInt) = idx < (memByte / 8).U

  val offset = log2Ceil(bitWidth)
  val wIdx = addr + (writeBeatCnt.value << offset)
  val rIdx = addr + (readBeatCnt.value << offset)

  if (fpga) {
    val mem = Module(new FPGAMem)
    mem.io.clk := clock
    mem.io.raddr := rIdx // raddr
    mem.io.waddr := wIdx // waddr
    mem.io.wdata := io.in.a.data
    mem.io.wmask := fullMask
    mem.io.wen := wen
    val rdata = mem.io.rdata
    io.in.d.data := RegEnable(rdata, ren)
    valid := BoolStopWatch(
      ren,
      fireD,
      startHighPriority = true
    )
  } else {
    val mem = Module(new SimMem)
    mem.io.clk := clock
    mem.io.raddr := rIdx // raddr
    mem.io.waddr := wIdx // waddr
    mem.io.wdata := io.in.a.data
    mem.io.wmask := fullMask
    mem.io.wen := wen
    val rdata = mem.io.rdata
    io.in.d.data := RegEnable(rdata, ren)
    valid := BoolStopWatch(
      ren,
      fireD,
      startHighPriority = true
    )
  }

  printf(p"[${GTimer()}]: TLRAM Debug Start----------\n")
  printf(
    "waddr = %x, wIdx = %x, wdata = %x, wmask = %x, wen = %d, valid = %d, ready = %d, write = %d/%d, writing = %d\n",
    addr,
    wIdx,
    io.in.a.data,
    fullMask,
    wen,
    io.in.a.valid,
    io.in.a.ready,
    writeBeatCnt.value,
    writeBeatTot,
    writing
  )
  printf(
    "raddr = %x, rIdx = %x, rdata = %x, ren = %x, valid = %d, ready = %d, cnt = %d/%d, reading = %d\n",
    addr,
    rIdx,
    io.in.d.data,
    ren,
    io.in.d.valid,
    io.in.d.ready,
    readBeatCnt.value,
    readBeatTot,
    reading
  )
  printf("----------TLRAM Debug Done----------\n")
}
