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
  val s_amo  = 1.U(3.W)
  val s_write = 2.U(3.W)
  val s_write_wait = 3.U(3.W)
  val s_finish = 4.U(3.W)

  val state = RegInit(UInt(s_idle.getWidth.W), s_idle)
  val next_state = WireInit(UInt(s_idle.getWidth.W), s_idle)
  val amo_res = RegInit(UInt(xlen.W), 0.U)
  val mem_val = RegInit(UInt(xlen.W), 0.U)
  val last_stall_req = RegInit(Bool(), false.B)

  state := next_state
  when(state === s_idle) {
    when(io.amo_op.orR && !io.exception_or_int && io.dmem_valid) {
      next_state := s_amo
    }.otherwise {
      next_state := state
    }
  }.elsewhen(state === s_amo) {
    next_state := s_write
  }.elsewhen(state === s_write) {
    next_state := s_write_wait
  }.elsewhen(state === s_write_wait) {
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

  when(next_state === s_amo) {
    mem_val := io.dmem_data
  }

  last_stall_req := io.stall_req

  io.write_what := amo_res
  io.write_now := state === s_write
  io.dont_read_again := state === s_amo
  io.stall_req := next_state =/= s_idle && next_state =/= s_finish
  io.mem_val_out := mem_val
  io.force_mem_val_out := last_stall_req && !io.stall_req

//  printf("--------- state %x, valid %x, read_in %x, write_now %x, write_what %x\n", state, io.dmem_valid, io.dmem_data, io.write_now, io.write_what)
}

class ReservationIO extends Bundle with phvntomParams {
  val push = Input(Bool())
  val push_is_word = Input(Bool())
  val push_addr = Input(UInt(xlen.W))
  val compare = Input(Bool())
  val compare_is_word = Input(Bool())
  val compare_addr = Input(UInt(xlen.W))
  val flush = Input(Bool())
  val succeed = Output(Bool())
}

class Reservation extends Module with phvntomParams {
  val io = IO(new ReservationIO)

  val empty = RegInit(Bool(), true.B)
  val addr = RegInit(UInt(xlen.W), 0.U)
  val is_word = RegInit(Bool(), false.B)

  when(io.push) {
    empty := false.B
    addr := io.push_addr
    is_word := io.push_is_word
  }.elsewhen(io.compare || io.flush) {
    empty := true.B
  }

//  printf("compare %x; succeed %x\n", io.compare, io.succeed)

  io.succeed := !empty && (addr === io.compare_addr && is_word === io.compare_is_word) && io.compare
}
