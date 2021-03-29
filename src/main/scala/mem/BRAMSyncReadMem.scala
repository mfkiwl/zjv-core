package mem

import chisel3._
import chisel3.util._

class BRAMWrapperIO(width: Int = 128, depth: Int = 16) extends Bundle {
  val clk = Input(Clock())
  val rst = Input(Reset())
  val wea = Input(Bool())
  val web = Input(Bool())
  val ena = Input(Bool())
  val enb = Input(Bool())
  val addra = Input(UInt(log2Ceil(depth).W))
  val addrb = Input(UInt(log2Ceil(depth).W))
  val dina = Input(UInt(width.W))
  val dinb = Input(UInt(width.W))
  val douta = Output(UInt(width.W))
  val doutb = Output(UInt(width.W))
}

class dual_port_ram(DATA_WIDTH: Int, DEPTH: Int, LATENCY: Int = 1) extends BlackBox(Map("DATA_WIDTH" -> DATA_WIDTH,
                                                                                        "DEPTH" -> DEPTH,
                                                                                        "LATENCY" -> LATENCY)) {
  val io = IO(new BRAMWrapperIO(DATA_WIDTH, DEPTH))
}

class BRAMSyncReadMemIO(DATA_WIDTH: Int, DEPTH: Int) extends Bundle {
  val wea   = Input(Bool())
  val web   = Input(Bool())
  val addra = Input(UInt(log2Ceil(DEPTH).W))
  val addrb = Input(UInt(log2Ceil(DEPTH).W))
  val dina  = Input(UInt(DATA_WIDTH.W))
  val dinb  = Input(UInt(DATA_WIDTH.W))
  val douta = Output(UInt(DATA_WIDTH.W))
  val doutb = Output(UInt(DATA_WIDTH.W))
}

class BRAMSyncReadMem(DEPTH: Int, DATA_WIDTH: Int, LATENCY: Int = 1) extends Module {
  val dpr = Module(new dual_port_ram(DATA_WIDTH, DEPTH, LATENCY))
  val io = IO(new BRAMSyncReadMemIO(DATA_WIDTH, DEPTH))

  dpr.io.clk    := clock
  dpr.io.rst    := reset
  dpr.io.wea    := io.wea
  dpr.io.web    := io.web
  dpr.io.ena    := true.B
  dpr.io.enb    := true.B
  dpr.io.addra  := io.addra
  dpr.io.addrb  := io.addrb
  dpr.io.dina   := io.dina
  dpr.io.dinb   := io.dinb
  io.douta      := dpr.io.douta
  io.doutb      := dpr.io.doutb
}
