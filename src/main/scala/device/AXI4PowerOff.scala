package device

import chisel3._
import chisel3.util._
import bus.AXI4Parameters
import rv64_3stage.phvntomParams
import bus.AXI4Slave
import utils._

class AXI4PowerOffIO extends Bundle with phvntomParams {
  val poweroff = Output(Bool())
}

class AXI4PowerOff extends AXI4Slave(new AXI4PowerOffIO) with AXI4Parameters {
  val poweroff = RegInit(0.U(xlen.W))

  val mapping = Map(
    RegMap(0x0, poweroff)
  )

  RegMap.generate(
    mapping,
    raddr(3, 0),
    io.in.r.bits.data,
    waddr(3, 0),
    io.in.w.fire(),
    io.in.w.bits.data,
    MaskExpand(io.in.w.bits.strb)
  )

  io.extra.get.poweroff := RegNext(poweroff =/= 0.U)
}
