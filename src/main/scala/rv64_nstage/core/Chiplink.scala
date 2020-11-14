package rv64_nstage.core

import chisel3._
import chisel3.stage._
import chisel3.util._

import common._
import bus._
import device._
import mem._

class ChiplinkIO extends Bundle with phvntomParams {
  val mem = new AXI4Bundle
  val mmio = new AXI4Bundle
  val frontend = Flipped(new AXI4Bundle)
  val meip = Input(Bool())
}

class ysys_zjv extends Module with phvntomParams {
  val io = IO(new ChiplinkIO)

  val core = Module(new Core)
  core.reset := reset

  // FixMe DISABLED FRONTEND AMBA AXI4 PROTOCOL
  io.frontend.b.valid := false.B
  io.frontend.r.valid := false.B
  io.frontend.aw.ready := false.B
  io.frontend.ar.ready := false.B
  io.frontend.w.ready := false.B
  io.frontend.b.bits := DontCare
  io.frontend.r.bits := DontCare

  // mem path
  val icache = Module(
    new ICacheForwardSplitSync3StageMMIOReorg()(
      CacheConfig(name = "icache", readOnly = true)
    )
  )
  val dcache = Module(new DCacheWriteThroughSplit3StageReorg()(CacheConfig(name = "dcache", readOnly = true)))

  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in

  val mem_source = List(icache, dcache)
  val l2cache = Module(
    new L2CacheSplit3StageReorg(4)(
      CacheConfig(
        name = "l2cache",
        blockBits = dcache.lineBits,
        totalSize = 128
      )
    )
  )
  val l2cacheBus = Module(new DUncache(l2cache.lineBits, "mem uncache"))
  dcache.io.mem <> l2cache.io.in(0)
  core.io.dmmu <> l2cache.io.in(1)
  icache.io.mem <> l2cache.io.in(2)
  core.io.immu <> l2cache.io.in(3)
  l2cache.io.mem <> l2cacheBus.io.in
  l2cacheBus.io.out <> io.mem

  // mmio path
  val immioBus = Module(new Uncache(mname = "immio uncache"))
  icache.io.mmio <> immioBus.io.in
  val dmmioBus = Module(new Uncache(mname = "dmmio uncache"))
  dcache.io.mmio <> dmmioBus.io.in
  val mmioxbar_internal = Module(new CrossbarNto1Lite(2))  
  mmioxbar_internal.io.in(0) <> dmmioBus.io.out
  mmioxbar_internal.io.in(1) <> immioBus.io.out

  // clint
  val clint = Module(new Clint)
  val mtipSync = clint.io.extra.get.mtip
  val msipSync = clint.io.extra.get.msip
  core.io.int.msip := msipSync
  core.io.int.mtip := mtipSync

  // plic
  val plic = Module(new AXI4PLIC)
  val uart_irqSync = io.meip
  val hart0_meipSync = plic.io.extra.get.meip(0)
  val hart0_seipSync = plic.io.extra.get.meip(1)
  plic.io.extra.get.intrVec := uart_irqSync
  core.io.int.meip := hart0_meipSync
  core.io.int.seip := hart0_seipSync

  val mmioxbar_external = Module(new Crossbar1toNLite(AddressSpace.mmio))
  mmioxbar_internal.io.out <> mmioxbar_external.io.in
  mmioxbar_external.io.out(0) <> io.mmio
  mmioxbar_external.io.out(1) <> clint.io.in
  mmioxbar_external.io.out(2) <> plic.io.in
}

object chiplink {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName

    (new chisel3.stage.ChiselStage).execute(
      Array("-td", "build/verilog/" + packageName, "-X", "verilog"),
      Seq(ChiselGeneratorAnnotation(() => new ysys_zjv))
    )
  }
}
