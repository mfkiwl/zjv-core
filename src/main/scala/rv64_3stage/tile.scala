package rv64_3stage

import chisel3._
import chisel3.util._
import common._
import bus._
import device._

class TileIO extends Bundle with phvntomParams {
  // TODO
}


class Tile extends Module with phvntomParams with projectConfig{
  val io = IO(new TileIO)

  val core = Module(new Core)
  
  val icache = Module(new Uncache)
  val dcache = Module(new Uncache)
  val mem = Module(new AXI4RAM(memByte = 4 * 1024 * 1024 * 1024))
  // val clint = Module(new Clint)

  val addrSpace = List(
    // (Settings.getLong("MMIOBase"), Settings.getLong("MMIOSize")), // external devices
    // (0x38000000L, 0x00010000L), // CLINT
    // (0x3c000000L, 0x04000000L)  // PLIC
    (0x0L, 0x02000000L) // mem
  )
  val xbar = Module(new AXI4Xbar(2, addrSpace))

  core.reset := reset

  // clint.io <> xbar.io.out(0)
  mem.io.in <> xbar.io.out(addrSpace.length - 1)
  core.io.imem <> icache.io.in
  icache.io.out <> xbar.io.in(0)  
  core.io.dmem <> dcache.io.in
  dcache.io.out <> xbar.io.in(1)  
}
