package rv64_nstage.tlb

import chisel3.util._
import chisel3._
import rv64_nstage.core.phvntomParams
import rv64_nstage.control.ControlConst._

class TLBStorageIO extends Bundle with phvntomParams {

}

class TLBStorage extends Module with phvntomParams {

}

class TLBIO extends Bunble with phvntomParams {
  val valid_visit = Input(Bool())
  val va = Input(UInt(validVABits.W))
  val flush_all = Input(Bool())
  val satp_val = Input(UInt(xlen.W))
  val stall_req = Output(Bool())
  val pa = Output(UInt(xlen.W))
  val pf = Output(Bool())
  val af = Output(Bool())
}

class EmbeddedTLB(config: TLBConfig) extends Module with phvntomParams {
  val io = IO(new TLBIO)

  // Some Constants of the FSM
  val s_idle = 0.U(3.W)
  val s_mem_req = 1.U(3.W)
  val s_mem_resp = 2.U(3.W)
  val s_write_pte = 3.U(3.W)
  val s_wait_resp = 4.U(3.W)
  val s_miss_slpf = 5.U(3.W)
  val fsmBits = s_idle.getWidth

  // Sv39 Constants
  val level_39 = 3
  val levelBits = log2Ceil(level_39)

  // TLB Hit
  val hit = io.valid_visit && true.B  // TODO Tomorrow
  val miss = io.valid_visit && !true.B

  // Permission Checking and Exceptions
  // TODO

  // FSM for Refilling TLB
  val state = RegInit(UInt(fsmBits.W), s_idle)
  val level = RegInit(UInt(levelBits.W), level_39)

  val mem_resp_store = RegInit(UInt(xlen.W), 0.U)
  val miss_mask = WireInit("h3ffff".U(maskLen.W))
  val missMaskStore = Reg(UInt(maskLen.W))
  val miss_meta_refill = WireInit(false.B)
  val miss_refill_flag = WireInit(0.U(8.W))
  val mem_rdata = io.mem.resp.bits.rdata.asTypeOf(pteBundle)
  val raddr = RegInit(UInt(xlen.W), 0.U)

  when(state === s_idle) {
    when (!io_flush && hit_wb) {
      state := s_refi
      need_flush := false.B
    }.elsewhen (miss && !io_flush) {
      state := s_memReadReq
      raddr := paddrApply(satp.ppn, vpn.vpn2) //
      level := Level.U
      needFlush := false.B
      alreadyOutFire := false.B
    }
  }.elsewhen(state === s_wait) {
    when(/*resp*/) {
      next_state := s_recv
    }.otherwise {
      next_state := s_wait
    }
  }.elsewhen(state === s_recv) {
    when(/*over*/) {
      next_state := s_refi
    }.otherwise {
      next_state := s_recv
    }
  }.elsewhen(state === s_refi) {
    next_state := s_fini
  }.otherwise {
    next_state := s_idle
  }

  io.pf := false.B
  io.af := false.B
  io.pa := io.va
  io.stall_req := state.orR
}
