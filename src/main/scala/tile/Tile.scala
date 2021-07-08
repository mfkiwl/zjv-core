package tile

import bus._
import chisel3._
import chisel3.util.experimental.BoringUtils
import device._
import mem._

class TileIO extends Bundle with phvntomParams {
  // TODO
}

class Tile extends Module with phvntomParams {
  val io = IO(new TileIO)

  val core = Module(new Core)
  core.reset := reset

  val icache = if (hasCache) {
    if (withCExt) {
      Module(
        new ShadowICache()(
          CacheConfig(name = "icache", readOnly = true, shadowByte = true)
        )
      )
    } else {
      Module(
        new ICacheForwardSplitSync3StageMMIO()(
          CacheConfig(name = "icache", readOnly = true)
        )
      )
    }
  } else { Module(new CacheDummy()(CacheConfig(name = "icache", lines = 1))) }
  val dcache = if (hasCache) {
    Module(
      new DCacheWriteThroughSplit3Stage()(
        CacheConfig(name = "dcache", readOnly = true)
      )
    )
  } else { Module(new CacheDummy()(CacheConfig(name = "dcache", lines = 1))) }
  val mem = Module(new TLRAM(memByte = 128 * 1024 * 1024)) // 0x8000000
  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in

  // mem path
  if (hasCache) {
    if (hasL2Cache) {
      val mem_source = List(icache, dcache)
      // val dcache_wb = Module(new WriteBuffer()(WBConfig(wb_name = "dcache write buffer", dataWidth = dcache.lineBits)))
      // dcache_wb.io.in <> dcache.io.mem
      //val memxbar = Module(new CrossbarNto1(1))
      val l2cache = Module(
        new L2CacheSplit3Stage(4)(
          CacheConfig(
            name = "l2cache",
            blockBits = dcache.lineBits,
            totalSize = 32,
            lines = 2,
            ways = 2
          )
        )
      )
      val l2cacheBus = Module(new TLUncache(false, l2cache.lineBits, "mem uncache"))
      dcache.io.mem <> l2cache.io.in(0)
      core.io.dmmu <> l2cache.io.in(1)
      icache.io.mem <> l2cache.io.in(2)
      core.io.immu <> l2cache.io.in(3)
      l2cache.io.mem <> l2cacheBus.io.in
      //l2cacheBus.io.out <> memxbar.io.in(0)
      //memxbar.io.out <> mem.io.in
      l2cacheBus.io.out <> mem.io.in
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
  } else {
    val memxbar = Module(new CrossbarNto1(4))
    val imemBus = Module(new MemUncache(dataWidth = xlen, mname = "imem uncache"))
    val dmemBus = Module(new MemUncache(dataWidth = xlen, mname = "dmem uncache"))
    val immuBus = Module(new MemUncache(dataWidth = xlen, mname = "immu uncache"))
    val dmmuBus = Module(new MemUncache(dataWidth = xlen, mname = "dmmu uncache"))
    dcache.io.mem <> dmemBus.io.in
    icache.io.mem <> imemBus.io.in
    core.io.immu <> immuBus.io.in
    core.io.dmmu <> dmmuBus.io.in
    dmemBus.io.out <> memxbar.io.in(0)
    dmmuBus.io.out <> memxbar.io.in(1)
    imemBus.io.out <> memxbar.io.in(2)
    immuBus.io.out <> memxbar.io.in(3)
    memxbar.io.out <> mem.io.in
  }

  // mmio path
  // flash
  val flash = Module(new AXI4DummyFlash(memByte = 16 * 1024 * 1024))

  // uart
  val uart = Module(new AXI4UART)

  // power off
  val poweroff = Module(new AXI4PowerOff)
  val poweroffSync = poweroff.io.extra.get.poweroff
  if (diffTest) {
    BoringUtils.addSource(poweroffSync(31, 0), "difftestpoweroff")
  }

  // clint
  val clint = Module(new Clint)
  val mtipSync = clint.io.extra.get.mtip
  val msipSync = clint.io.extra.get.msip
  core.io.int.msip := msipSync
  core.io.int.mtip := mtipSync

  // plic
  val plic = Module(new AXI4PLIC)
  val uart_irqSync = uart.io.extra.get.irq
  val hart0_meipSync = plic.io.extra.get.meip(0)
  val hart0_seipSync = plic.io.extra.get.meip(1)
  plic.io.extra.get.intrVec := uart_irqSync
  core.io.int.meip := hart0_meipSync
  core.io.int.seip := hart0_seipSync
  if (diffTest) {
    BoringUtils.addSource(uart_irqSync, "difftestuartirq")
    BoringUtils.addSource(hart0_meipSync, "difftestplicmeip")
    BoringUtils.addSource(hart0_seipSync, "difftestplicseip")
  }

  // xbar
  val immioBus = Module(new Uncache(mname = "immio uncache"))
  icache.io.mmio <> immioBus.io.in
  val dmmioBus = Module(new Uncache(mname = "dmmio uncache"))
  dcache.io.mmio <> dmmioBus.io.in
  val mmioxbar_internal = Module(new CrossbarNto1Lite(2))
  mmioxbar_internal.io.in(0) <> dmmioBus.io.out
  mmioxbar_internal.io.in(1) <> immioBus.io.out

  val mmio_device = List(flash, poweroff, clint, plic, uart)
  val mmioxbar_external = Module(new Crossbar1toNLite(AddressSpace.mmio))
  mmioxbar_internal.io.out <> mmioxbar_external.io.in
  for (i <- 0 until mmio_device.length) {
    mmio_device(i).io.in <> mmioxbar_external.io.out(i)
  }
}
