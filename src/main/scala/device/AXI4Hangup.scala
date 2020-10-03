package device

import chisel3._
import chisel3.util._
import bus.AXI4Parameters
import rv64_3stage.phvntomParams
import bus.AXI4Slave
import utils._

class AXI4HangupIO extends Bundle with phvntomParams {
  val hangup = Output(Bool())
}

class AXI4Hangup extends AXI4Slave(new AXI4HangupIO) with AXI4Parameters {
  val hangup = RegInit(0.U(xlen.W))

  val mapping = Map(
    RegMap(0x0, hangup),
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

  io.extra.get.hangup := RegNext(hangup =/= 0.U)
}
