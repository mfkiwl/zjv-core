package rv64_nstage.core

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import common._
import bus._
import device._
import mem._

import firrtl.stage.RunFirrtlTransformAnnotation

class ILABundle extends Bundle with phvntomParams {
  val pc = UInt(xlen.W)
  val inst = UInt(xlen.W)
  val wbvalid = Bool()
  val int = Bool()
  val alu = UInt(xlen.W)
  val mem = Bool()
}

class ChiplinkIO extends Bundle with phvntomParams {
  val mem = new AXI4Bundle
  val mmio = new AXI4Bundle
  // val frontend = Flipped(new AXI4Bundle)
  val meip = Input(Bool())
}

class SOCIO extends Bundle with phvntomParams with projectConfig {
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
  val mmio_awlen = if (ila) { Output(UInt(8.W)) }
  else { null }
  val mmio_awsize = if (ila) { Output(UInt(3.W)) }
  else { null }
  val mmio_awburst = if (ila) { Output(UInt(2.W)) }
  else { null }
  val mmio_awlock = if (ila) { Output(UInt(1.W)) }
  else { null }
  val mmio_awcache = if (ila) { Output(UInt(4.W)) }
  else { null }
  val mmio_awqos = if (ila) { Output(UInt(4.W)) }
  else { null }
  val mmio_wlast = if (ila) { Output(UInt(1.W)) }
  else { null }
  val mmio_arlen = if (ila) { Output(UInt(8.W)) }
  else { null }
  val mmio_arsize = if (ila) { Output(UInt(3.W)) }
  else { null }
  val mmio_arburst = if (ila) { Output(UInt(2.W)) }
  else { null }
  val mmio_arlock = if (ila) { Output(UInt(1.W)) }
  else { null }
  val mmio_arcache = if (ila) { Output(UInt(4.W)) }
  else { null }
  val mmio_arqos = if (ila) { Output(UInt(4.W)) }
  else { null }
  val mmio_rlast = if (ila) { Input(UInt(1.W)) }
  else { null }
  val mmio_awid = if (ila) { Output(UInt(1.W)) }
  else { null }
  val mmio_bid = if (ila) { Input(UInt(1.W)) }
  else { null }
  val mmio_arid = if (ila) { Output(UInt(1.W)) }
  else { null }
  val mmio_rid = if (ila) { Input(UInt(1.W)) }
  else { null }
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
//  val ila_bundle = if (ila) { Output(new ILABundle) }
//  else { null }
}
class ChiplinkTile extends Module with phvntomParams with projectConfig {
  val io = IO(new ChiplinkIO)

  val core = Module(new Core)
  core.reset := reset

  // mem path
  val icache = if (hasCache) {
    if (ila) {
      Module(
        new ICacheForwardSplitSync3StageMMIO()(CacheConfig(readOnly = true))
      )
    } else {
      Module(
        new ICacheForwardSplitSync3StageMMIOReorg()(
          CacheConfig(readOnly = true)
        )
      )
    }
  } else { Module(new CacheDummy()(CacheConfig(name = "icache", lines = 1))) }
  val dcache = if (hasCache) {
    if (ila) {
      Module(
        new DCacheWriteThroughSplit3Stage()(CacheConfig(readOnly = true))
      )
    } else {
      Module(
        new DCacheWriteThroughSplit3StageReorg()(CacheConfig(readOnly = true))
      )
    }
  } else { Module(new CacheDummy()(CacheConfig(lines = 1))) }

  core.io.imem <> icache.io.in
  core.io.dmem <> dcache.io.in

  if (hasCache) {
    val l2cache = if (ila) {
      Module(
        new L2CacheSplit3Stage(4)(
          CacheConfig(blockBits = dcache.lineBits, totalSize = 32, lines = 2, ways = 2)
        )
      )
    } else {
      Module(
        new L2CacheSplit3StageReorg(4)(
          CacheConfig(blockBits = dcache.lineBits, totalSize = 32, lines = 2, ways = 2)
        )
      )
    }
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

class ysyx_zjv extends Module with phvntomParams with projectConfig {
  val io = IO(new SOCIO)

  val chiplinkTile = Module(new ChiplinkTile)

  chiplinkTile.io.mem.aw.ready := io.mem_awready
  io.mem_awvalid := chiplinkTile.io.mem.aw.valid
  io.mem_awaddr := chiplinkTile.io.mem.aw.bits.addr
  io.mem_awprot := chiplinkTile.io.mem.aw.bits.prot
  io.mem_awid := chiplinkTile.io.mem.aw.bits.id
  io.mem_awuser := chiplinkTile.io.mem.aw.bits.user
  io.mem_awlen := chiplinkTile.io.mem.aw.bits.len
  io.mem_awsize := chiplinkTile.io.mem.aw.bits.size
  io.mem_awburst := chiplinkTile.io.mem.aw.bits.burst
  io.mem_awlock := chiplinkTile.io.mem.aw.bits.lock
  io.mem_awcache := chiplinkTile.io.mem.aw.bits.cache
  io.mem_awqos := chiplinkTile.io.mem.aw.bits.qos
  chiplinkTile.io.mem.w.ready := io.mem_wready
  io.mem_wvalid := chiplinkTile.io.mem.w.valid
  io.mem_wdata := chiplinkTile.io.mem.w.bits.data
  io.mem_wstrb := chiplinkTile.io.mem.w.bits.strb
  io.mem_wlast := chiplinkTile.io.mem.w.bits.last
  io.mem_bready := chiplinkTile.io.mem.b.ready
  chiplinkTile.io.mem.b.valid := io.mem_bvalid
  chiplinkTile.io.mem.b.bits.resp := io.mem_bresp
  chiplinkTile.io.mem.b.bits.id := io.mem_bid
  chiplinkTile.io.mem.b.bits.user := io.mem_buser
  chiplinkTile.io.mem.ar.ready := io.mem_arready
  io.mem_arvalid := chiplinkTile.io.mem.ar.valid
  io.mem_araddr := chiplinkTile.io.mem.ar.bits.addr
  io.mem_arprot := chiplinkTile.io.mem.ar.bits.prot
  io.mem_arid := chiplinkTile.io.mem.ar.bits.id
  io.mem_aruser := chiplinkTile.io.mem.ar.bits.user
  io.mem_arlen := chiplinkTile.io.mem.ar.bits.len
  io.mem_arsize := chiplinkTile.io.mem.ar.bits.size
  io.mem_arburst := chiplinkTile.io.mem.ar.bits.burst
  io.mem_arlock := chiplinkTile.io.mem.ar.bits.lock
  io.mem_arcache := chiplinkTile.io.mem.ar.bits.cache
  io.mem_arqos := chiplinkTile.io.mem.ar.bits.qos
  io.mem_rready := chiplinkTile.io.mem.r.ready
  chiplinkTile.io.mem.r.valid := io.mem_rvalid
  chiplinkTile.io.mem.r.bits.resp := io.mem_rresp
  chiplinkTile.io.mem.r.bits.data := io.mem_rdata
  chiplinkTile.io.mem.r.bits.last := io.mem_rlast
  chiplinkTile.io.mem.r.bits.id := io.mem_rid
  chiplinkTile.io.mem.r.bits.user := io.mem_ruser
  chiplinkTile.io.mmio.aw.ready := io.mmio_awready
  io.mmio_awvalid := chiplinkTile.io.mmio.aw.valid
  io.mmio_awaddr := chiplinkTile.io.mmio.aw.bits.addr
  io.mmio_awprot := chiplinkTile.io.mmio.aw.bits.prot
  chiplinkTile.io.mmio.w.ready := io.mmio_wready
  io.mmio_wvalid := chiplinkTile.io.mmio.w.valid
  io.mmio_wdata := chiplinkTile.io.mmio.w.bits.data
  io.mmio_wstrb := chiplinkTile.io.mmio.w.bits.strb
  io.mmio_bready := chiplinkTile.io.mmio.b.ready
  chiplinkTile.io.mmio.b.valid := io.mmio_bvalid
  chiplinkTile.io.mmio.b.bits.resp := io.mmio_bresp
  chiplinkTile.io.mmio.ar.ready := io.mmio_arready
  io.mmio_arvalid := chiplinkTile.io.mmio.ar.valid
  io.mmio_araddr := chiplinkTile.io.mmio.ar.bits.addr
  io.mmio_arprot := chiplinkTile.io.mmio.ar.bits.prot
  io.mmio_rready := chiplinkTile.io.mmio.r.ready
  chiplinkTile.io.mmio.r.valid := io.mmio_rvalid
  chiplinkTile.io.mmio.r.bits.resp := io.mmio_rresp
  chiplinkTile.io.mmio.r.bits.data := io.mmio_rdata
  // io.frontend_awready := chiplinkTile.io.frontend.aw.ready
  // chiplinkTile.io.frontend.aw.valid := io.frontend_awvalid
  // chiplinkTile.io.frontend.aw.bits.addr := io.frontend_awaddr
  // chiplinkTile.io.frontend.aw.bits.prot := io.frontend_awprot
  // chiplinkTile.io.frontend.aw.bits.id := io.frontend_awid
  // chiplinkTile.io.frontend.aw.bits.user := io.frontend_awuser
  // chiplinkTile.io.frontend.aw.bits.len := io.frontend_awlen
  // chiplinkTile.io.frontend.aw.bits.size := io.frontend_awsize
  // chiplinkTile.io.frontend.aw.bits.burst := io.frontend_awburst
  // chiplinkTile.io.frontend.aw.bits.lock := io.frontend_awlock
  // chiplinkTile.io.frontend.aw.bits.cache := io.frontend_awcache
  // chiplinkTile.io.frontend.aw.bits.qos := io.frontend_awqos
  // io.frontend_wready := chiplinkTile.io.frontend.w.ready
  // chiplinkTile.io.frontend.w.valid := io.frontend_wvalid
  // chiplinkTile.io.frontend.w.bits.data := io.frontend_wdata
  // chiplinkTile.io.frontend.w.bits.strb := io.frontend_wstrb
  // chiplinkTile.io.frontend.w.bits.last := io.frontend_wlast
  // chiplinkTile.io.frontend.b.ready := io.frontend_bready
  // io.frontend_bvalid := chiplinkTile.io.frontend.b.valid
  // io.frontend_bresp := chiplinkTile.io.frontend.b.bits.resp
  // io.frontend_bid := chiplinkTile.io.frontend.b.bits.id
  // io.frontend_buser := chiplinkTile.io.frontend.b.bits.user
  // io.frontend_arready := chiplinkTile.io.frontend.ar.ready
  // chiplinkTile.io.frontend.ar.valid := io.frontend_arvalid
  // chiplinkTile.io.frontend.ar.bits.addr := io.frontend_araddr
  // chiplinkTile.io.frontend.ar.bits.prot := io.frontend_arprot
  // chiplinkTile.io.frontend.ar.bits.id := io.frontend_arid
  // chiplinkTile.io.frontend.ar.bits.user := io.frontend_aruser
  // chiplinkTile.io.frontend.ar.bits.len := io.frontend_arlen
  // chiplinkTile.io.frontend.ar.bits.size := io.frontend_arsize
  // chiplinkTile.io.frontend.ar.bits.burst := io.frontend_arburst
  // chiplinkTile.io.frontend.ar.bits.lock := io.frontend_arlock
  // chiplinkTile.io.frontend.ar.bits.cache := io.frontend_arcache
  // chiplinkTile.io.frontend.ar.bits.qos := io.frontend_arqos
  // chiplinkTile.io.frontend.r.ready := io.frontend_rready
  // io.frontend_rvalid := chiplinkTile.io.frontend.r.valid
  // io.frontend_rresp := chiplinkTile.io.frontend.r.bits.resp
  // io.frontend_rdata := chiplinkTile.io.frontend.r.bits.data
  // io.frontend_rlast := chiplinkTile.io.frontend.r.bits.last
  // io.frontend_rid := chiplinkTile.io.frontend.r.bits.id
  // io.frontend_ruser := chiplinkTile.io.frontend.r.bits.user
  chiplinkTile.io.meip := io.meip

  chiplinkTile.io.mmio.r.bits.id := DontCare
  chiplinkTile.io.mmio.r.bits.user := DontCare
  chiplinkTile.io.mmio.r.bits.last := DontCare
  chiplinkTile.io.mmio.b.bits.user := DontCare
  // chiplinkTile.io.frontend.w.bits.user := DontCare
  chiplinkTile.io.mmio.b.bits.id := DontCare

  if (ila) {
    io.mmio_awlen := chiplinkTile.io.mmio.aw.bits.len
    io.mmio_awsize := chiplinkTile.io.mmio.aw.bits.size
    io.mmio_awburst := chiplinkTile.io.mmio.aw.bits.burst
    io.mmio_awlock := chiplinkTile.io.mmio.aw.bits.lock
    io.mmio_awcache := chiplinkTile.io.mmio.aw.bits.cache
    io.mmio_awqos := chiplinkTile.io.mmio.aw.bits.qos
    io.mmio_wlast := chiplinkTile.io.mmio.w.bits.last
    io.mmio_arlen := chiplinkTile.io.mmio.ar.bits.len
    io.mmio_arsize := chiplinkTile.io.mmio.ar.bits.size
    io.mmio_arburst := chiplinkTile.io.mmio.ar.bits.burst
    io.mmio_arlock := chiplinkTile.io.mmio.ar.bits.lock
    io.mmio_arcache := chiplinkTile.io.mmio.ar.bits.cache
    io.mmio_arqos := chiplinkTile.io.mmio.ar.bits.qos
    chiplinkTile.io.mmio.r.bits.last := io.mmio_rlast
    io.mmio_awid := chiplinkTile.io.mmio.aw.bits.id
    chiplinkTile.io.mmio.b.bits.id := io.mmio_bid
    io.mmio_arid := chiplinkTile.io.mmio.ar.bits.id
    chiplinkTile.io.mmio.r.bits.id := io.mmio_rid

//    def BoringUtilsConnect(sink: UInt, id: String) {
//      val temp = WireInit(0.U(64.W))
//      BoringUtils.addSink(temp, id)
//      sink := temp
//    }
//
//    BoringUtilsConnect(io.ila_bundle.pc      ,"ilaPC")
//    BoringUtilsConnect(io.ila_bundle.inst   ,"ilaInst")
//    BoringUtilsConnect(io.ila_bundle.wbvalid   ,"ilaValid")
//    BoringUtilsConnect(io.ila_bundle.int  ,"ilaInt")
//    BoringUtilsConnect(io.ila_bundle.alu  ,"ilaALU")
//    BoringUtilsConnect(io.ila_bundle.mem  ,"ilaMem")
  }
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
