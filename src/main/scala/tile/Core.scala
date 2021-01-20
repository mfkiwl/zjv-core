package tile

import chisel3._
import device.MemIO
import tile.common.rf._
import tile.common.control._
import tile.icore._

trait phvntomParams extends config.projectConfig {
}

class CoreIO extends Bundle with phvntomParams {
  val imem = Flipped(new MemIO)
  val dmem = Flipped(new MemIO)
  val immu = Flipped(new MemIO(cachiLine * cachiBlock))
  val dmmu = Flipped(new MemIO(cachiLine * cachiBlock))
  val int = new InterruptIO
}

class Core extends Module with phvntomParams {
  val io = IO(new CoreIO)
  val dpath = Module(new DataPath)
  val cpath = Module(new ControlPath)

  dpath.io.ctrl <> cpath.io
  dpath.io.imem <> io.imem
  dpath.io.dmem <> io.dmem
  dpath.io.immu <> io.immu
  dpath.io.dmmu <> io.dmmu
  dpath.io.int  <> io.int
}