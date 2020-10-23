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
  val icache = Module(
    new ICacheForward()(CacheConfig(name = "icache", readOnly = true, hasMMIO = false))
  )
  val dcache = Module(new DCacheWriteThrough()(CacheConfig(name = "dcache")))
  val mem = Module(new AXI4RAM(memByte = 128 * 1024 * 1024)) // 0x8000000

  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in

  if (hasL2Cache) {
    val mem_source = List(icache, dcache)
    val memxbar = Module(new CrossbarNto1(1))
    val l2cache = Module(
      new L2Cache(4)(
        CacheConfig(
          name = "l2cache",
          blockBits = dcache.lineBits,
          totalSize = 256
        )
      )
    )
    val l2cacheBus = Module(new DUncache(l2cache.lineBits, "mem uncache"))
    for (i <- 0 until mem_source.length) {
      mem_source(i).io.mem <> l2cache.io.in(i)
    }
    core.io.immu <> l2cache.io.in(2)
    core.io.dmmu <> l2cache.io.in(3)
    l2cache.io.mem <> l2cacheBus.io.in
    l2cacheBus.io.out <> memxbar.io.in(0)
    memxbar.io.out <> mem.io.in
  } else {
    val icacheBus = Module(new DUncache(icache.lineBits, "inst uncache"))
    val dcacheBus = Module(new DUncache(dcache.lineBits, "mem uncache"))
    val immuBus = Module(new DUncache(icache.lineBits))
    val dmmuBus = Module(new DUncache(dcache.lineBits))
    val mem_source = List(icacheBus, dcacheBus, immuBus, dmmuBus)
    val memxbar = Module(new CrossbarNto1(mem_source.length))
    icache.io.mem <> icacheBus.io.in
    dcache.io.mem <> dcacheBus.io.in
    core.io.immu <> immuBus.io.in
    core.io.dmmu <> dmmuBus.io.in
    memxbar.io.out <> mem.io.in
    for (i <- 0 until mem_source.length) {
      mem_source(i).io.out <> memxbar.io.in(i)
    }
  }

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
  BoringUtils.addSource(mtipSync, "mtip")
  BoringUtils.addSource(msipSync, "msip")

  // plic
  val plic = Module(new AXI4PLIC)
  plic.io.extra.get.intrVec := 1.U(1.W)
  val meipSync = plic.io.extra.get.meip(0)
  core.io.int.meip := meipSync
  core.io.int.seip := false.B
//  printf("Here is the output of PLIC meip %x\n", meipSync)

  // uart
  val uart = Module(new AXI4UART)

  // xbar
  val mmio_device = List(poweroff, clint, plic, uart)
  val mmioBus = Module(new Uncache(mname = "mmio uncache"))
  val mmioxbar = Module(new Crossbar1toN(AddressSpace.mmio))
  // val xbar = Module(new AXI4Xbar(2, addrSpace))

  dcache.io.mmio <> mmioBus.io.in
  mmioBus.io.out <> mmioxbar.io.in
  for (i <- 0 until mmio_device.length) {
    mmio_device(i).io.in <> mmioxbar.io.out(i)
  }
}
