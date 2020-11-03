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

class ZJV_SOC extends Module with phvntomParams {
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
    new ICacheForwardSplitSync3Stage()(
      CacheConfig(name = "icache", readOnly = true, hasMMIO = false)
    )
  )
  val dcache = Module(new DCacheWriteThroughSplit3Stage()(CacheConfig(name = "dcache")))

  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in

  val mem_source = List(icache, dcache)
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
  l2cacheBus.io.out <> io.mem

  // mmio path
  core.io.int.msip := false.B
  core.io.int.mtip := false.B

  core.io.int.meip := io.meip
  core.io.int.seip := false.B

  // xbar
  val mmioBus = Module(new Uncache(mname = "mmio uncache"))
  dcache.io.mmio <> mmioBus.io.in
  mmioBus.io.out <> io.mmio
}

object chiplink {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName

    (new chisel3.stage.ChiselStage).execute(
      Array("-td", "build/verilog/" + packageName, "-X", "verilog"),
      Seq(ChiselGeneratorAnnotation(() => new ZJV_SOC))
    )
  }
}
