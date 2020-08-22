package core

import chisel3._
import chisel3.util._

class ALU extends Module {

//  [R-Type]
//    31__________25_24______20_19______15_14__12_11______7_6__________0
//    |_____________|__________|__________|______|_________|___________|
//        funct7        rs2        rs1     funct3    rd        opcode
// add:  0000000                             0                 0110011
// sub:  0100000                             0
// sll:  0000000                             1
// slt:  0000000                             2
// sltu: 0000000                             3
// xor:  0000000                             4
// srl:  0000000                             5
// sra:  0100000                             5
// or:   0000000                             6
// and:  0000000                             7
//
//  [I-Type]
//    31____________________20_19______15_14__12_11______7_6__________0
//    |_______________________|__________|______|_________|___________|
//            imm[11:0]           rs1     funct3    rd        opcode
// addi:                                    0                0010011
// slli:    0000000  shamt                  1
// slti:                                    2
// sltiu:                                   3
// xori:                                    4
// srli:    0000000  shamt                  5
// srai:    0100000  shamt                  5
// ori:                                     6
// andi:                                    7

  object OP {
    // funct7
    def FUNCT7_ZERO = "b0000000".U
    def FUNCT7_32 = "b0100000".U

    // funct3
    def FUNCT3_ADD = "b000".U
    def FUNCT3_SUB = FUNCT3_ADD
    def FUNCT3_SLL = "b001".U
    def FUNCT3_SLT = "b010".U
    def FUNCT3_SLTU = "b011".U
    def FUNCT3_XOR = "b100".U
    def FUNCT3_SRL = "b101".U
    def FUNCT3_SRA = FUNCT3_SRL
    def FUNCT3_OR = "b110".U
    def FUNCT3_AND = "b111".U

    // ALU operation
    def ADD = Cat(FUNCT7_ZERO, FUNCT3_ADD)
    def SUB = Cat(FUNCT7_32, FUNCT3_SUB)
    def SLL = Cat(FUNCT7_ZERO, FUNCT3_SLL)
    def SLT = Cat(FUNCT7_ZERO, FUNCT3_SLT)
    def SLTU = Cat(FUNCT7_ZERO, FUNCT3_SLTU)
    def XOR = Cat(FUNCT7_ZERO, FUNCT3_XOR)
    def SRL = Cat(FUNCT7_ZERO, FUNCT3_SRL)
    def SRA = Cat(FUNCT7_32, FUNCT3_SRA)
    def OR = Cat(FUNCT7_ZERO, FUNCT3_OR)
    def AND = Cat(FUNCT7_ZERO, FUNCT3_AND)
  }


  val io = IO(new Bundle() {
    // Standard
    val funct7 = Input(UInt(7.W))
    val funct3 = Input(UInt(3.W))
    val rs1 = Input(UInt(64.W))
    val rs2 = Input(UInt(64.W))
    val rd = Output(UInt(64.W))

    // Custom
//    val zero = Output(UInt(1.W))
//    val overflow = Output(UInt(1.W))
  })

  val res = Wire(UInt(65.W))
  io.rd := res
  val op = Cat(io.funct7, io.funct3)
  when (op === OP.ADD) {
    res := io.rs1 + io.rs2
  } .elsewhen (op === OP.SUB) {
    res := io.rs1 - io.rs2
  } .elsewhen(op === OP.SLL) {
    res := io.rs1 << io.rs2(4,0)
  } .elsewhen(op === OP.SLTU) {
    res := Cat(0.U(31.W), (io.rs1 < io.rs2))
  } .elsewhen(op === OP.SLT) {
    res := Cat(0.U(31.W), (io.rs1.asSInt < io.rs2.asSInt))
  } .elsewhen(op === OP.XOR) {
    res := io.rs1 ^ io.rs2
  } .elsewhen(op === OP.SRL) {
    res := io.rs1 >> io.rs2
  } .elsewhen(op === OP.SRA) {
    res := (io.rs1.asSInt >> io.rs2).asUInt
  } .elsewhen(op === OP.OR) {
    res := io.rs1 | io.rs2
  } .elsewhen(op === OP.AND) {
    res := io.rs1 & io.rs2
  } .otherwise {
    res := "hdeadbeef".U
  }

}

object ALUGen extends App {
  Driver.execute(args, () => new ALU)
}