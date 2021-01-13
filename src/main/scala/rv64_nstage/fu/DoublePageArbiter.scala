package rv64_nstage.fu

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams
import rv64_nstage.control.ControlConst._

// This unit detects the instruction in ID stage
// Step 1: If we [use IMMU] and the instruction is [not compressed] and is [in different pages] and there is [no page fault]
//         The the high half of the instruction must be invalid
//         Then we [flush IF1 to IF3], and [store the basic information] in HERE
// Step 2: Use [PC + 2] to get the upper half in next page
//         Then we can check if there is a instruction page fault
// Step 3: After several cycles, the upper half will be HERE with the information of page fault
//         We first [assemble the instruction] and flush [IF1 to IF3], with [PC + 4] to be the new PC
// Step 4: We carry all information along the pipeline
class DoublePageArbiterIO extends Bundle with phvntomParams {
  val vpc = Input(UInt(xlen.W))
  val inst = Input(UInt((xlen / 2).W))
  val page_fault = Input(Bool())
  val is_compressed = Input(Bool())
  val use_immu = Input(Bool())
  val flush_req = Output(Bool())
  val flush_target_vpc = Output(UInt(xlen.W))
  val insert_bubble_next = Output(Bool())
  val full_inst = Output(UInt(xlen.W))
  val full_inst_pc = Output(UInt(xlen.W))
  val full_inst_ready = Output(Bool())
}

class DoublePageArbiter extends Module with phvntomParams {
  val io = IO(new DoublePageArbiterIO)

  def cross_page(vpc: UInt): Bool = {
    (vpc + 2.U)(12) =/= vpc(12)
  }

  val s_idle :: s_wait :: Nil = Enum(2)
  val state = RegInit(s_idle)
  val next_state = WireDefault(s_idle)
  val low_vpc_buf = RegInit(UInt(xlen.W), startAddr.asUInt)
  val low_inst_buff = RegInit(UInt((xlen / 2).W), BUBBLE(15, 0))

  state := next_state

  when (state === s_idle) {
    when (io.use_immu && !io.is_compressed && cross_page(io.vpc) && !io.page_fault) {
      next_state := s_wait
    }.otherwise {
      next_state := s_idle
    }
  }.otherwise {
    when (io.vpc === low_vpc_buf + 2.U) {
      next_state := s_idle
    }.otherwise {
      next_state := s_wait
    }
  }

  when (state === s_idle) {
    when (next_state === s_wait) {
      low_vpc_buf := io.vpc
      low_inst_buff := io.inst(15, 0)
    }
  }

  io.flush_req := state === s_idle && next_state === s_wait || state === s_wait && next_state === s_idle
  io.flush_target_vpc := io.vpc + 2.U
  io.insert_bubble_next := next_state === s_wait
  io.full_inst := Cat(io.inst(15, 0), low_inst_buff)
  io.full_inst_pc := low_vpc_buf
  io.full_inst_ready := state === s_wait && next_state === s_idle
}
