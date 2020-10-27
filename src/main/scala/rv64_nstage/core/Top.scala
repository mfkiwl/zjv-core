package rv64_nstage.core

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import common._
import rv64_nstage.control.ControlConst

class DiffTestIO extends Bundle with phvntomParams {
  val regs     = Output(Vec(regNum, UInt(xlen.W)))
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
  BoringUtils.addSink(difftest.regs,    "difftestRegs")
  BoringUtils.addSink(difftest.pc,      "difftestPC")
  BoringUtils.addSink(difftest.inst,    "difftestInst")
  BoringUtils.addSink(difftest.valid,   "difftestValid")
  BoringUtils.addSink(difftest.csr_cmd, "difftestCSRCmd")
  BoringUtils.addSink(difftest.int,     "difftestInt")
  BoringUtils.addSink(difftest.mcycle,  "difftestmcycler")
  BoringUtils.addSink(difftest.mstatus, "difftestmstatusr")
  BoringUtils.addSink(difftest.priv,    "difftestprivilege")

  BoringUtils.addSink(difftest.mepc,    "difftestmepcr")
  BoringUtils.addSink(difftest.mtval,   "difftestmtvalr")
  BoringUtils.addSink(difftest.mcause,  "difftestmcauser")
  BoringUtils.addSink(difftest.sstatus, "difftestsstatusr")
  BoringUtils.addSink(difftest.sepc,    "difftestsepcr")
  BoringUtils.addSink(difftest.stval,   "diffteststvalr")
  BoringUtils.addSink(difftest.scause,  "difftestscauser")
  BoringUtils.addSink(difftest.stvec,   "diffteststvecr")
  BoringUtils.addSink(difftest.mtvec,   "difftestmtvecr")
  BoringUtils.addSink(difftest.mideleg, "difftestmidelegr")
  BoringUtils.addSink(difftest.medeleg, "difftestmedelegr")
  BoringUtils.addSink(difftest.mip,     "difftestmipr")
  BoringUtils.addSink(difftest.mie,     "difftestmier")
  BoringUtils.addSink(difftest.sip,     "difftestsipr")
  BoringUtils.addSink(difftest.sie,     "difftestsier")

  val poweroff = WireInit(0.U(xlen.W))
  BoringUtils.addSink(poweroff, "difftestpoweroff")

  BoringUtils.addSink(difftest.uartirq,  "difftestuartirq")
  BoringUtils.addSink(difftest.plicmeip, "difftestplicmeip")
  BoringUtils.addSink(difftest.plicseip, "difftestplicseip")

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