package rv64_3stage

import chisel3._
import chisel3.util._

import common._

class ALUIO() extends Bundle with phvntomParams {
  val rs1 = Input(UInt(xlen.W))
  val rs2 = Input(UInt(xlen.W))

  val funct3 = Input(UInt(3.W))
  val funct7 = Input(UInt(7.W))

  val rd = Output(UInt(xlen.W))

  val zero = Output(Bool())
}

object ALU {
  // funct7
  val FUNCT7_ZERO = "b0000000".U(7.W)
  val FUNCT7_32 =   "b0100000".U(7.W)

  // funct3
  val FUNCT3_ADD = "b000".U(3.W)
  val FUNCT3_SUB = FUNCT3_ADD
  val FUNCT3_SLL = "b001".U(3.W)
  val FUNCT3_SLT = "b010".U(3.W)
  val FUNCT3_SLTU = "b011".U(3.W)
  val FUNCT3_XOR = "b100".U(3.W)
  val FUNCT3_SRL = "b101".U(3.W)
  val FUNCT3_SRA = FUNCT3_SRL
  val FUNCT3_OR = "b110".U(3.W)
  val FUNCT3_AND = "b111".U(3.W)

  // ALU operation
  val ALU_ADD = Cat(FUNCT7_ZERO, FUNCT3_ADD)
  val ALU_SUB = Cat(FUNCT7_32, FUNCT3_SUB)
  val ALU_SLL = Cat(FUNCT7_ZERO, FUNCT3_SLL)
  val ALU_SLT = Cat(FUNCT7_ZERO, FUNCT3_SLT)
  val ALU_SLTU = Cat(FUNCT7_ZERO, FUNCT3_SLTU)
  val ALU_XOR = Cat(FUNCT7_ZERO, FUNCT3_XOR)
  val ALU_SRL = Cat(FUNCT7_ZERO, FUNCT3_SRL)
  val ALU_SRA = Cat(FUNCT7_32, FUNCT3_SRA)
  val ALU_OR = Cat(FUNCT7_ZERO, FUNCT3_OR)
  val ALU_AND = Cat(FUNCT7_ZERO, FUNCT3_AND)
}

import ALU._

class ALU extends Module with phvntomParams{
  val io = IO(new ALUIO)

  val shamt = io.rs2(bitWidth-1, 0)

  val op = Cat(io.funct7, io.funct3)
  io.rd := MuxLookup(op, "hdeadbeef".U, Seq(
    ALU_ADD -> (io.rs1 + io.rs2),
    ALU_SUB -> (io.rs1 - io.rs2),
    ALU_SLL -> (io.rs1 << shamt),
    ALU_SLT -> (io.rs1.asSInt < io.rs2.asSInt),
    ALU_SLTU-> (io.rs1 < io.rs2),
    ALU_XOR -> (io.rs1 ^ io.rs2),
    ALU_SRL -> (io.rs1 >> shamt),
    ALU_SRA -> (io.rs1.asSInt >> shamt).asUInt,
    ALU_OR  -> (io.rs1 | io.rs2),
    ALU_AND -> (io.rs1 & io.rs2),
  ))

  io.zero := ~ io.rd.orR

//  printf(p"${io.funct7} ${io.funct3} rs1: ${Hexadecimal(io.rs1)} rs2: ${Hexadecimal(io.rs2)} rd: ${Hexadecimal(io.rd)} zero: ${Hexadecimal(io.zero)}\n")

}
