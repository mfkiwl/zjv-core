package mem

import chisel3._
import chisel3.util._
import rv64_nstage.core._

class SRAMWrapperIO(depth: Int, width: Int) extends Bundle with phvntomParams {
  val aa = Input(UInt(depth.W))
  val ab = Input(UInt(depth.W))
  val db = Input(UInt(width.W))
  val bwenb = Input(UInt(width.W))
  val cena = Input(Bool())
  val cenb = Input(Bool())
  val clka = Input(Bool())
  val clkb = Input(Bool())
  val qa = Output(UInt(width.W))
}

class SRAMTrueWrapper(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))
}

class SRAMWrapper(depth: Int, width: Int) extends Module with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))
  val mem = Module(new SRAMTrueWrapper(depth, width))
  mem.io.clka := clock
  mem.io.clkb := clock

  def read(addr: UInt, en: Bool) = {
    mem.io.aa := addr
    mem.io.cena := ~en
    mem.io.qa
  }
  
  def wrire(addr: UInt, data: UInt, en: Bool) = {
    mem.io.ab := addr
    mem.io.db := data
    mem.io.bwenb := Fill(width, 1.U(1.W))
    mem.io.cenb := ~en
  }
}
