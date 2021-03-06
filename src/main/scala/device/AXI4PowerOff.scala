package device

import chisel3._
import chisel3.util._
import tile._
import bus._
import utils._

class AXI4PowerOffIO extends Bundle with phvntomParams {
  val poweroff = Output(UInt(xlen.W))
}

class AXI4PowerOff(name: String = "poweroff")
    extends AXI4LiteSlave(new AXI4PowerOffIO, name)
    with AXI4Parameters {
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

  io.extra.get.poweroff := poweroff
}
