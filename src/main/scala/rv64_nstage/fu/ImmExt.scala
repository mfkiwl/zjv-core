package rv64_nstage.fu

import chisel3._
import chisel3.util._
import rv64_nstage.control.ControlConst._
import rv64_nstage.core.phvntomParams

class ImmExtIO extends Bundle with phvntomParams {
  val inst = Input(UInt(xlen.W))
  val instType = Input(UInt(instBits.W))
  val out = Output(UInt(xlen.W))
}

class ImmExt extends Module with phvntomParams {
  val io = IO(new ImmExtIO)

  // unsigned
  val IImm = Cat(Fill(20, io.inst(31)), io.inst(31, 20))
  val SImm = Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val BImm = Cat(
    Fill(19, io.inst(31)),
    io.inst(31),
    io.inst(7),
    io.inst(30, 25),
    io.inst(11, 8),
    0.U
  )
  val UImm = Cat(io.inst(31, 12), Fill(12, 0.U))
  val JImm = Cat(
    Fill(11, io.inst(31)),
    io.inst(31),
    io.inst(19, 12),
    io.inst(20),
    io.inst(30, 21),
    0.U
  )
  val ZImm = Cat(Fill(27, 0.U), io.inst(19, 15))
  // Compressed
  val CI4Imm = Cat(Fill(24, 0.U), io.inst(3, 2), io.inst(12), io.inst(6, 4), Fill(2, 0.U))
  val CI8Imm = Cat(Fill(23, 0.U), io.inst(4, 2), io.inst(12), io.inst(6, 5), Fill(3, 0.U))

  val imm_32 = MuxLookup(
    io.instType,
    "hdeadbeef".U,
    Seq(
      IType -> IImm,
      SType -> SImm,
      BType -> BImm,
      UType -> UImm,
      JType -> JImm,
      ZType -> ZImm,
      CI4Type -> CI4Imm,
      CI8Type -> CI8Imm
    )
  )

  io.out := Cat(Fill(32, imm_32(31)), imm_32)
}