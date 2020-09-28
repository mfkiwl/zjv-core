package rv64_3stage

import chisel3._
import chisel3.util._
import chisel3.iotesters._

import scala.util.Random

import ControlConst._

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
  def alu_cpa:(Long, Long) => Long = (rs1, rs2) => { rs1 }
  def alu_cpb:(Long, Long) => Long = (rs1, rs2) => { rs2 }
  def cut32:(Long) => Int = (rs) => { (rs & 0x00000000ffffffff).toInt }
  def alu_addiw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) + cut32(rs2)}
  def alu_slliw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) << (rs2 & 0x1f)}
  def alu_srliw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) >>> (rs2 & 0x1f) }
  def alu_sraiw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) >> (rs2 & 0x1f) }
  def alu_addw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) + cut32(rs2)}
  def alu_subw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) - cut32(rs2)}
  def alu_sllw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) << (rs2 & 0x1f)}
  def alu_srlw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) >>> (rs2 & 0x1f) }
  def alu_sraw:(Long, Long) => Long = (rs1, rs2) => { cut32(rs1) >> (rs2 & 0x1f) }

  val opType  = List(aluADD,  aluSUB,  aluSLL,  aluSLT,  aluSLTU,  aluXOR,  aluSRL,  aluSRA,  aluOR,  aluAND,  aluCPA,  aluCPB, aluADDIW, aluSLLIW, aluSRLIW, aluSRAIW, aluADDW , aluSUBW , aluSLLW , aluSRLW , aluSRAW)
  val alu_op  = List(alu_add, alu_sub, alu_sll, alu_slt, alu_sltu, alu_xor, alu_srl, alu_sra, alu_or, alu_and, alu_cpa, alu_cpb, alu_addiw, alu_slliw, alu_srliw, alu_sraiw, alu_addw , alu_subw, alu_sllw, alu_srlw, alu_sraw)
  val op_name = List("ADD", "SUB", "SLL", "SLT", "SLTU", "XOR", "SRL", "SRA", "OR", "AND", "COPYA", "COPYB", "ADDIW", "SLLIW", "SRLIW", "SRAIW", "ADDW", "SUBW", "SLLW", "SRLW", "SRAW")

  def alu_sim(rs1: Long, rs2: Long)  = {
    for (i <- 0 until alu_op.size) {
      val rd_long = alu_op(i)(rs1, rs2)
      val rd = longToUnsignedBigInt(rd_long)
      poke(c.io.opType, opType(i))
      poke(c.io.a, rs1)
      poke(c.io.b, rs2)

      step(1)

//      println(f"[${opType(i)} rs1: ${rs1}%16x rs2: ${rs2}%16x rd: $rd%16x zero ${(rd==0).toInt}")
      expect(c.io.out, rd)
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
