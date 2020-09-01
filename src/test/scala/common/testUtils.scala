package common

import chisel3._
import chisel3.util._
import rv64_3stage.ALU._


//class TestUtils {
//
//  val rnd = new scala.util.Random
//  def rand_fn7 = (rnd.nextInt(1 << 7)).U(7.W)
//  def rand_rs2 = (rnd.nextInt((1 << 5) - 1) + 1).U(5.W)
//  def rand_rs1 = (rnd.nextInt((1 << 5) - 1) + 1).U(5.W)
//  def rand_fn3 = (rnd.nextInt(1 << 3)).U(3.W)
//  def rand_rd  = (rnd.nextInt((1 << 5) - 1) + 1).U(5.W)
//  def rand_csr = csrRegs(rnd.nextInt(csrRegs.size-1)).U
//  def rand_inst = toBigInt(rnd.nextInt()).U
//  def rand_addr = toBigInt(rnd.nextInt()).U
//  def rand_data = toBigInt(rnd.nextInt()).U
//
//
//}
