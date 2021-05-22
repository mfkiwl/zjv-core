package tile

import bus._
import chisel3._
import device._
import mem._

class TileIO extends Bundle with phvntomParams {
  // DIFFTEST PLIC
  val plicip   = Output(Vec(32, Bool()))
  val plicie   = Output(UInt(32.W))
  val plicprio = Output(UInt(32.W))
  val plicthrs = Output(UInt(32.W))
  val plicclaim = Output(UInt(32.W))
  // CSR DIFF
  val mstatusr = Output(UInt(xlen.W))
  val mipr = Output(UInt(xlen.W))
  val mier = Output(UInt(xlen.W))
  val mcycler = Output(UInt(xlen.W))
  val current_p = Output(UInt(xlen.W))
  val mepcr = Output(UInt(xlen.W))
  val mtvalr = Output(UInt(xlen.W))
  val mcauser = Output(UInt(xlen.W))
  val sstatusr = Output(UInt(xlen.W))
  val sipr = Output(UInt(xlen.W))
  val sier = Output(UInt(xlen.W))
  val sepcr = Output(UInt(xlen.W))
  val stvalr = Output(UInt(xlen.W))
  val scauser = Output(UInt(xlen.W))
  val stvecr = Output(UInt(xlen.W))
  val mtvecr = Output(UInt(xlen.W))
  val midelegr = Output(UInt(xlen.W))
  val medelegr = Output(UInt(xlen.W))
  // REGS
  val regs     = Output(Vec(regNum/2, UInt(xlen.W)))
  // HALT
  val poweroff = Output(UInt(xlen.W))
  // INT
  val irq = Output(Bool())
  val meip = Output(Bool())
  val seip = Output(Bool())
  // Stalls
  val streqs   = Output(Vec(10, UInt(xlen.W)))
  val dtest_pc = Output(UInt(xlen.W))
  val dtest_inst = Output(UInt(xlen.W))
  val dtest_wbvalid = Output(Bool())
  val dtest_int = Output(Bool())
  val dtest_alu = Output(UInt(xlen.W))
  val dtest_mem = Output(UInt(xlen.W))
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
  val mem = Module(new AXI4RAM(memByte = 128 * 1024 * 1024)) // 0x8000000
  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in

  // mem path
  if (hasCache) {
    if (hasL2Cache) {
      val mem_source = List(icache, dcache)
      // val dcache_wb = Module(new WriteBuffer()(WBConfig(wb_name = "dcache write buffer", dataWidth = dcache.lineBits)))
      // dcache_wb.io.in <> dcache.io.mem
      val memxbar = Module(new CrossbarNto1(1))
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
      val l2cacheBus = Module(new DUncache(l2cache.lineBits, "mem uncache"))
      dcache.io.mem <> l2cache.io.in(0)
      core.io.dmmu <> l2cache.io.in(1)
      icache.io.mem <> l2cache.io.in(2)
      core.io.immu <> l2cache.io.in(3)
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
    io.poweroff := poweroffSync(31, 0)
  } else {
    io.poweroff := 0.U
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
    io.irq := uart_irqSync
    io.meip := hart0_meipSync
    io.seip := hart0_seipSync
  } else {
    io.irq := false.B 
    io.meip := false.B 
    io.seip := false.B
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

  if (diffTest) {
  // Difftest
    io.mstatusr := core.io.mstatusr
    io.mipr := core.io.mipr
    io.mier := core.io.mier
    io.mcycler := core.io.mcycler
    io.current_p := core.io.current_p
    io.mepcr := core.io.mepcr
    io.mtvalr := core.io.mtvalr
    io.mcauser := core.io.mcauser
    io.sstatusr := core.io.sstatusr
    io.sipr := core.io.sipr
    io.sier := core.io.sier
    io.sepcr := core.io.sepcr
    io.stvalr := core.io.stvalr
    io.scauser := core.io.scauser
    io.stvecr := core.io.stvecr
    io.mtvecr := core.io.mtvecr
    io.midelegr := core.io.midelegr
    io.medelegr := core.io.medelegr
  } else {
    io.mstatusr := 0.U
    io.mipr := 0.U
    io.mier := 0.U
    io.mcycler := 0.U
    io.current_p := 0.U
    io.mepcr := 0.U
    io.mtvalr := 0.U
    io.mcauser := 0.U
    io.sstatusr := 0.U
    io.sipr := 0.U
    io.sier := 0.U
    io.sepcr := 0.U
    io.stvalr := 0.U
    io.scauser := 0.U
    io.stvecr := 0.U
    io.mtvecr := 0.U
    io.midelegr := 0.U
    io.medelegr := 0.U    
  }
  if (diffTest) {
    io.regs := VecInit((0 to regNum/2-1).map(i => core.io.regs(i)))
  } else {
    io.regs := VecInit((0 to regNum/2-1).map(i => 0.U))
  }

  if (diffTest) {
    io.streqs := core.io.streqs
    io.dtest_pc := core.io.dtest_pc
    io.dtest_inst := core.io.dtest_inst
    io.dtest_wbvalid := core.io.dtest_wbvalid
    io.dtest_int := core.io.dtest_int
    io.dtest_alu := core.io.dtest_alu
    io.dtest_mem := core.io.dtest_mem
  } else {
    io.streqs := VecInit((0 to 9).map(i => 0.U))
    io.dtest_pc := 0.U
    io.dtest_inst := 0.U
    io.dtest_wbvalid := 0.U
    io.dtest_int := 0.U
    io.dtest_alu := 0.U
    io.dtest_mem := 0.U
  }

  if (diffTest) {
    io.plicip := plic.io.extra.get.plicip
    io.plicie := plic.io.extra.get.plicie
    io.plicprio := plic.io.extra.get.plicprio
    io.plicthrs := plic.io.extra.get.plicthrs
    io.plicclaim := plic.io.extra.get.plicclaim
  } else {
    io.plicip := 0.U
    io.plicie := 0.U
    io.plicprio := 0.U
    io.plicthrs := 0.U
    io.plicclaim := 0.U
  }
}
