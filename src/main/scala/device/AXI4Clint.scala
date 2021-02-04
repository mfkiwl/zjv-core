package device

import chisel3._
import chisel3.util._
import rv64_nstage.core._
import bus._
import utils._

class ClintIO extends Bundle with phvntomParams {
  val mtip = Output(Bool())
  val msip = Output(Bool())
}

class Clint(name: String = "clint")
    extends AXI4LiteSlave(new ClintIO, name)
    with AXI4Parameters {
  val mtime = RegInit(0.S(xlen.W).asUInt) // unit: us
  val mtimecmp = RegInit(1024.U(xlen.W))
  val msip = RegInit(0.U(xlen.W))
  val sim = true
  val speeder = false

  val clk =
    (if (!sim) 40 /* 40MHz / 1000000 */
     else if (fpga) 10000
    else 100)
  val freq = RegInit(clk.U(16.W))
  val inc = RegInit(1.U(16.W))

  val cnt = RegInit(1.U(16.W))
  val nextCnt = cnt + 1.U
  cnt := Mux(nextCnt < freq, nextCnt, 0.U)
  val tick = (nextCnt === freq)
  if (speeder) {
    when(true.B) {
      mtime := mtime + inc
    }
  } else {
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

  // printf("MTIME %x, MCMP %x, cnt %x\n", mtime, mtimecmp, cnt)

  io.extra.get.mtip := mtime >= mtimecmp
  io.extra.get.msip := msip =/= 0.U
}
