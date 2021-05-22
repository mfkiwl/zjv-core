package tile

import chisel3._
import chisel3.stage._
import tile.common.control._

class DiffTestIO extends Bundle with phvntomParams {
  val streqs   = Output(Vec(10, UInt(xlen.W)))
  val regs     = Output(Vec(regNum/2, UInt(xlen.W)))
  val pc       = Output(UInt(xlen.W))
  val inst     = Output(UInt(xlen.W))
  val valid    = Output(Bool())
  val csr_cmd  = Output(UInt(ControlConst.wenBits.W))
  val tick     = Output(Bool())
  val int      = Output(Bool())
  val mcycle   = Output(UInt(xlen.W))
  val mstatus  = Output(UInt(xlen.W))
  val priv     = Output(UInt(2.W))
  val mepc     = Output(UInt(xlen.W))
  val mtval    = Output(UInt(xlen.W))
  val mcause   = Output(UInt(xlen.W))
  val sstatus  = Output(UInt(xlen.W))
  val sepc     = Output(UInt(xlen.W))
  val stval    = Output(UInt(xlen.W))
  val scause   = Output(UInt(xlen.W))
  val stvec    = Output(UInt(xlen.W))
  val mtvec    = Output(UInt(xlen.W))
  val mideleg  = Output(UInt(xlen.W))
  val medeleg  = Output(UInt(xlen.W))
  val mip      = Output(UInt(xlen.W))
  val mie      = Output(UInt(xlen.W))
  val sip      = Output(UInt(xlen.W))
  val sie      = Output(UInt(xlen.W))
  val uartirq  = Output(Bool())
  val plicmeip = Output(Bool())
  val plicseip = Output(Bool())
  val plicip   = Output(Vec(32, Bool()))
  val plicie   = Output(UInt(32.W))
  val plicprio = Output(UInt(32.W))
  val plicthrs = Output(UInt(32.W))
  val plicclaim = Output(UInt(32.W))
  val alu_val  = Output(UInt(xlen.W))
  val is_mem   = Output(Bool())
}

class TopIO extends Bundle with phvntomParams {
  // Difftest
  val difftest = new DiffTestIO
  val poweroff = Output(UInt(xlen.W))

}

class Top extends Module with phvntomParams {
  val io = IO(new TopIO)

  val tile = Module(new Tile)

  val difftest = WireInit(0.U.asTypeOf(new DiffTestIO))
  difftest.streqs  := tile.io.streqs
  difftest.regs    := tile.io.regs
  difftest.pc      := tile.io.dtest_pc
  difftest.inst    := tile.io.dtest_inst
  difftest.valid   := tile.io.dtest_wbvalid
  difftest.csr_cmd := 0.U
  difftest.int     := tile.io.dtest_int
  difftest.mcycle  := tile.io.mcycler 
  difftest.mstatus := tile.io.mstatusr
  difftest.priv    := tile.io.current_p

  difftest.mepc    := tile.io.mepcr
  difftest.mtval   := tile.io.mtvalr
  difftest.mcause  := tile.io.mcauser
  difftest.sstatus := tile.io.sstatusr
  difftest.sepc    := tile.io.sepcr
  difftest.stval   := tile.io.stvalr
  difftest.scause  := tile.io.scauser
  difftest.stvec   := tile.io.stvecr
  difftest.mtvec   := tile.io.mtvecr
  difftest.mideleg := tile.io.midelegr
  difftest.medeleg := tile.io.medelegr
  difftest.mip     := tile.io.mipr
  difftest.mie     := tile.io.mier
  difftest.sip     := tile.io.sipr
  difftest.sie     := tile.io.sier

  val poweroff = tile.io.poweroff

  difftest.uartirq  := tile.io.irq
  difftest.plicmeip := tile.io.meip
  difftest.plicseip := tile.io.seip

  difftest.plicip := tile.io.plicip
  difftest.plicie := tile.io.plicie
  difftest.plicprio := tile.io.plicprio
  difftest.plicthrs := tile.io.plicthrs
  difftest.plicclaim := tile.io.plicclaim

  difftest.alu_val := tile.io.dtest_alu
  difftest.is_mem  := tile.io.dtest_mem

  io.difftest := difftest
  io.poweroff := poweroff
}

object elaborate {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName

    if (args.isEmpty)
      (new chisel3.stage.ChiselStage).execute(
        Array("-td", "build/verilog/"+packageName, "-X", "verilog"),
        Seq(ChiselGeneratorAnnotation(() => new Top)))
    else
      (new chisel3.stage.ChiselStage).execute(args,
        Seq(ChiselGeneratorAnnotation(() => new Top)))
  }
}