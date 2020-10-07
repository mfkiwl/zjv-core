package rv64_3stage

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.util.experimental.BoringUtils


import common._

class DiffTestIO extends Bundle with phvntomParams {
  val regs = Output(Vec(regNum, UInt(xlen.W)))
  val pc   = Output(UInt(xlen.W))
  val inst = Output(UInt(xlen.W))
  val valid = Output(Bool())
  val csr_cmd = Output(UInt(ControlConst.wenBits.W))
  val tick = Output(Bool())
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
  BoringUtils.addSink(difftest.tick,    "difftestTick")

  val poweroff = WireInit(0.U(xlen.W))
  BoringUtils.addSink(poweroff,         "poweroff")

  val mtip = WireInit(false.B)
  val msip = WireInit(false.B)
  BoringUtils.addSink(mtip, "mtip")
  BoringUtils.addSink(msip, "msip")

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