package rv64_nstage.fu

import chisel3._
import chisel3.util._
import rv64_nstage.control.ControlConst._
import rv64_nstage.core.phvntomParams

class ALUIO() extends Bundle with phvntomParams {
  val a = Input(UInt(xlen.W))
  val b = Input(UInt(xlen.W))
  val opType = Input(UInt(aluBits.W))
  val out = Output(UInt(xlen.W))
  val zero = Output(Bool())
}

class ALU extends Module with phvntomParams {
  val io = IO(new ALUIO)

  val shamt = io.b(bitWidth - 1, 0)
  def sign_ext32(a: UInt): UInt = { Cat(Fill(32, a(31)), a(31, 0)) }
  io.out := MuxLookup(
    io.opType,
    "hdeadbeef".U,
    Seq(
      aluADD -> (io.a + io.b),
      aluSUB -> (io.a - io.b),
      aluSLL -> (io.a << shamt),
      aluSLT -> (io.a.asSInt < io.b.asSInt),
      aluSLTU -> (io.a < io.b),
      aluXOR -> (io.a ^ io.b),
      aluSRL -> (io.a >> shamt),
      aluSRA -> (io.a.asSInt >> shamt).asUInt,
      aluOR -> (io.a | io.b),
      aluAND -> (io.a & io.b),
      aluCPA -> io.a,
      aluCPB -> io.b,
      aluADDW -> sign_ext32(io.a(31, 0) + io.b(31, 0)),
      aluSUBW -> sign_ext32(io.a(31, 0) - io.b(31, 0)),
      aluSLLW -> sign_ext32(io.a(31, 0) << shamt(4, 0)),
      aluSRLW -> sign_ext32(io.a(31, 0) >> shamt(4, 0)),
      aluSRAW -> sign_ext32((io.a(31, 0).asSInt >> shamt(4, 0)).asUInt)
    )
  )

  io.zero := ~io.out.orR
}

class WallaceMultiplierIO extends Bundle with phvntomParams {
  // Signals for Input
  val abs_a_i = Input(UInt(xlen.W))
  val abs_b_i = Input(UInt(xlen.W))
  // Signals for Output
  val data_o = Output(UInt((2 * xlen).W))
}

// The Pipelined Multiplier
class WallaceMultiplier extends Module with phvntomParams {
  val io = IO(new WallaceMultiplierIO)

  val stage_one_reg_num = (xlen + 3) / 4
  val stage_two_reg_num = (stage_one_reg_num + 3) / 4
  val stage_three_reg_num = (stage_two_reg_num + 3) / 4

  val abs_a = WireInit(UInt(xlen.W), 0.U)
  val abs_b = WireInit(UInt(xlen.W), 0.U)

  val temp_regs_stage_one = RegInit(VecInit(Seq.fill(stage_one_reg_num)(0.U((xlen + 4).W))))
  val temp_regs_stage_two = RegInit(VecInit(Seq.fill(stage_two_reg_num)(0.U((xlen + 4 + 12).W))))

  // Stage Zero
  abs_a := io.abs_a_i
  abs_b := io.abs_b_i

//  printf("BEGIN\n")
//  printf("In stage0 a %x, b %x\n", abs_a, abs_b)
  // Stage One
  for(i <- 0 to stage_one_reg_num - 1) {
    temp_regs_stage_one(i) := (
      0.U((xlen + 4).W) +
      Cat(Mux(abs_b((i << 2) + 3), abs_a, 0.U), 0.U(3.W)) +
      Cat(Mux(abs_b((i << 2) + 2), abs_a, 0.U), 0.U(2.W)) +
      Cat(Mux(abs_b((i << 2) + 1), abs_a, 0.U), 0.U(1.W)) +
      Mux(abs_b((i << 2) + 0), abs_a, 0.U)
    )
//    printf("In stage1(%x) %x\n", i.asUInt, temp_regs_stage_one(i))
  }

  // Stage Two
  for(i <- 0 to stage_two_reg_num - 1) {
    temp_regs_stage_two(i) := (
      0.U((xlen + 4 + 12).W) +
      Cat(temp_regs_stage_one((i << 2) + 3), 0.U(12.W)) +
      Cat(temp_regs_stage_one((i << 2) + 2), 0.U(8.W)) +
      Cat(temp_regs_stage_one((i << 2) + 1), 0.U(4.W)) +
      temp_regs_stage_one((i << 2) + 0)
    )
//    printf("In stage2(%x) %x\n", i.asUInt, temp_regs_stage_two(i))
  }

  // Stage Three
  if(xlen == 64) {
    io.data_o := (
      0.U((xlen + 4 + 12 + 48).W) +
      temp_regs_stage_two(0) +
      Cat(temp_regs_stage_two(1), 0.U(16.W)) +
      Cat(temp_regs_stage_two(2), 0.U(32.W)) +
      Cat(temp_regs_stage_two(3), 0.U(48.W))
    )
//    printf("data %x\n", io.data_o)
  } else {
    io.data_o := 0.U((xlen + 4 + 12 + 16).W) + (temp_regs_stage_two(0) << 0.U) + (temp_regs_stage_two(1) << 16.U)
  }

//  printf("END\n")
}

class MultiplierIO extends Bundle with phvntomParams {
  val start = Input(Bool())
  val a = Input(UInt(xlen.W))
  val b = Input(UInt(xlen.W))
  val op = Input(UInt(aluBits.W))
  val stall_req = Output(Bool())
  val mult_out = Output(UInt(xlen.W))
}

class Multiplier extends Module with phvntomParams {
  val io = IO(new MultiplierIO)

  def resemble_op(op: UInt, op_history: UInt): Bool = {
    (((op_history === aluMULHU || op_history === aluMULH || op_history === aluMULHSU) && op === aluMUL) ||
      (op_history === aluDIV && op === aluREM) ||
      (op_history === aluDIVU && op === aluREMU) ||
      (op_history === aluDIVUW && op === aluREMUW) ||
      (op_history === aluDIVW && op === aluREMW) ||
      (op === aluDIV && op_history === aluREM) ||
      (op === aluDIVU && op_history === aluREMU) ||
      (op === aluDIVUW && op_history === aluREMUW) ||
      (op === aluDIVW && op_history === aluREMW))
  }

  def check_history_same(a: UInt, b: UInt, op: UInt, ha: UInt, hb: UInt, hop: UInt): Bool = {
    a === ha && b === hb && resemble_op(op, hop)
  }

  val last_a = RegInit(UInt(xlen.W), 0.U)
  val last_b = RegInit(UInt(xlen.W), 0.U)
  val last_op = RegInit(UInt(aluBits.W), aluXXX)

  when(io.start) {
    last_a := io.a
    last_b := io.b
    last_op := io.op
  }

  val is_mult = io.op === aluMUL || io.op === aluMULH || io.op === aluMULHSU || io.op === aluMULHU || io.op === aluMULW
  val res = RegInit(UInt((2 * xlen).W), 0.U)
  val mult_cnt = RegInit(UInt(log2Ceil(xlen + 1).W), 0.U)
  val div_cnt = RegInit(UInt(log2Ceil(xlen + 1).W), 0.U)
  val sign_a = io.a(xlen - 1)
  val sign_wa = io.a(31)
  val abs_a = MuxLookup(io.op, Cat(Fill(32, 0.U), io.a(31, 0)),
    Seq(
      aluMUL -> io.a,
      aluMULH -> Mux(io.a(xlen - 1), ((~io.a) + 1.U), io.a),
      aluMULHSU -> Mux(io.a(xlen - 1), ((~io.a) + 1.U), io.a),
      aluMULHU -> io.a,
      aluMULW -> Cat(Fill(32, 0.U), io.a(31, 0)),
      aluDIV -> Mux(io.a(xlen - 1), ((~io.a) + 1.U), io.a),
      aluDIVU -> io.a,
      aluDIVW -> Cat(Fill(32, 0.U), Mux(io.a(31), ((~io.a) + 1.U)(31, 0), io.a(31, 0))),
      aluDIVUW -> Cat(Fill(32, 0.U), io.a(31, 0)),
      aluREM -> Mux(io.a(xlen - 1), ((~io.a) + 1.U), io.a),
      aluREMU -> io.a,
      aluREMW -> Cat(Fill(32, 0.U), Mux(io.a(31), ((~io.a) + 1.U)(31, 0), io.a(31, 0))),
      aluREMUW -> Cat(Fill(32, 0.U), io.a(31, 0))
    )
  )

//  printf("MUL a:%x, b:%x, out:%x, op:%x, start:%x, stall_req:%x, cnt:%x\n", io.a, io.b, io.mult_out, io.op, io.start, io.stall_req, mult_cnt)

  val sign_b = io.b(xlen - 1)
  val sign_wb = io.b(31)
  val abs_b = MuxLookup(io.op, Cat(Fill(32, 0.U), io.b(31, 0)),
    Seq(
      aluMUL -> io.b,
      aluMULH -> Mux(io.b(xlen - 1), ((~io.b) + 1.U), io.b),
      aluMULHSU -> io.b,
      aluMULHU -> io.b,
      aluMULW -> Cat(Fill(32, 0.U), io.b(31, 0)),
      aluDIV -> Mux(io.b(xlen - 1), ((~io.b) + 1.U), io.b),
      aluDIVU -> io.b,
      aluDIVW -> Cat(Fill(32, 0.U), Mux(io.b(31), ((~io.b) + 1.U)(31, 0), io.b(31, 0))),
      aluDIVUW -> Cat(Fill(32, 0.U), io.b(31, 0)),
      aluREM -> Mux(io.b(xlen - 1), ((~io.b) + 1.U), io.b),
      aluREMU -> io.b,
      aluREMW -> Cat(Fill(32, 0.U), Mux(io.b(31), ((~io.b) + 1.U)(31, 0), io.b(31, 0))),
      aluREMUW -> Cat(Fill(32, 0.U), io.b(31, 0))
    )
  )
  val res_ss = Mux(sign_a === sign_b, res, ((~res) + 1.U))
  val res_su = Mux(sign_a === 0.U, res, ((~res) + 1.U))
  val res_divs = Mux(sign_a === sign_b, res(xlen - 1, 0), ((~res(xlen - 1, 0)) + 1.U))
  val res_rems = Mux(sign_a === 0.U, res(2 * xlen - 1, xlen), ((~res(2 * xlen - 1, xlen)) + 1.U))
  val res_divsw = Mux(sign_wa === sign_wb, res(31, 0), ((~res(31, 0)) + 1.U))
  val res_remsw = Mux(sign_a === 0.U, res(xlen + 31, xlen), ((~res(xlen + 31, xlen)) + 1.U))
  val last_stall_req = RegInit(Bool(), false.B)
  val step_result = WireInit(UInt((2 * xlen).W), 0.U)
  val front_val = WireInit(UInt((xlen + 1).W), 0.U)

  last_stall_req := io.stall_req

  /* ------ Try My New Multiplier ------ */
  val abs_a_reg = RegNext(abs_a)
  val abs_b_reg = RegNext(abs_b)
  val adv_multiplier = Module(new WallaceMultiplier)
  adv_multiplier.io.abs_a_i := abs_a_reg
  adv_multiplier.io.abs_b_i := abs_b_reg
  /* ------------ Done ------------ */

  when(io.start) {
    when(is_mult) {
//      when (io.stall_req) {
//        mult_cnt := mult_cnt + 1.U
//      }.otherwise {
//        mult_cnt := 0.U
//      }
//      when(io.stall_req) {
//        when(!last_stall_req) {
//          res := Cat(Fill(xlen, 0.U), abs_b)
//        }.otherwise {
//          res := Cat(front_val(xlen), step_result(2 * xlen - 1, 1))
//        }
//      }
      when(io.stall_req) {
        mult_cnt := mult_cnt + 1.U
      }.otherwise {
        mult_cnt := 0.U
      }
      when(io.stall_req) {
        res := adv_multiplier.io.data_o
      }
    }.otherwise {
      when (io.stall_req) {
        div_cnt := div_cnt + 1.U
      }.otherwise {
        div_cnt := 0.U
      }
      when(io.stall_req) {
        when(!last_stall_req) {
          res := Cat(Fill(xlen - 1, 0.U), abs_a, Fill(1, 0.U))
        }.elsewhen(div_cnt === xlen.U) {
          res := Cat(front_val(xlen - 1), step_result(2 * xlen - 1, xlen + 1), step_result(xlen - 1, 0))
        }.otherwise {
          res := step_result
        }
      }

    }
  }

  // TODO a more advanced multiplication algorithm needs implementing, 65 cycles are too long
  // TODO additionally, div_cnt and mult_cnt are separated because I want to accelerate MULT

  when(is_mult) {
    io.stall_req := io.start && (mult_cnt =/= 4.U || (!last_stall_req && !check_history_same(io.a, io.b, io.op, last_a, last_b, last_op)))
//    front_val := Cat(Fill(1, 0.U), Mux(res(0), abs_a, 0.U(xlen.W))) + Cat(Fill(1, 0.U), res(2 * xlen - 1, xlen))
//    step_result := Cat(front_val(xlen - 1, 0), res(xlen - 1, 0))
//    io.stall_req := io.start && (mult_cnt =/= (xlen + 1).U || (!last_stall_req && !check_history_same(io.a, io.b, io.op, last_a, last_b, last_op)))
    io.mult_out := MuxLookup(io.op, Cat(Fill(32, res(31)), res(31, 0)),
      Seq(
        aluMUL -> res(xlen - 1, 0),
        aluMULH -> res_ss(2 * xlen - 1, xlen),
        aluMULHSU -> res_su(2 * xlen - 1, xlen),
        aluMULHU -> res(2 * xlen - 1, xlen),
        aluMULW -> Cat(Fill(32, res(31)), res(31, 0))
      )
    )
  }.otherwise {
    front_val := Mux(res(2 * xlen - 1, xlen) >= abs_b, res(2 * xlen - 1, xlen) - abs_b, res(2 * xlen - 1, xlen))
    step_result := Cat(front_val(xlen - 2, 0), res(xlen - 1, 0), (res(2 * xlen - 1, xlen) >= abs_b).asUInt)
    io.stall_req := io.start && (div_cnt =/= (xlen + 1).U || (!last_stall_req && !check_history_same(io.a, io.b, io.op, last_a, last_b, last_op)))
    io.mult_out := MuxLookup(io.op, 0.U,
      Seq(
        aluDIV -> Mux(io.b.orR, res_divs, Fill(xlen, 1.U)),
        aluDIVU -> Mux(io.b.orR, res(xlen - 1, 0), Fill(xlen, 1.U)),
        aluDIVW -> Mux(io.b(31, 0).orR, Cat(Fill(32, res_divsw(31)), res_divsw), Fill(xlen, 1.U)),
        aluDIVUW -> Mux(io.b(31, 0).orR, Cat(Fill(32, res(31)), res(31, 0)), Fill(xlen, 1.U)),
        aluREM -> Mux(io.b.orR, res_rems, io.a),
        aluREMU -> Mux(io.b.orR, res(2 * xlen - 1, xlen), io.a),
        aluREMW -> Mux(io.b(31, 0).orR, Cat(Fill(32, res_remsw(31)), res_remsw), io.a),
        aluREMUW -> Mux(io.b(31, 0).orR, Cat(Fill(32, res(xlen + 31)), res(xlen + 31, xlen)), io.a)
      )
    )
  }

}

class AMOALUIO extends Bundle with phvntomParams {
  val a = Input(UInt(xlen.W))
  val b = Input(UInt(xlen.W))
  val op = Input(UInt(amoBits.W))
  val is_word = Input(Bool())
  val ret = Output(UInt(xlen.W))
}

class AMOALU extends Module with phvntomParams {
  val io = IO(new AMOALUIO)

  def sign_ext32(a: UInt): UInt = { Cat(Fill(32, a(31)), a(31, 0)) }
  val real_a = Mux(io.is_word, sign_ext32(io.a), io.a)
  val real_b = Mux(io.is_word, sign_ext32(io.b), io.b)
  val out = MuxLookup(
    io.op,
    "hdeadbeef".U,
    Seq(
      amoSWAP -> real_b,
      amoADD  -> (real_a + real_b),
      amoAND  -> (real_a & real_b),
      amoOR   -> (real_a | real_b),
      amoXOR  -> (real_a ^ real_b),
      amoMAX  -> Mux(real_a.asSInt > real_b.asSInt, real_a, real_b),
      amoMAXU -> Mux(real_a > real_b, real_a, real_b),
      amoMIN  -> Mux(real_a.asSInt < real_b.asSInt, real_a, real_b),
      amoMINU -> Mux(real_a < real_b, real_a, real_b)
    )
  )

  when(io.is_word) {
    io.ret := Cat(Fill(32, out(31)), out(31, 0))
  }.otherwise {
    io.ret := out
  }
}