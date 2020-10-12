package rv64_nstage.fu

import chisel3._
import chisel3.util._
import rv64_nstage.control.ControlConst._
import rv64_nstage.core.phvntomParams

class AMOArbiterIO extends Bundle with phvntomParams {
  // Exception
  val exception_or_int = Input(Bool())
  // AMO and basic informations from RegExeMem1
  val amo_op = Input(UInt(amoBits.W))
  val dmem_valid = Input(Bool())
  val dmem_data = Input(UInt(xlen.W))
  val reg_val = Input(UInt(xlen.W))
  val mem_type = Input(UInt(memBits.W))
  // AMO stall request has higher priority over D$
  val stall_req = Output(Bool())
  val write_now = Output(Bool())
  val dont_read_again = Output(Bool())
  val write_what = Output(UInt(xlen.W))
  val mem_val_out = Output(UInt(xlen.W))
  val force_mem_val_out = Output(Bool())
}

// TODO use FSM and communicate with D$
class AMOArbiter extends Module with phvntomParams {
  val io = IO(new AMOArbiterIO)
  val amo_alu = Module(new AMOALU)

  val s_idle = 0.U(3.W)
  val s_read = 1.U(3.W)
  val s_amo  = 2.U(3.W)
  val s_write = 3.U(3.W)
  val s_finish = 4.U(3.W)

  val state = RegInit(UInt(s_idle.getWidth.W), s_idle)
  val next_state = WireInit(UInt(s_idle.getWidth.W), s_idle)
  val amo_res = RegInit(UInt(xlen.W), 0.U)
  val mem_val = RegInit(UInt(xlen.W), 0.U)
  val last_stall_req = RegInit(Bool(), false.B)

  state := next_state
  when(state === s_idle) {
    when(io.amo_op.orR && !io.exception_or_int) {
      next_state := s_read
    }.otherwise {
      next_state := state
    }
  }.elsewhen(state === s_read) {
    when(io.dmem_valid) {
      next_state := s_amo
    }.otherwise {
      next_state := state
    }
  }.elsewhen(state === s_amo) {
    next_state := s_write
  }.elsewhen(state === s_write) {
    when(io.dmem_valid) {
      next_state := s_finish
    }.otherwise {
      next_state := state
    }
  }.otherwise {
    next_state := s_idle
  }

  amo_alu.io.a := io.dmem_data
  amo_alu.io.b := io.reg_val
  amo_alu.io.op := io.amo_op
  amo_alu.io.is_word := io.mem_type

  when(next_state === s_amo) {
    amo_res := amo_alu.io.ret
  }

  when(state === s_read && next_state === s_amo) {
    mem_val := io.dmem_data
  }

  last_stall_req := io.stall_req

  io.write_what := amo_res
  io.write_now := state === s_write
  io.dont_read_again := state === s_amo
  io.stall_req := next_state =/= s_idle && next_state =/= s_finish
  io.mem_val_out := mem_val
  io.force_mem_val_out := last_stall_req && !io.stall_req
}
