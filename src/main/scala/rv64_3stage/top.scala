package rv64_3stage

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import common._

class DiffTestIO extends Bundle with phvntomParams {
  val regs = Output(Vec(regNum, UInt(xlen.W)))
  val pc   = Output(UInt(xlen.W))
  val inst = Output(UInt(xlen.W))

}

class TopIO extends Bundle with phvntomParams {
  // Difftest
  val difftest = new DiffTestIO


}

class Top extends Module with phvntomParams {
  val io = IO(new TopIO)

  val tile = Module(new Tile)

  val difftest = WireInit(0.U.asTypeOf(new DiffTestIO))
  BoringUtils.addSink(difftest.regs, "difftestRegs")
  BoringUtils.addSink(difftest.pc, "difftestPc")
  BoringUtils.addSink(difftest.inst, "difftestInst")

  io.difftest := difftest

}

object elaborate {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      chisel3.Driver.execute(Array("--target-dir", "build/verilog/rv64_3stage"), () => new Top)
    else
      chisel3.Driver.execute(args, () => new Top)
  }
}