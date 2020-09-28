package rv64_3stage

import chisel3._
import chisel3.util._
import common._

class TileIO extends Bundle with phvntomParams {
  // TODO
}


class Tile extends Module with phvntomParams with projectConfig{
  val io = IO(new TileIO)

  val core = Module(new Core)

  val mem = if (fpga) {Module(new FPGAMem)} else{Module(new simpleMem)}

  core.reset := reset

  core.io.dmem <> mem.io.dport
  core.io.imem <> mem.io.iport

}
