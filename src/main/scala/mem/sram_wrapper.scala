package mem

import chisel3._
import chisel3.util._
import rv64_nstage.core._

class SRAMWrapperIO(val depth: Int = 256, val dataWidth: Int = 64) extends Bundle with phvntomParams {
  val depthLength = log2Ceil(depth)
  val AA = Input(UInt(depthLength.W))
  val AB = Input(UInt(depthLength.W))
  val DB = Input(UInt(dataWidth.W))
  val CENA = Input(Bool())
  val CENB = Input(Bool())
  val CLKA = Input(Clock())
  val CLKB = Input(Clock())
  val QA = Output(UInt(dataWidth.W))
}

class S011HD2P_X128Y2D53_Wrapper(depth: Int, width: Int) extends Module with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))

  val aar = RegInit(UInt(io.depthLength.W), 0.U)
  aar := io.AA
  val abr = RegInit(UInt(io.depthLength.W), 0.U)
  abr := io.AB
  val dbr = RegInit(UInt(io.dataWidth.W), 0.U)
  dbr := io.DB
  val cenar = RegInit(Bool(), true.B)
  cenar := io.CENA
  val cenbr = RegInit(Bool(), true.B)
  cenbr := io.CENB

  val mem = Module(new S011HD2P_X128Y2D53(depth, width))

  mem.io.CLKA := (~(clock.asBool)).asClock
  mem.io.CLKB := (~(clock.asBool)).asClock
  mem.io.AA := aar
  mem.io.AB := abr
  mem.io.DB := dbr
  mem.io.CENA := cenar
  mem.io.CENB := cenbr

  io.QA := mem.io.QA
}

class S011HD2P_X128Y2D54_Wrapper(depth: Int, width: Int) extends Module with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))

  val aar = RegInit(UInt(io.depthLength.W), 0.U)
  aar := io.AA
  val abr = RegInit(UInt(io.depthLength.W), 0.U)
  abr := io.AB
  val dbr = RegInit(UInt(io.dataWidth.W), 0.U)
  dbr := io.DB
  val cenar = RegInit(Bool(), true.B)
  cenar := io.CENA
  val cenbr = RegInit(Bool(), true.B)
  cenbr := io.CENB

  val mem = Module(new S011HD2P_X128Y2D54(depth, width))

  mem.io.CLKA := (~(clock.asBool)).asClock
  mem.io.CLKB := (~(clock.asBool)).asClock
  mem.io.AA := aar
  mem.io.AB := abr
  mem.io.DB := dbr
  mem.io.CENA := cenar
  mem.io.CENB := cenbr

  io.QA := mem.io.QA
}

class S011HD2P_X128Y2D64_Wrapper(depth: Int, width: Int) extends Module with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))

  val aar = RegInit(UInt(io.depthLength.W), 0.U)
  aar := io.AA
  val abr = RegInit(UInt(io.depthLength.W), 0.U)
  abr := io.AB
  val dbr = RegInit(UInt(io.dataWidth.W), 0.U)
  dbr := io.DB
  val cenar = RegInit(Bool(), true.B)
  cenar := io.CENA
  val cenbr = RegInit(Bool(), true.B)
  cenbr := io.CENB

  val mem = Module(new S011HD2P_X128Y2D64(depth, width))

  mem.io.CLKA := (~(clock.asBool)).asClock
  mem.io.CLKB := (~(clock.asBool)).asClock
  mem.io.AA := aar
  mem.io.AB := abr
  mem.io.DB := dbr
  mem.io.CENA := cenar
  mem.io.CENB := cenbr

  io.QA := mem.io.QA
}

class S011HD2P_X128Y2D53(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))
}

class S011HD2P_X128Y2D54(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))
}

class S011HD2P_X128Y2D64(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMWrapperIO(depth, width))
}
