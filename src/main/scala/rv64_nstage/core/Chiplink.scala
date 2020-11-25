package rv64_nstage.core

import chisel3._
import chisel3.stage._
import chisel3.util._

import common._
import bus._
import device._
import mem._

import firrtl.stage.RunFirrtlTransformAnnotation

class ChiplinkIO extends Bundle with phvntomParams {
  val mem = new AXI4Bundle
  val mmio = new AXI4Bundle
  // val frontend = Flipped(new AXI4Bundle)
  val meip = Input(Bool())
}

class SOCIO extends Bundle with phvntomParams {
  val mem_awready = Input(Bool())
  val mem_awvalid = Output(Bool())
  val mem_awaddr = Output(UInt(32.W))
  val mem_awprot = Output(UInt(3.W))
  val mem_awid = Output(UInt(1.W))
  val mem_awuser = Output(UInt(1.W))
  val mem_awlen = Output(UInt(8.W))
  val mem_awsize = Output(UInt(3.W))
  val mem_awburst = Output(UInt(2.W))
  val mem_awlock = Output(UInt(1.W))
  val mem_awcache = Output(UInt(4.W))
  val mem_awqos = Output(UInt(4.W))
  val mem_wready = Input(Bool())
  val mem_wvalid = Output(Bool())
  val mem_wdata = Output(UInt(64.W))
  val mem_wstrb = Output(UInt(8.W))
  val mem_wlast = Output(UInt(1.W))
  val mem_bready = Output(Bool())
  val mem_bvalid = Input(Bool())
  val mem_bresp = Input(UInt(2.W))
  val mem_bid = Input(UInt(1.W))
  val mem_buser = Input(UInt(1.W))
  val mem_arready = Input(Bool())
  val mem_arvalid = Output(Bool())
  val mem_araddr = Output(UInt(32.W))
  val mem_arprot = Output(UInt(3.W))
  val mem_arid = Output(UInt(1.W))
  val mem_aruser = Output(UInt(1.W))
  val mem_arlen = Output(UInt(8.W))
  val mem_arsize = Output(UInt(3.W))
  val mem_arburst = Output(UInt(2.W))
  val mem_arlock = Output(UInt(1.W))
  val mem_arcache = Output(UInt(4.W))
  val mem_arqos = Output(UInt(4.W))
  val mem_rready = Output(Bool())
  val mem_rvalid = Input(Bool())
  val mem_rresp = Input(UInt(2.W))
  val mem_rdata = Input(UInt(64.W))
  val mem_rlast = Input(UInt(1.W))
  val mem_rid = Input(UInt(1.W))
  val mem_ruser = Input(UInt(1.W))
  val mmio_awready = Input(Bool())
  val mmio_awvalid = Output(Bool())
  val mmio_awaddr = Output(UInt(32.W))
  val mmio_awprot = Output(UInt(3.W))
  val mmio_wready = Input(Bool())
  val mmio_wvalid = Output(Bool())
  val mmio_wdata = Output(UInt(64.W))
  val mmio_wstrb = Output(UInt(8.W))
  val mmio_bready = Output(Bool())
  val mmio_bvalid = Input(Bool())
  val mmio_bresp = Input(UInt(2.W))
  val mmio_arready = Input(Bool())
  val mmio_arvalid = Output(Bool())
  val mmio_araddr = Output(UInt(32.W))
  val mmio_arprot = Output(UInt(3.W))
  val mmio_rready = Output(Bool())
  val mmio_rvalid = Input(Bool())
  val mmio_rresp = Input(UInt(2.W))
  val mmio_rdata = Input(UInt(64.W))
  // val frontend_awready = Output(Bool())
  // val frontend_awvalid = Input(Bool())
  // val frontend_awaddr = Input(UInt(32.W))
  // val frontend_awprot = Input(UInt(3.W))
  // val frontend_awid = Input(UInt(1.W))
  // val frontend_awuser = Input(UInt(1.W))
  // val frontend_awlen = Input(UInt(8.W))
  // val frontend_awsize = Input(UInt(3.W))
  // val frontend_awburst = Input(UInt(2.W))
  // val frontend_awlock = Input(UInt(1.W))
  // val frontend_awcache = Input(UInt(4.W))
  // val frontend_awqos = Input(UInt(4.W))
  // val frontend_wready = Output(Bool())
  // val frontend_wvalid = Input(Bool())
  // val frontend_wdata = Input(UInt(64.W))
  // val frontend_wstrb = Input(UInt(8.W))
  // val frontend_wlast = Input(UInt(1.W))
  // val frontend_bready = Input(Bool())
  // val frontend_bvalid = Output(Bool())
  // val frontend_bresp = Output(UInt(2.W))
  // val frontend_bid = Output(UInt(1.W))
  // val frontend_buser = Output(UInt(1.W))
  // val frontend_arready = Output(Bool())
  // val frontend_arvalid = Input(Bool())
  // val frontend_araddr = Input(UInt(32.W))
  // val frontend_arprot = Input(UInt(3.W))
  // val frontend_arid = Input(UInt(1.W))
  // val frontend_aruser = Input(UInt(1.W))
  // val frontend_arlen = Input(UInt(8.W))
  // val frontend_arsize = Input(UInt(3.W))
  // val frontend_arburst = Input(UInt(2.W))
  // val frontend_arlock = Input(UInt(1.W))
  // val frontend_arcache = Input(UInt(4.W))
  // val frontend_arqos = Input(UInt(4.W))
  // val frontend_rready = Input(Bool())
  // val frontend_rvalid = Output(Bool())
  // val frontend_rresp = Output(UInt(2.W))
  // val frontend_rdata = Output(UInt(64.W))
  // val frontend_rlast = Output(UInt(1.W))
  // val frontend_rid = Output(UInt(1.W))
  // val frontend_ruser = Output(UInt(1.W))
  val mtip = Input(Bool())
  val meip = Input(Bool())
}

class ChiplinkTile extends Module with phvntomParams {
  val io = IO(new ChiplinkIO)

  val core = Module(new Core)
  core.reset := reset

  // FixMe DISABLED FRONTEND AMBA AXI4 PROTOCOL
  // io.frontend.b.valid := false.B
  // io.frontend.r.valid := false.B
  // io.frontend.aw.ready := false.B
  // io.frontend.ar.ready := false.B
  // io.frontend.w.ready := false.B
  // io.frontend.b.bits := DontCare
  // io.frontend.r.bits := DontCare

  // mem path
  val icache = if (hasCache) {
    Module(
      new ICacheForwardSplitSync3StageMMIOReorg()(CacheConfig(readOnly = true))
    )
  } else { Module(new CacheDummy()(CacheConfig(name = "icache", lines = 1))) }
  val dcache = if (hasCache) {
    Module(
      new DCacheWriteThroughSplit3StageReorg()(CacheConfig(readOnly = true))
    )
  } else { Module(new CacheDummy()(CacheConfig(lines = 1))) }

  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in

  if (hasCache) {
    val l2cache = Module(
      new L2CacheSplit3StageReorg(4)(
        CacheConfig(blockBits = dcache.lineBits, totalSize = 64)
      )
    )
    val l2cacheBus = Module(new DUncache(l2cache.lineBits))
    dcache.io.mem <> l2cache.io.in(0)
    core.io.dmmu <> l2cache.io.in(1)
    icache.io.mem <> l2cache.io.in(2)
    core.io.immu <> l2cache.io.in(3)
    l2cache.io.mem <> l2cacheBus.io.in
    l2cacheBus.io.out <> io.mem
  } else {
    val memxbar = Module(new CrossbarNto1(4))
    val imemBus = Module(new MemUncache(dataWidth = xlen))
    val dmemBus = Module(new MemUncache(dataWidth = xlen))
    val immuBus = Module(new MemUncache(dataWidth = xlen))
    val dmmuBus = Module(new MemUncache(dataWidth = xlen))
    dcache.io.mem <> dmemBus.io.in
    icache.io.mem <> imemBus.io.in
    core.io.immu <> immuBus.io.in
    core.io.dmmu <> dmmuBus.io.in
    dmemBus.io.out <> memxbar.io.in(0)
    dmmuBus.io.out <> memxbar.io.in(1)
    imemBus.io.out <> memxbar.io.in(2)
    immuBus.io.out <> memxbar.io.in(3)
    memxbar.io.out <> io.mem
  }

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

class ysyx_zjv extends Module with phvntomParams {
  val io = IO(new SOCIO)

  val chiplink = Module(new ChiplinkTile)

  chiplink.io.mem.aw.ready := io.mem_awready
  io.mem_awvalid := chiplink.io.mem.aw.valid
  io.mem_awaddr := chiplink.io.mem.aw.bits.addr
  io.mem_awprot := chiplink.io.mem.aw.bits.prot
  io.mem_awid := chiplink.io.mem.aw.bits.id
  io.mem_awuser := chiplink.io.mem.aw.bits.user
  io.mem_awlen := chiplink.io.mem.aw.bits.len
  io.mem_awsize := chiplink.io.mem.aw.bits.size
  io.mem_awburst := chiplink.io.mem.aw.bits.burst
  io.mem_awlock := chiplink.io.mem.aw.bits.lock
  io.mem_awcache := chiplink.io.mem.aw.bits.cache
  io.mem_awqos := chiplink.io.mem.aw.bits.qos
  chiplink.io.mem.w.ready := io.mem_wready
  io.mem_wvalid := chiplink.io.mem.w.valid
  io.mem_wdata := chiplink.io.mem.w.bits.data
  io.mem_wstrb := chiplink.io.mem.w.bits.strb
  io.mem_wlast := chiplink.io.mem.w.bits.last
  io.mem_bready := chiplink.io.mem.b.ready
  chiplink.io.mem.b.valid := io.mem_bvalid
  chiplink.io.mem.b.bits.resp := io.mem_bresp
  chiplink.io.mem.b.bits.id := io.mem_bid
  chiplink.io.mem.b.bits.user := io.mem_buser
  chiplink.io.mem.ar.ready := io.mem_arready
  io.mem_arvalid := chiplink.io.mem.ar.valid
  io.mem_araddr := chiplink.io.mem.ar.bits.addr
  io.mem_arprot := chiplink.io.mem.ar.bits.prot
  io.mem_arid := chiplink.io.mem.ar.bits.id
  io.mem_aruser := chiplink.io.mem.ar.bits.user
  io.mem_arlen := chiplink.io.mem.ar.bits.len
  io.mem_arsize := chiplink.io.mem.ar.bits.size
  io.mem_arburst := chiplink.io.mem.ar.bits.burst
  io.mem_arlock := chiplink.io.mem.ar.bits.lock
  io.mem_arcache := chiplink.io.mem.ar.bits.cache
  io.mem_arqos := chiplink.io.mem.ar.bits.qos
  io.mem_rready := chiplink.io.mem.r.ready
  chiplink.io.mem.r.valid := io.mem_rvalid
  chiplink.io.mem.r.bits.resp := io.mem_rresp
  chiplink.io.mem.r.bits.data := io.mem_rdata
  chiplink.io.mem.r.bits.last := io.mem_rlast
  chiplink.io.mem.r.bits.id := io.mem_rid
  chiplink.io.mem.r.bits.user := io.mem_ruser
  chiplink.io.mmio.aw.ready := io.mmio_awready
  io.mmio_awvalid := chiplink.io.mmio.aw.valid
  io.mmio_awaddr := chiplink.io.mmio.aw.bits.addr
  io.mmio_awprot := chiplink.io.mmio.aw.bits.prot
  chiplink.io.mmio.w.ready := io.mmio_wready
  io.mmio_wvalid := chiplink.io.mmio.w.valid
  io.mmio_wdata := chiplink.io.mmio.w.bits.data
  io.mmio_wstrb := chiplink.io.mmio.w.bits.strb
  io.mmio_bready := chiplink.io.mmio.b.ready
  chiplink.io.mmio.b.valid := io.mmio_bvalid
  chiplink.io.mmio.b.bits.resp := io.mmio_bresp
  chiplink.io.mmio.ar.ready := io.mmio_arready
  io.mmio_arvalid := chiplink.io.mmio.ar.valid
  io.mmio_araddr := chiplink.io.mmio.ar.bits.addr
  io.mmio_arprot := chiplink.io.mmio.ar.bits.prot
  io.mmio_rready := chiplink.io.mmio.r.ready
  chiplink.io.mmio.r.valid := io.mmio_rvalid
  chiplink.io.mmio.r.bits.resp := io.mmio_rresp
  chiplink.io.mmio.r.bits.data := io.mmio_rdata
  // io.frontend_awready := chiplink.io.frontend.aw.ready
  // chiplink.io.frontend.aw.valid := io.frontend_awvalid
  // chiplink.io.frontend.aw.bits.addr := io.frontend_awaddr
  // chiplink.io.frontend.aw.bits.prot := io.frontend_awprot
  // chiplink.io.frontend.aw.bits.id := io.frontend_awid
  // chiplink.io.frontend.aw.bits.user := io.frontend_awuser
  // chiplink.io.frontend.aw.bits.len := io.frontend_awlen
  // chiplink.io.frontend.aw.bits.size := io.frontend_awsize
  // chiplink.io.frontend.aw.bits.burst := io.frontend_awburst
  // chiplink.io.frontend.aw.bits.lock := io.frontend_awlock
  // chiplink.io.frontend.aw.bits.cache := io.frontend_awcache
  // chiplink.io.frontend.aw.bits.qos := io.frontend_awqos
  // io.frontend_wready := chiplink.io.frontend.w.ready
  // chiplink.io.frontend.w.valid := io.frontend_wvalid
  // chiplink.io.frontend.w.bits.data := io.frontend_wdata
  // chiplink.io.frontend.w.bits.strb := io.frontend_wstrb
  // chiplink.io.frontend.w.bits.last := io.frontend_wlast
  // chiplink.io.frontend.b.ready := io.frontend_bready
  // io.frontend_bvalid := chiplink.io.frontend.b.valid
  // io.frontend_bresp := chiplink.io.frontend.b.bits.resp
  // io.frontend_bid := chiplink.io.frontend.b.bits.id
  // io.frontend_buser := chiplink.io.frontend.b.bits.user
  // io.frontend_arready := chiplink.io.frontend.ar.ready
  // chiplink.io.frontend.ar.valid := io.frontend_arvalid
  // chiplink.io.frontend.ar.bits.addr := io.frontend_araddr
  // chiplink.io.frontend.ar.bits.prot := io.frontend_arprot
  // chiplink.io.frontend.ar.bits.id := io.frontend_arid
  // chiplink.io.frontend.ar.bits.user := io.frontend_aruser
  // chiplink.io.frontend.ar.bits.len := io.frontend_arlen
  // chiplink.io.frontend.ar.bits.size := io.frontend_arsize
  // chiplink.io.frontend.ar.bits.burst := io.frontend_arburst
  // chiplink.io.frontend.ar.bits.lock := io.frontend_arlock
  // chiplink.io.frontend.ar.bits.cache := io.frontend_arcache
  // chiplink.io.frontend.ar.bits.qos := io.frontend_arqos
  // chiplink.io.frontend.r.ready := io.frontend_rready
  // io.frontend_rvalid := chiplink.io.frontend.r.valid
  // io.frontend_rresp := chiplink.io.frontend.r.bits.resp
  // io.frontend_rdata := chiplink.io.frontend.r.bits.data
  // io.frontend_rlast := chiplink.io.frontend.r.bits.last
  // io.frontend_rid := chiplink.io.frontend.r.bits.id
  // io.frontend_ruser := chiplink.io.frontend.r.bits.user
  chiplink.io.meip := io.meip

  chiplink.io.mmio.r.bits.id := DontCare
  chiplink.io.mmio.r.bits.user := DontCare
  chiplink.io.mmio.r.bits.last := DontCare
  chiplink.io.mmio.b.bits.user := DontCare
  // chiplink.io.frontend.w.bits.user := DontCare
  chiplink.io.mmio.b.bits.id := DontCare
}

object chiplink {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName

    (new chisel3.stage.ChiselStage).execute(
      Array("-td", "build/verilog/" + packageName, "-X", "verilog"),
      Seq(
        ChiselGeneratorAnnotation(() => new ysyx_zjv),
        RunFirrtlTransformAnnotation(new AddModulePrefix()),
        ModulePrefixAnnotation("zjv_")
      )
    )
  }
}
