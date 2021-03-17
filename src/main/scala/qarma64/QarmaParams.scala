package qarma64

import chisel3._
import chisel3.util._

trait QarmaParams {
  val debug = false
  val ppldbg = false
  // TODO support super-scalar in both scheduler and SRAM
  val superscalar = false
  val ds = false

  val n = 64
  val m = n / 16
  val sbox_number = 2

  val check_box = Array(
    "hc003b93999b33765".U,
    "h270a787275c48d10".U,
    "h5c06a7501b63b2fd".U
  )
  val alpha = "hC0AC29B7C97C50DD".U
  val c = VecInit(
    "h0000000000000000".U, "h13198A2E03707344".U, "hA4093822299F31D0".U, "h082EFA98EC4E6C89".U,
    "h452821E638D01377".U, "hBE5466CF34E90C6C".U, "h3F84D5B5B5470917".U, "h9216D5D98979FB1B".U
  )
  val sbox = Array(
    VecInit(0.U(4.W), 14.U(4.W), 2.U(4.W), 10.U(4.W), 9.U(4.W), 15.U(4.W), 8.U(4.W), 11.U(4.W), 6.U(4.W), 4.U(4.W), 3.U(4.W), 7.U(4.W), 13.U(4.W), 12.U(4.W), 1.U(4.W), 5.U(4.W)),
    VecInit(10.U(4.W), 13.U(4.W), 14.U(4.W), 6.U(4.W), 15.U(4.W), 7.U(4.W), 3.U(4.W), 5.U(4.W), 9.U(4.W), 8.U(4.W), 0.U(4.W), 12.U(4.W), 11.U(4.W), 1.U(4.W), 2.U(4.W), 4.U(4.W)),
    VecInit(11.U(4.W), 6.U(4.W), 8.U(4.W), 15.U(4.W), 12.U(4.W), 0.U(4.W), 9.U(4.W), 14.U(4.W), 3.U(4.W), 7.U(4.W), 4.U(4.W), 5.U(4.W), 13.U(4.W), 2.U(4.W), 1.U(4.W), 10.U(4.W))
  )
  val sbox_inv = Array(
    VecInit(0.U, 14.U, 2.U, 10.U, 9.U, 15.U, 8.U, 11.U, 6.U, 4.U, 3.U, 7.U, 13.U, 12.U, 1.U, 5.U),
    VecInit(10.U, 13.U, 14.U, 6.U, 15.U, 7.U, 3.U, 5.U, 9.U, 8.U, 0.U, 12.U, 11.U, 1.U, 2.U, 4.U),
    VecInit(5.U, 14.U, 13.U, 8.U, 10.U, 11.U, 1.U, 9.U, 2.U, 6.U, 15.U, 0.U, 4.U, 12.U, 7.U, 3.U)
  )
  val t = Array(0, 11, 6, 13, 10, 1, 12, 7, 5, 14, 3, 8, 15, 4, 9, 2)
  val t_inv = Array(0, 5, 15, 10, 13, 8, 2, 7, 11, 14, 4, 1, 6, 3, 9, 12)
  val h = Array(6, 5, 14, 15, 0, 1, 2, 3, 7, 12, 13, 4, 8, 9, 10, 11)
  val h_inv = Array(4, 5, 6, 7, 11, 1, 0, 8, 12, 13, 14, 15, 9, 10, 2, 3)
  val M = Array(
    0, 1, 2, 1,
    1, 0, 1, 2,
    2, 1, 0, 1,
    1, 2, 1, 0
  )

  def lfsr_inv_operation(operand: UInt): UInt = {
    Cat(operand(2, 0), operand(0) ^ operand(3))
  }

  def log(round: Int, num1: UInt, num2: UInt): Unit = {
    if (debug) {
      printf("cp %d\tis=%x tk=%x\n", round.asUInt, num1, num2)
    }
  }

  def o_operation(operand: UInt): UInt = {
    Cat(operand(0), operand(operand.getWidth - 1, 1)) ^ (operand >> (n - 1).asUInt).asUInt
  }

  def lfsr_operation(operand: UInt): UInt = {
    Cat(operand(0) ^ operand(1), operand(3, 1))
  }

  val code_map_width = 2
  val code_map = Map("end" -> 0.U(code_map_width.W), "forward" -> 1.U(code_map_width.W),
    "reflect" -> 2.U(code_map_width.W), "backward" -> 3.U(code_map_width.W))
}

trait QarmaParamsIO extends MultiIOModule with QarmaParams {
  val input = IO(Flipped(Decoupled(new Bundle {
    val encrypt = Bool()
    val keyh = UInt(64.W)
    val keyl = UInt(64.W)
    val tweak = UInt(64.W)
    val text = UInt(64.W)
    val actual_round = UInt(3.W)
  })))
  val output = IO(Decoupled(new Bundle {
    val result = UInt(64.W)
  }))
}
