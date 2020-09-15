package rv64_3stage

import chisel3._
import chisel3.util._

class TileIO extends Bundle with phvntomParams {
  // TODO
}


class Tile extends Module with phvntomParams {
  val io = IO(new TileIO)

  val core = Module(new Core)

  val mem = Module(new simpleMem)

  core.reset := reset

  core.io.dmem <> mem.io.dport
  core.io.imem <> mem.io.iport

}
