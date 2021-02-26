package tile

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import common._
import bus._
import device._
import firrtl.stage.RunFirrtlTransformAnnotation
import mem._


class FPGATile extends Module with phvntomParams {
  val io = IO(new ChiplinkIO)

  val core = Module(new Core)
  core.reset := reset

  // mem path
  val icache = if (hasCache) {
    if (ila) {
      if (withCExt) {
        Module(
          new ShadowICache()(CacheConfig(name = "icache", readOnly = true, shadowByte = true))
        )
      } else {
        Module(
          new ICacheForwardSplitSync3StageMMIO()(CacheConfig(name = "icache", readOnly = true))
        )
      }
    } else {
      Module(
        new ICacheForwardSplitSync3StageMMIOReorg()(
          CacheConfig(name = "icache", readOnly = true)
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

class fpga_zjv extends Module with phvntomParams {
  val io = IO(new SOCIO)

  val FPGATile = Module(new FPGATile)

  FPGATile.io.mem.aw.ready := io.mem_awready
  io.mem_awvalid := FPGATile.io.mem.aw.valid
  io.mem_awaddr := FPGATile.io.mem.aw.bits.addr
  io.mem_awprot := FPGATile.io.mem.aw.bits.prot
  io.mem_awid := FPGATile.io.mem.aw.bits.id
  io.mem_awuser := FPGATile.io.mem.aw.bits.user
  io.mem_awlen := FPGATile.io.mem.aw.bits.len
  io.mem_awsize := FPGATile.io.mem.aw.bits.size
  io.mem_awburst := FPGATile.io.mem.aw.bits.burst
  io.mem_awlock := FPGATile.io.mem.aw.bits.lock
  io.mem_awcache := FPGATile.io.mem.aw.bits.cache
  io.mem_awqos := FPGATile.io.mem.aw.bits.qos
  FPGATile.io.mem.w.ready := io.mem_wready
  io.mem_wvalid := FPGATile.io.mem.w.valid
  io.mem_wdata := FPGATile.io.mem.w.bits.data
  io.mem_wstrb := FPGATile.io.mem.w.bits.strb
  io.mem_wlast := FPGATile.io.mem.w.bits.last
  io.mem_bready := FPGATile.io.mem.b.ready
  FPGATile.io.mem.b.valid := io.mem_bvalid
  FPGATile.io.mem.b.bits.resp := io.mem_bresp
  FPGATile.io.mem.b.bits.id := io.mem_bid
  FPGATile.io.mem.b.bits.user := io.mem_buser
  FPGATile.io.mem.ar.ready := io.mem_arready
  io.mem_arvalid := FPGATile.io.mem.ar.valid
  io.mem_araddr := FPGATile.io.mem.ar.bits.addr
  io.mem_arprot := FPGATile.io.mem.ar.bits.prot
  io.mem_arid := FPGATile.io.mem.ar.bits.id
  io.mem_aruser := FPGATile.io.mem.ar.bits.user
  io.mem_arlen := FPGATile.io.mem.ar.bits.len
  io.mem_arsize := FPGATile.io.mem.ar.bits.size
  io.mem_arburst := FPGATile.io.mem.ar.bits.burst
  io.mem_arlock := FPGATile.io.mem.ar.bits.lock
  io.mem_arcache := FPGATile.io.mem.ar.bits.cache
  io.mem_arqos := FPGATile.io.mem.ar.bits.qos
  io.mem_rready := FPGATile.io.mem.r.ready
  FPGATile.io.mem.r.valid := io.mem_rvalid
  FPGATile.io.mem.r.bits.resp := io.mem_rresp
  FPGATile.io.mem.r.bits.data := io.mem_rdata
  FPGATile.io.mem.r.bits.last := io.mem_rlast
  FPGATile.io.mem.r.bits.id := io.mem_rid
  FPGATile.io.mem.r.bits.user := io.mem_ruser
  FPGATile.io.mmio.aw.ready := io.mmio_awready
  io.mmio_awvalid := FPGATile.io.mmio.aw.valid
  io.mmio_awaddr := FPGATile.io.mmio.aw.bits.addr
  io.mmio_awprot := FPGATile.io.mmio.aw.bits.prot
  FPGATile.io.mmio.w.ready := io.mmio_wready
  io.mmio_wvalid := FPGATile.io.mmio.w.valid
  io.mmio_wdata := FPGATile.io.mmio.w.bits.data
  io.mmio_wstrb := FPGATile.io.mmio.w.bits.strb
  io.mmio_bready := FPGATile.io.mmio.b.ready
  FPGATile.io.mmio.b.valid := io.mmio_bvalid
  FPGATile.io.mmio.b.bits.resp := io.mmio_bresp
  FPGATile.io.mmio.ar.ready := io.mmio_arready
  io.mmio_arvalid := FPGATile.io.mmio.ar.valid
  io.mmio_araddr := FPGATile.io.mmio.ar.bits.addr
  io.mmio_arprot := FPGATile.io.mmio.ar.bits.prot
  io.mmio_rready := FPGATile.io.mmio.r.ready
  FPGATile.io.mmio.r.valid := io.mmio_rvalid
  FPGATile.io.mmio.r.bits.resp := io.mmio_rresp
  FPGATile.io.mmio.r.bits.data := io.mmio_rdata
  // io.frontend_awready := FPGATile.io.frontend.aw.ready
  // FPGATile.io.frontend.aw.valid := io.frontend_awvalid
  // FPGATile.io.frontend.aw.bits.addr := io.frontend_awaddr
  // FPGATile.io.frontend.aw.bits.prot := io.frontend_awprot
  // FPGATile.io.frontend.aw.bits.id := io.frontend_awid
  // FPGATile.io.frontend.aw.bits.user := io.frontend_awuser
  // FPGATile.io.frontend.aw.bits.len := io.frontend_awlen
  // FPGATile.io.frontend.aw.bits.size := io.frontend_awsize
  // FPGATile.io.frontend.aw.bits.burst := io.frontend_awburst
  // FPGATile.io.frontend.aw.bits.lock := io.frontend_awlock
  // FPGATile.io.frontend.aw.bits.cache := io.frontend_awcache
  // FPGATile.io.frontend.aw.bits.qos := io.frontend_awqos
  // io.frontend_wready := FPGATile.io.frontend.w.ready
  // FPGATile.io.frontend.w.valid := io.frontend_wvalid
  // FPGATile.io.frontend.w.bits.data := io.frontend_wdata
  // FPGATile.io.frontend.w.bits.strb := io.frontend_wstrb
  // FPGATile.io.frontend.w.bits.last := io.frontend_wlast
  // FPGATile.io.frontend.b.ready := io.frontend_bready
  // io.frontend_bvalid := FPGATile.io.frontend.b.valid
  // io.frontend_bresp := FPGATile.io.frontend.b.bits.resp
  // io.frontend_bid := FPGATile.io.frontend.b.bits.id
  // io.frontend_buser := FPGATile.io.frontend.b.bits.user
  // io.frontend_arready := FPGATile.io.frontend.ar.ready
  // FPGATile.io.frontend.ar.valid := io.frontend_arvalid
  // FPGATile.io.frontend.ar.bits.addr := io.frontend_araddr
  // FPGATile.io.frontend.ar.bits.prot := io.frontend_arprot
  // FPGATile.io.frontend.ar.bits.id := io.frontend_arid
  // FPGATile.io.frontend.ar.bits.user := io.frontend_aruser
  // FPGATile.io.frontend.ar.bits.len := io.frontend_arlen
  // FPGATile.io.frontend.ar.bits.size := io.frontend_arsize
  // FPGATile.io.frontend.ar.bits.burst := io.frontend_arburst
  // FPGATile.io.frontend.ar.bits.lock := io.frontend_arlock
  // FPGATile.io.frontend.ar.bits.cache := io.frontend_arcache
  // FPGATile.io.frontend.ar.bits.qos := io.frontend_arqos
  // FPGATile.io.frontend.r.ready := io.frontend_rready
  // io.frontend_rvalid := FPGATile.io.frontend.r.valid
  // io.frontend_rresp := FPGATile.io.frontend.r.bits.resp
  // io.frontend_rdata := FPGATile.io.frontend.r.bits.data
  // io.frontend_rlast := FPGATile.io.frontend.r.bits.last
  // io.frontend_rid := FPGATile.io.frontend.r.bits.id
  // io.frontend_ruser := FPGATile.io.frontend.r.bits.user
  FPGATile.io.meip := io.meip

  FPGATile.io.mmio.r.bits.id := DontCare
  FPGATile.io.mmio.r.bits.user := DontCare
  FPGATile.io.mmio.r.bits.last := DontCare
  FPGATile.io.mmio.b.bits.user := DontCare
  // FPGATile.io.frontend.w.bits.user := DontCare
  FPGATile.io.mmio.b.bits.id := DontCare

  if (ila) {
    io.mmio_awlen := FPGATile.io.mmio.aw.bits.len
    io.mmio_awsize := FPGATile.io.mmio.aw.bits.size
    io.mmio_awburst := FPGATile.io.mmio.aw.bits.burst
    io.mmio_awlock := FPGATile.io.mmio.aw.bits.lock
    io.mmio_awcache := FPGATile.io.mmio.aw.bits.cache
    io.mmio_awqos := FPGATile.io.mmio.aw.bits.qos
    io.mmio_wlast := FPGATile.io.mmio.w.bits.last
    io.mmio_arlen := FPGATile.io.mmio.ar.bits.len
    io.mmio_arsize := FPGATile.io.mmio.ar.bits.size
    io.mmio_arburst := FPGATile.io.mmio.ar.bits.burst
    io.mmio_arlock := FPGATile.io.mmio.ar.bits.lock
    io.mmio_arcache := FPGATile.io.mmio.ar.bits.cache
    io.mmio_arqos := FPGATile.io.mmio.ar.bits.qos
    FPGATile.io.mmio.r.bits.last := io.mmio_rlast
    io.mmio_awid := FPGATile.io.mmio.aw.bits.id
    FPGATile.io.mmio.b.bits.id := io.mmio_bid
    io.mmio_arid := FPGATile.io.mmio.ar.bits.id
    FPGATile.io.mmio.r.bits.id := io.mmio_rid

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

object fpga {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName

    (new chisel3.stage.ChiselStage).execute(
      Array("-td", "build/verilog/" + packageName, "-X", "verilog"),
      Seq(
        ChiselGeneratorAnnotation(() => new fpga_zjv)
      )
    )
  }
}
