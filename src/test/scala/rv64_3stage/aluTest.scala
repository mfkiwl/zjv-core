package rv64_3stage

import chisel3._
import chisel3.util._
import chisel3.iotesters._

import scala.util.Random

import ALU._

class ALUTester(c: ALU) extends PeekPokeTester(c)  {

  def alu_add:(Long, Long) => Long = (rs1, rs2) => { rs1 + rs2 }
  def alu_sub:(Long, Long) => Long = (rs1, rs2) => { rs1 - rs2 }
  def alu_sll:(Long, Long) => Long = (rs1, rs2) => { rs1 << (rs2 & 0x3f) }
  def alu_slt:(Long, Long) => Long = (rs1, rs2) => { (rs1 < rs2).toLong }
  def alu_sltu:(Long, Long) => Long = (rs1, rs2) => { (longToUnsignedBigInt(rs1) < longToUnsignedBigInt(rs2)).toLong }
  def alu_xor:(Long, Long) => Long = (rs1, rs2) => { rs1 ^ rs2 }
  def alu_srl:(Long, Long) => Long = (rs1, rs2) => { (longToUnsignedBigInt(rs1) >>  (rs2 & 0x3f)).toLong }
  def alu_sra:(Long, Long) => Long = (rs1, rs2) => { rs1 >> (rs2 & 0x3f) }
  def alu_or:(Long, Long) => Long = (rs1, rs2) => { rs1 | rs2 }
  def alu_and:(Long, Long) => Long = (rs1, rs2) => { rs1 & rs2 }

  val funct3  = List(FUNCT3_ADD,  FUNCT3_SUB, FUNCT3_SLL,  FUNCT3_SLT,  FUNCT3_SLTU, FUNCT3_XOR,  FUNCT3_SRL,  FUNCT3_SRA, FUNCT3_OR,   FUNCT3_AND)
  val funct7  = List(FUNCT7_ZERO, FUNCT7_32,  FUNCT7_ZERO, FUNCT7_ZERO, FUNCT7_ZERO, FUNCT7_ZERO, FUNCT7_ZERO, FUNCT7_32,  FUNCT7_ZERO, FUNCT7_ZERO)
  val alu_op  = List(alu_add,     alu_sub,    alu_sll,     alu_slt,     alu_sltu,    alu_xor,     alu_srl,     alu_sra,    alu_or,      alu_and)
  val op_name = List("ADD",       "SUB",      "SLL",       "SLT",       "SLTU",      "XOR",       "SRL",       "SRA",      "OR",        "AND")


  def alu_sim(rs1: Long, rs2: Long)  = {
    for (i <- 0 until alu_op.size) {
      val rd_long = alu_op(i)(rs1, rs2)
      val rd = longToUnsignedBigInt(rd_long)
      poke(c.io.funct7, funct7(i))
      poke(c.io.funct3, funct3(i))
      poke(c.io.rs1, rs1)
      poke(c.io.rs2, rs2)

      step(1)

//      println(f"[${op_name(i)}] ${funct7(i)} ${funct3(i)} rs1: ${rs1}%16x rs2: ${rs2}%16x rd: $rd%16x zero ${(rd==0).toInt}")
      expect(c.io.rd, rd )
      expect(c.io.zero, rd==0 )
    }
  }

  val rs1 = 0 until 1024 map  { i => Random.nextLong()}
  val rs2 = 0 until 1024 map  { i => if (i % 10 == 0) -rs1(i) else Random.nextLong() }

  for (i <- 0 until rs1.length)
    alu_sim(rs1(i), rs2(i))

}

class ALUTest extends ChiselFlatSpec {
    "ALU Test" should "pass" in {
      iotesters.Driver.execute(Array(), () => new ALU) {
        c => new ALUTester(c)
      }
    }
}
