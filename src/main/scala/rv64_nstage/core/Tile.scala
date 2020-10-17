package rv64_nstage.core

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import common._
import bus._
import device._
import mem._
import rv64_nstage.core
import mem.Uncache
import mem.DUncache

class TileIO extends Bundle with phvntomParams {
  // TODO
}

class Tile extends Module with phvntomParams with projectConfig {
  val io = IO(new TileIO)

  val core = Module(new Core)
  core.reset := reset

  // mem path
  val icache = Module(new ICacheSimple()(CacheConfig(name = "icache", readOnly = true, hasMMIO = false)))
  val icacheBus = Module(new DUncache(4 * xlen, "inst uncache")) // TODO parameterize this
  val dcache = Module(new DCacheSimple()(CacheConfig(name = "dcache")))
  val dcacheBus = Module(new DUncache(4 * xlen, "mem uncache")) // TODO parameterize this
  val mmioBus = Module(new Uncache(mname = "mmio uncache"))
  val mem_source = List(icacheBus, dcacheBus)
  val mem = Module(new AXI4RAM(memByte = 128 * 1024 * 1024)) // 0x8000000
  val memxbar = Module(new CrossbarNto1(2))

  core.io.imem <> icache.io.in
  icache.io.mem <> icacheBus.io.in
  core.io.dmem <> dcache.io.in
  dcache.io.mem <> dcacheBus.io.in
  for (i <- 0 until mem_source.length) {
    mem_source(i).io.out <> memxbar.io.in(i)
  }
  memxbar.io.out <> mem.io.in

  // mmio path
  // power off
  val poweroff = Module(new AXI4PowerOff)
  val poweroffSync = poweroff.io.extra.get.poweroff
  BoringUtils.addSource(poweroffSync(31, 0), "poweroff")

  // clint
  val clint = Module(new Clint)
  val mtipSync = clint.io.extra.get.mtip
  val msipSync = clint.io.extra.get.msip
  core.io.int.msip := msipSync
  core.io.int.mtip := mtipSync
  core.io.int.meip := false.B
  core.io.int.seip := false.B
  BoringUtils.addSource(mtipSync, "mtip")
  BoringUtils.addSource(msipSync, "msip")

  // uart
  val uart = Module(new AXI4UART)
  uart.io.extra.get.offset := mmioBus.io.offset

  // xbar
  val mmio_device = List(poweroff, clint, uart)
  val mmioxbar = Module(new Crossbar1toN(AddressSpace.mmio))
  // val xbar = Module(new AXI4Xbar(2, addrSpace))

  dcache.io.mmio <> mmioBus.io.in
  mmioBus.io.out <> mmioxbar.io.in
  for (i <- 0 until mmio_device.length) {
    mmio_device(i).io.in <> mmioxbar.io.out(i)
  }
}
