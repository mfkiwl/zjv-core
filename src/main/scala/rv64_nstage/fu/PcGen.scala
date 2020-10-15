package rv64_nstage.fu

import chisel3.util._
import chisel3._
import rv64_nstage.core.phvntomParams
import rv64_nstage.control.ControlConst._

class PcGenIO extends Bundle with phvntomParams {
  // Stall Signal
  val stall = Input(Bool())
  // Exception, Interrupt and Return
  val expt_int = Input(Bool())
  val error_ret = Input(Bool())
  val epc = Input(UInt(xlen.W))
  val tvec = Input(UInt(xlen.W))
  // Branch and Jump
  val branch_jump = Input(Bool())
  val branch_pc = Input(UInt(xlen.W))
  val inst_addr_misaligned = Input(Bool())
  // PC Output
  val pc_out = Output(UInt(xlen.W))
}

class PcGen extends Module with phvntomParams {
  val io = IO(new PcGenIO)

  val pc = RegInit(UInt(xlen.W), startAddr)

  val last_stall = RegInit(Bool(), false.B)
  val pc_for_restore = RegInit(UInt(xlen.W), startAddr)

  last_stall := io.stall

  when(io.expt_int) {
    pc_for_restore := io.tvec
  }.elsewhen(io.error_ret) {
    pc_for_restore := io.epc
  }.elsewhen(io.branch_jump && !io.inst_addr_misaligned) {
    pc_for_restore := io.branch_pc
  }.elsewhen(!last_stall && io.stall) {
    pc_for_restore := pc + 4.U
  }

  when(!io.stall) {
    when(io.expt_int) {
      pc := Cat(io.tvec(xlen - 1, 1), Fill(1, 0.U))
    }.elsewhen(io.error_ret) {
      pc := Cat(io.epc(xlen - 1, 1), Fill(1, 0.U))
    }.elsewhen(io.branch_jump && !io.inst_addr_misaligned) {
      pc := Cat(io.branch_pc(xlen - 1, 1), Fill(1, 0.U))
    } elsewhen (last_stall) {
      pc := Cat(pc_for_restore(xlen - 1, 1), Fill(1, 0.U))
    }.otherwise {
      pc := pc + 4.U
    }
  }

  io.pc_out := pc
}
