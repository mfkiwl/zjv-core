package device

import chisel3._
import chisel3.util._
import bus.AXI4Parameters
import rv64_3stage.phvntomParams
import bus.AXI4Slave
import utils._

class ClintIO extends Bundle with phvntomParams {
  val mtip = Output(Bool())
  val msip = Output(Bool())
}

class Clint extends AXI4Slave(new ClintIO) with AXI4Parameters {
  val mtime = RegInit(0.U(xlen.W)) // unit: us
  val mtimecmp = RegInit(1024.U(xlen.W))
  val msip = RegInit(0.U(xlen.W))
  val sim = true
  val speeder = true

  val clk =
    (if (!sim) 40 /* 40MHz / 1000000 */
     else 10000)
  val freq = RegInit(clk.U(16.W))
  val inc = RegInit(1.U(16.W))

  val cnt = RegInit(0.U(16.W))
  val nextCnt = cnt + 1.U
  cnt := Mux(nextCnt < freq, nextCnt, 0.U)
  val tick = (nextCnt === freq)
  if(speeder) {
    when(true.B) {
      mtime := mtime + inc
    }
  }
  else {
    when(tick) {
      mtime := mtime + inc
    }
  }

  val mapping = Map(
    RegMap(0x0, msip),
    RegMap(0x4000, mtimecmp),
    RegMap(0x8000, freq),
    RegMap(0x8008, inc),
    RegMap(0xbff8, mtime)
  )

  def getOffset(addr: UInt) = addr(15, 0)

  RegMap.generate(
    mapping,
    getOffset(raddr),
    io.in.r.bits.data,
    getOffset(waddr),
    io.in.w.fire(),
    io.in.w.bits.data,
    MaskExpand(io.in.w.bits.strb)
  )

  io.extra.get.mtip := RegNext(mtime >= mtimecmp)
  io.extra.get.msip := RegNext(msip =/= 0.U)
}
