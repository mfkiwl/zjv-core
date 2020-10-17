package rv64_nstage.tlb

import chisel3.util._
import chisel3._
import rv64_nstage.core.phvntomParams
import rv64_nstage.control.ControlConst._
import rv64_nstage.tlb.TLBConfig

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

//class EmbeddedTLB extends Module with TLBConfig {
//  val io = IO(new TLBIO)
//
//  // Some Constants of the FSM
//  val s_idle = 0.U(3.W)
//  val s_mem_req = 1.U(3.W)
//  val s_mem_resp = 2.U(3.W)
//  val s_write_pte = 3.U(3.W)
//  val s_wait_resp = 4.U(3.W)
//  val s_miss_slpf = 5.U(3.W)
//  val fsmBits = s_idle.getWidth
//
//  // Sv39 Constants
//  val level_39 = 3
//  val levelBits = log2Ceil(level_39)
//
//  // TLB Hit
//  val hit = io.valid_visit && true.B // TODO Tomorrow
//  val miss = io.valid_visit && !true.B
//
//  // Permission Checking and Exceptions
//  // TODO
//
//  // FSM for Refilling TLB
//  val state = RegInit(UInt(fsmBits.W), s_idle)
//  val level = RegInit(UInt(levelBits.W), level_39)
//
//  val mem_resp_store = RegInit(UInt(xlen.W), 0.U)
//  val miss_mask = WireInit("h3ffff".U(maskLen.W))
//  val missMaskStore = Reg(UInt(maskLen.W))
//  val miss_meta_refill = WireInit(false.B)
//  val miss_refill_flag = WireInit(0.U(8.W))
//  val mem_rdata = io.mem.resp.bits.rdata.asTypeOf(pteBundle)
//  val raddr = RegInit(UInt(xlen.W), 0.U)
//
//  val need_flush = RegInit(false.B)
//  val is_flush = need_flush || io.flush_all
//
//  when(io.flush_all && (state =/= s_idle)) {
//    need_flush := true.B
//  }
//  when(io.out.fire() && need_flush) {
//    need_flush := false.B
//  }
//
//  val miss_ipf = RegInit(false.B)
//  val missflag = mem_rdata.flag.asTypeOf(flagBundle)
//
//  when(state === s_idle) {
//    when(!io.flush_all && hit_wb) {
//      state := s_write_pte
//      need_flush := false.B
//    }.elsewhen(miss && !io.flush_all) {
//      state := s_mem_req
//      raddr := paddrApply(satp.ppn, vpn.vpn2) //
//      level := Level.U
//      need_flush := false.B
//    }
//  }.elsewhen(state === s_mem_req) {
//    when(is_flush) {
//      state := s_idle
//      need_flush := false.B
//    }.elsewhen(io.mem.req.fire()) {
//      state := s_mem_resp
//    }
//  }.elsewhen(state === s_mem_resp) {
//    when(io.mem.resp.fire()) {
//      when(is_flush) {
//        state := s_idle
//        need_flush := false.B
//      }.elsewhen(!(missflag.r || missflag.x) && (level === 3.U || level === 2.U)) {
//        when(!missflag.v || (!missflag.r && missflag.w)) { //TODO: fix need_flush
//          if (tlbname == "itlb") {
//            state := s_wait_resp
//          } else {
//            state := s_miss_slpf
//          }
//          if (tlbname == "itlb") {
//            missIPF := true.B
//          }
//          if (tlbname == "dtlb") {
//            loadPF := req.isRead() && !isAMO
//            storePF := req.isWrite() || isAMO
//          }
//        }.otherwise {
//          state := s_memReadReq
//          raddr := paddrApply(memRdata.ppn, Mux(level === 3.U, vpn.vpn1, vpn.vpn0))
//        }
//      }.elsewhen(level =/= 0.U) { //TODO: fix need_flush
//        val permCheck = missflag.v && !(pf.priviledgeMode === ModeU && !missflag.u) && !(pf.priviledgeMode === ModeS && missflag.u && (!pf.status_sum || ifecth))
//        val permExec = permCheck && missflag.x
//        val permLoad = permCheck && (missflag.r || pf.status_mxr && missflag.x)
//        val permStore = permCheck && missflag.w
//        val updateAD = if (Settings.get("FPGAPlatform")) !missflag.a || (!missflag.d && req.isWrite()) else false.B
//        val updateData = Cat(0.U(56.W), req.isWrite(), 1.U(1.W), 0.U(6.W))
//        missRefillFlag := Cat(req.isWrite(), 1.U(1.W), 0.U(6.W)) | missflag.asUInt
//        memRespStore := io.mem.resp.bits.rdata | updateData
//        if (tlbname == "itlb") {
//          when(!permExec) {
//            missIPF := true.B;
//            state := s_wait_resp
//          }
//            .otherwise {
//              state := Mux(updateAD, s_write_pte, s_wait_resp)
//              missMetaRefill := true.B
//            }
//        }
//        if (tlbname == "dtlb") {
//          when((!permLoad && req.isRead()) || (!permStore && req.isWrite())) {
//            state := s_miss_slpf
//            loadPF := req.isRead() && !isAMO
//            storePF := req.isWrite() || isAMO
//          }.otherwise {
//            state := Mux(updateAD, s_write_pte, s_wait_resp)
//            missMetaRefill := true.B
//          }
//        }
//        missMask := Mux(level === 3.U, 0.U(maskLen.W), Mux(level === 2.U, "h3fe00".U(maskLen.W), "h3ffff".U(maskLen.W)))
//        missMaskStore := missMask
//      }
//      level := level - 1.U
//    }
//  }
//
//  is(s_write_pte) {
//    when(isFlush) {
//      state := s_idle
//      need_flush := false.B
//    }.elsewhen(io.mem.req.fire()) {
//      state := s_wait_resp
//    }
//  }
//
//  is(s_wait_resp) {
//    when(io.out.fire() || ioFlush || alreadyOutFire) {
//      state := s_idle
//      missIPF := false.B
//      alreadyOutFire := false.B
//    }
//  }
//
//  is(s_miss_slpf) {
//    state := s_idle
//  }
//}
//
//io.pf := false.B
//io.af := false.B
//io.pa := io.va
//io.stall_req := state.orR
//}
