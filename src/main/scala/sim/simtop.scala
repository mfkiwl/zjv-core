package sim

import chisel3._
import chisel3.util._
import bus._
import rv64_3stage._

class TileIO extends Bundle with phvntomParams {
  // TODO
}

class SimTop extends Module {
  val io = IO(new TileIO)

  val core = Module(new Core)
  
  val icache = Module(new CacheDummy)
  val dcache = Module(new CacheDummy)
  val mem = Module(new AXI4RAM(memByte = 128 * 1024 * 1024))
  val xbar = Module(new AXI4Xbar(2, List((0L, 100L), (100L, 100L))))

  core.reset := reset

  mem.io <> xbar.io.out(0)
  core.io.imem <> icache.io.in
  icache.io.out <> xbar.io.in(0)  
  core.io.dmem <> dcache.io.in
  dcache.io.out <> xbar.io.in(1)  
}
