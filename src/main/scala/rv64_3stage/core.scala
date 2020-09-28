package rv64_3stage

import chisel3._
import chisel3.util._
import common._

trait phvntomParams {
  val xlen     = 64
  val bitWidth = log2Ceil(xlen)
  val regNum   = 32

  val diffTest = true
  val pipe     = true
}


class CoreIO extends Bundle with phvntomParams {
  val imem = Flipped(new MemIO)
  val dmem = Flipped(new MemIO)
}

class Core extends Module with phvntomParams {
  val io = IO(new CoreIO)

  val dpath = Module(new DataPath)
  val cpath = Module(new ControlPath)

  dpath.io.ctrl <> cpath.io
  dpath.io.imem <> io.imem
  dpath.io.dmem <> io.dmem

}
