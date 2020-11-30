package mem

import chisel3._
import chisel3.util._
import rv64_nstage.core._

class SRAMDPWrapperIO(val depth: Int = 32, val dataWidth: Int = 64) extends Bundle with phvntomParams {
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

class SRAMSPWrapperIO(val depth: Int = 32, val dataWidth: Int = 64) extends Bundle with phvntomParams {
  val depthLength = log2Ceil(depth)
  val A = Input(UInt(depthLength.W))
  val D = Input(UInt(dataWidth.W))
  val WEN = Input(Bool())
  val CEN = Input(Bool())
  val CLK = Input(Clock())
  val Q = Output(UInt(dataWidth.W))
}

class S011HD2P_X32Y2D56_Wrapper(depth: Int, width: Int) extends Module with phvntomParams {
  val io = IO(new SRAMDPWrapperIO(depth, width))

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

  val mem = Module(new S011HD2P_X32Y2D56(depth, width))

//  mem.io.CLKA := (~(clock.asBool)).asClock
//  mem.io.CLKB := (~(clock.asBool)).asClock
//  mem.io.AA := aar
//  mem.io.AB := abr
//  mem.io.DB := dbr
//  mem.io.CENA := cenar
//  mem.io.CENB := cenbr

  mem.io.CLKA := clock
  mem.io.CLKB := clock
  mem.io.AA := io.AA
  mem.io.AB := io.AB
  mem.io.DB := io.DB
  mem.io.CENA := io.CENA
  mem.io.CENB := io.CENB

  io.QA := mem.io.QA
}

class S011HD2P_X32Y2D64_Wrapper(depth: Int, width: Int) extends Module with phvntomParams {
  val io = IO(new SRAMDPWrapperIO(depth, width))

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

  val mem = Module(new S011HD2P_X32Y2D64(depth, width))

//  mem.io.CLKA := (~(clock.asBool)).asClock
//  mem.io.CLKB := (~(clock.asBool)).asClock
//  mem.io.AA := aar
//  mem.io.AB := abr
//  mem.io.DB := dbr
//  mem.io.CENA := cenar
//  mem.io.CENB := cenbr

  mem.io.CLKA := clock
  mem.io.CLKB := clock
  mem.io.AA := io.AA
  mem.io.AB := io.AB
  mem.io.DB := io.DB
  mem.io.CENA := io.CENA
  mem.io.CENB := io.CENB

  io.QA := mem.io.QA
}

class S011HD1P_X64Y2D128_Wrapper(depth: Int, width: Int) extends Module with phvntomParams {
  val io = IO(new SRAMSPWrapperIO(depth, width))

  val mem = Module(new S011HD1P_X64Y2D128(depth, width))

  val ar = RegInit(UInt(io.depthLength.W), 0.U)
  ar := io.A
  val dr = RegInit(UInt(io.dataWidth.W), 0.U)
  dr := io.D
  val nwenr = RegInit(Bool(), true.B)
  nwenr := io.WEN
  val cenr = RegInit(Bool(), true.B)
  cenr := io.CEN

//  mem.io.A := ar
//  mem.io.D := dr
//  mem.io.WEN := nwenr
//  mem.io.CEN := cenr
//  mem.io.CLK := (~(clock.asBool)).asClock

  mem.io.A := io.A
  mem.io.D := io.D
  mem.io.WEN := io.WEN
  mem.io.CEN := io.CEN
  mem.io.CLK := clock

  io.Q := mem.io.Q
}

class S011HD1P_X64Y2D54_Wrapper(depth: Int, width: Int) extends Module with phvntomParams {
  val io = IO(new SRAMSPWrapperIO(depth, width))

  val mem = Module(new S011HD1P_X64Y2D54(depth, width))

  val ar = RegInit(UInt(io.depthLength.W), 0.U)
  ar := io.A
  val dr = RegInit(UInt(io.dataWidth.W), 0.U)
  dr := io.D
  val nwenr = RegInit(Bool(), true.B)
  nwenr := io.WEN
  val cenr = RegInit(Bool(), true.B)
  cenr := io.CEN

//  mem.io.A := ar
//  mem.io.D := dr
//  mem.io.WEN := nwenr
//  mem.io.CEN := cenr
//  mem.io.CLK := (~(clock.asBool)).asClock

  mem.io.A := io.A
  mem.io.D := io.D
  mem.io.WEN := io.WEN
  mem.io.CEN := io.CEN
  mem.io.CLK := clock

  io.Q := mem.io.Q
}

class S011HD2P_X32Y2D56(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMDPWrapperIO(depth, width))
}

class S011HD2P_X32Y2D64(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMDPWrapperIO(depth, width))
}

class S011HD1P_X64Y2D54(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMSPWrapperIO(depth, width))
}

class S011HD1P_X64Y2D128(depth: Int, width: Int)
    extends BlackBox
    with phvntomParams {
  val io = IO(new SRAMSPWrapperIO(depth, width))
}