package rv64_3stage

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import common._
import bus._
import device._

class TileIO extends Bundle with phvntomParams {
  // TODO
}


class Tile extends Module with phvntomParams with projectConfig{
  val io = IO(new TileIO)

  val core = Module(new Core)
  core.reset := reset
  
  val icache = Module(new Uncache)
  val dcache = Module(new Uncache)
  val in_device = List(icache, dcache)

  val clint = Module(new Clint)
  val mtipSync = clint.io.extra.get.mtip
  val msipSync = clint.io.extra.get.msip

  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in
  core.io.int.msip := msipSync
  core.io.int.mtip := mtipSync

  val hangup = Module(new AXI4Hangup)
  val hangupSync = hangup.io.extra.get.hangup
  BoringUtils.addSource(hangupSync, "hangup")

  BoringUtils.addSource(mtipSync, "mtip")
  BoringUtils.addSource(msipSync, "msip")

  val mem = Module(new AXI4RAM(memByte = 4 * 1024 * 1024 * 1024))

  val addrSpace = List(
    // (Settings.getLong("MMIOBase"), Settings.getLong("MMIOSize")), // external devices
    (0x000100L, 0x10L), // HANGUP
    (0x38000000L, 0x00010000L), // CLINT
    // (0x3c000000L, 0x04000000L)  // PLIC
    // (0x40600000L, 0x10L), // uart
    (0x0L, 0x100000000L) // mem
  )
  val out_device = List(hangup, clint, mem)

  val xbar = Module(new AXI4Xbar(2, addrSpace))

  // icache.io.out <> xbar.io.in(0)  
  // dcache.io.out <> xbar.io.in(1)
  for(i <- 0 until in_device.length) {
    in_device(i).io.out <> xbar.io.in(i)
  }

  // hangup.io.in <> xbar.io.out(0)
  // clint.io.in <> xbar.io.out(1)
  // mem.io.in <> xbar.io.out(addrSpace.length - 1)  
  for(i <- 0 until out_device.length) {
    out_device(i).io.in <> xbar.io.out(i)
  }
}
