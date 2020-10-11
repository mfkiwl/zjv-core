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

  // TODO these buffers are used for the sequence MUL and MULH
  val last_a = RegInit(UInt(xlen.W), 0.U)
  val last_b = RegInit(UInt(xlen.W), 0.U)
  val last_op = RegInit(UInt(aluBits.W), aluXXX)

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

  when(io.start) {
    when(is_mult) {
      when (io.stall_req) {
        mult_cnt := mult_cnt + 1.U
      }.otherwise {
        mult_cnt := 0.U
      }
      when(!last_stall_req) {
        res := Cat(Fill(xlen, 0.U), abs_b)
      }.otherwise {
        res := Cat(front_val(xlen), step_result(2 * xlen - 1, 1))
      }
    }.otherwise {
      when (io.stall_req) {
        div_cnt := div_cnt + 1.U
      }.otherwise {
        div_cnt := 0.U
      }
      when(!last_stall_req) {
        res := Cat(Fill(xlen - 1, 0.U), abs_a, Fill(1, 0.U))
      }.elsewhen(div_cnt === xlen.U) {
        res := Cat(front_val(xlen - 1), step_result(2 * xlen - 1, xlen + 1), step_result(xlen - 1, 0))
      }.otherwise {
        res := step_result
      }
    }
  }

  // TODO a more advanced multiplication algorithm needs implementing, 65 cycles are too long
  // TODO additionally, div_cnt and mult_cnt are separated because I want to accelerate MULT

  when(is_mult) {
    front_val := Cat(Fill(1, 0.U), Mux(res(0), abs_a, 0.U(xlen.W))) + Cat(Fill(1, 0.U), res(2 * xlen - 1, xlen))
    step_result := Cat(front_val(xlen - 1, 0), res(xlen - 1, 0))
    io.stall_req := io.start && (mult_cnt =/= (xlen + 1).U || !last_stall_req)
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
    io.stall_req := io.start && (div_cnt =/= (xlen + 1).U || !last_stall_req)
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


}