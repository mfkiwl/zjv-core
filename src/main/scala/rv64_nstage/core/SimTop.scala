package rv64_nstage.core

import chisel3._
import chisel3.stage._
import chisel3.util._

import common._
import bus._
import device._
import mem._

class SimTopIO extends Bundle with phvntomParams {
  val mmio = new AXI4Bundle
  val clint = Flipped(new AXI4Bundle)
  val plic = Flipped(new AXI4Bundle)
  val uart_irq = Input(Bool())
}

class SimTop extends Module with phvntomParams {
  val io = IO(new SimTopIO)

  val core = Module(new Core)
  core.reset := reset

  // mem path
  val icache = Module(
    new ICacheForwardSplitSync3Stage()(
      CacheConfig(name = "icache", readOnly = true, hasMMIO = false)
    )
  )
  val dcache = Module(new DCacheWriteThroughSplit3Stage()(CacheConfig(name = "dcache")))
  val mem = Module(new AXI4RAM(memByte = 128 * 1024 * 1024)) // 0x8000000

  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in

  val mem_source = List(icache, dcache)
  val memxbar = Module(new CrossbarNto1(1))
  val l2cache = Module(
    new L2CacheSplit3Stage(4)(
      CacheConfig(
        name = "l2cache",
        blockBits = dcache.lineBits,
        totalSize = 128
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

  // mmio path

  // clint
  val clint = Module(new Clint)
  val mtipSync = clint.io.extra.get.mtip
  val msipSync = clint.io.extra.get.msip
  core.io.int.msip := msipSync
  core.io.int.mtip := mtipSync

  // plic
  val plic = Module(new AXI4PLIC)
  val uart_irqSync = io.uart_irq
  val hart0_meipSync = plic.io.extra.get.meip(0)
  val hart0_seipSync = plic.io.extra.get.meip(1)
  plic.io.extra.get.intrVec := uart_irqSync
  core.io.int.meip := hart0_meipSync
  core.io.int.seip := hart0_seipSync
  
  // xbar
  val mmioBus = Module(new Uncache(mname = "mmio uncache"))
  dcache.io.mmio <> mmioBus.io.in
  mmioBus.io.out <> io.mmio
  clint.io.in <> io.clint
  plic.io.in <> io.plic
}

object generate {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName

    (new chisel3.stage.ChiselStage).execute(
      Array("-td", "build/verilog/" + packageName, "-X", "verilog"),
      Seq(ChiselGeneratorAnnotation(() => new SimTop))
    )
  }
}
