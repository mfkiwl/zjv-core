package tile.common.fu

import chisel3._
import chisel3.util._
import tile.phvntomParams

class PcGenIO extends Bundle with phvntomParams {
  // Stall Signal
  val stall = Input(Bool())
  // Exception, Interrupt and Return
  val expt_int = Input(Bool())
  val error_ret = Input(Bool())
  val write_satp = Input(Bool())
  val flush_cache_tlb = Input(Bool())
  val epc = Input(UInt(xlen.W))
  val tvec = Input(UInt(xlen.W))
  // Prediction
  val predict_jump = Input(Bool())
  val predict_jump_target = Input(UInt(xlen.W))
  // Branch and Jump
  val branch_jump = Input(Bool())
  val branch_pc = Input(UInt(xlen.W))
  val pc_plus = Input(UInt(xlen.W))
  val inst_addr_misaligned = Input(Bool())
  // Compressed Extension
  val compr_jump = Input(Bool())
  val compr_pc = Input(UInt(xlen.W))
  // PC Output
  val pc_out = Output(UInt(xlen.W))
  val last_stall_out = Output(Bool())
}

class PcGen extends Module with phvntomParams {
  val io = IO(new PcGenIO)

  val pc = RegInit(UInt(xlen.W), startAddr.asUInt)

  val last_stall = RegInit(Bool(), false.B)
  val has_flush_in_stall = RegInit(Bool(), false.B)
  val pc_for_restore = RegInit(UInt(xlen.W), startAddr.asUInt)

  last_stall := io.stall
  io.last_stall_out := last_stall

  when(io.expt_int) {
    pc_for_restore := io.tvec
    has_flush_in_stall := true.B
  }.elsewhen(io.error_ret) {
    pc_for_restore := io.epc
    has_flush_in_stall := true.B
  }.elsewhen(io.write_satp) {
    pc_for_restore := io.pc_plus
    has_flush_in_stall := true.B
  }.elsewhen(io.flush_cache_tlb) {
    pc_for_restore := io.pc_plus
    has_flush_in_stall := true.B
  }.elsewhen(io.branch_jump && !io.inst_addr_misaligned) {
    pc_for_restore := io.branch_pc
    has_flush_in_stall := true.B
  }.elsewhen(io.compr_jump) {
    pc_for_restore := io.compr_pc
    has_flush_in_stall := true.B
  }.elsewhen(io.stall && !last_stall) {
    has_flush_in_stall := false.B
    pc_for_restore := Mux(io.predict_jump, io.predict_jump_target, pc + 4.U)
  }.elsewhen(io.stall && !has_flush_in_stall) {
    pc_for_restore := Mux(io.predict_jump, io.predict_jump_target, pc + 4.U)
  }

  when(!io.stall) {
    when(io.expt_int) {
      pc := Cat(io.tvec(xlen - 1, 1), Fill(1, 0.U))
    }.elsewhen(io.error_ret) {
      pc := Cat(io.epc(xlen - 1, 1), Fill(1, 0.U))
    }.elsewhen(io.write_satp) {
      pc := io.pc_plus
    }.elsewhen(io.flush_cache_tlb) {
      pc := io.pc_plus
    }.elsewhen(io.branch_jump && !io.inst_addr_misaligned) {
      pc := Cat(io.branch_pc(xlen - 1, 1), Fill(1, 0.U))
    }.elsewhen(io.compr_jump) {
      pc := Cat(io.compr_pc(xlen - 1, 1), Fill(1, 0.U))
    }.elsewhen(last_stall && has_flush_in_stall) {
      pc := Cat(pc_for_restore(xlen - 1, 1), Fill(1, 0.U))
    }.otherwise {
      pc := Mux(io.predict_jump, io.predict_jump_target, pc + 4.U)
    }
  }

  io.pc_out := pc

  if (pipeTrace) {
//    printf("In PC_Gen: pc %x, stall %x, br %x, bpc %x, ei %x, tvec %x\n", pc, io.stall, io.branch_jump, io.branch_pc, io.expt_int, io.tvec)
  }
}
