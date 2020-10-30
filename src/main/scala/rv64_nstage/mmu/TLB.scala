package rv64_nstage.mmu

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams
import utils._
import rv64_nstage.register.SATP
import rv64_nstage.register.CSR
import mem._
import device._

class TLBEntry(implicit val mmuConfig: MMUConfig)
  extends Bundle
    with MMUParameters {
  val valid = Bool()
  val meta = UInt(log2Ceil(nEntry).W)
  val vpn = UInt(27.W)
  val asid = UInt(16.W)
  val level = UInt(2.W)
  val pte = UInt(xlen.W)

  override def toPrintable: Printable =
    p"TLBEntry(valid=${valid}, meta=${meta}, level=${level}, vpn = 0x${
      Hexadecimal(
        vpn
      )
    }, asid = 0x${Hexadecimal(asid)}, pte = 0x${Hexadecimal(pte)})"
}

class TLB(implicit val mmuConfig: MMUConfig) extends Module with MMUParameters {
  val io = IO(new Bundle {
    val in = new MMUFrontIO
    val out = Flipped(new PTWIO)
  })

  def choose_victim(array: Vec[TLBEntry]): UInt = {
    val ageVec = VecInit(array.map(m => !m.meta.orR)).asUInt
    PriorityEncoder(ageVec)
  }

  def update_meta(
                   array: Vec[TLBEntry],
                   access_index: UInt
                 ): Vec[TLBEntry] = {
    val length = log2Ceil(array.length)
    val new_meta = WireDefault(array)
    val old_meta_value =
      Mux(array(access_index).valid, array(access_index).meta, 0.U)
    (new_meta zip array).map {
      case (n, o) =>
        when(o.meta > old_meta_value) {
          n.meta := o.meta - 1.U
        }
    }
    new_meta(access_index).meta := Fill(length, 1.U(1.W))
    new_meta
  }

  def illegal_va(va: UInt): Bool = {
    Mux(
      va(validVABits - 1),
      !va(xlen - 1, validVABits).andR,
      va(xlen - 1, validVABits).orR
    )
  }

  //  def pass_protection_check(
  //                             pte: UInt,
  //                             is_inst: Bool,
  //                             is_load: Bool,
  //                             is_store: Bool,
  //                             mxr: Bool
  //                           ): Bool = {
  //    Mux(is_inst,
  //      pte(3),
  //      Mux(is_load && is_store,
  //        (pte(1) | (mxr & pte(3))) & pte(2),
  //        Mux(is_load,
  //          pte(1) | (mxr & pte(3)),
  //          Mux(is_store,
  //            pte(2),
  //            true.B
  //          )
  //        )
  //      )
  //    )
  //  }

  def misaligned_spage(lev: UInt, last_pte: UInt): Bool = {
    Mux(
      lev === 0.U,
      false.B,
      Mux(lev === 1.U, last_pte(18, 10).orR, last_pte(27, 10).orR)
    )
  }

  // TODO Currently this is done out of MMU
  def pass_pmp_pma(last_pte: UInt): Bool = {
    true.B
  }

  //  def sum_is_zero_fault(
  //                         sum: UInt,
  //                         force_s_mode: Bool,
  //                         mpp_s: Bool,
  //                         current_p: UInt,
  //                         last_pte: UInt
  //                       ): Bool = {
  //    Mux(
  //      sum === 1.U,
  //      false.B,
  //      Mux(current_p === CSR.PRV_S || (force_s_mode && mpp_s), last_pte(4), false.B)
  //    )
  //  }

  def violate_pte_u_prot(current_p: UInt, last_pte: UInt, is_fetch: Bool, sum: UInt): Bool = {
    Mux(last_pte(4), current_p === CSR.PRV_S && (is_fetch || !sum.asBool), current_p === CSR.PRV_U)
  }

  def violate_pte_v_rw(last_pte: UInt): Bool = {
    !last_pte(0) || (!last_pte(1) && last_pte(2))
  }

  def violate_pte_ad(last_pte: UInt, is_store: Bool): Bool = {
    !last_pte(6) || (!last_pte(7) && is_store)
  }

  def violate_pte_rwx(last_pte: UInt, is_inst: Bool, is_load: Bool, mxr: UInt): Bool = {
    Mux(is_inst, !last_pte(3), Mux(is_load, !last_pte(1) && !(mxr.asBool && last_pte(3)), !(last_pte(2) && last_pte(1))))
  }

  val entryArray =
    RegInit(Vec(nEntry, new TLBEntry), 0.U.asTypeOf(Vec(nEntry, new TLBEntry)))

  val satp = WireInit(io.in.satp_val)
  val satp_mode = satp(xlen - 1, 60)
  val satp_asid = satp(59, 44)
  val satp_ppn = satp(43, 0)

  val hit_vec = VecInit(
    entryArray.map(m =>
      m.valid && m.asid === satp_asid && Mux(
        m.level === 2.U,
        m.vpn(26, 18) === io.in.va(38, 30),
        Mux(
          m.level === 1.U,
          m.vpn(26, 9) === io.in.va(38, 21),
          m.vpn === io.in.va(38, 12)
        )
      )
    )
  ).asUInt
  val hit_index = PriorityEncoder(hit_vec)
  val victim_index = choose_victim(entryArray)
  val victim_vec = UIntToOH(victim_index)
  val hit = hit_vec.orR
  val access_index = Mux(hit, hit_index, victim_index)
  val access_vec = UIntToOH(access_index)

  val s_idle :: s_req :: s_resp :: s_flush :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val need_translate =
    io.in.valid && satp_mode =/= SATP.Bare && (io.in.current_p =/= CSR.PRV_M || (isdmmu.B && io.in.force_s_mode))
  val need_ptw = !hit && need_translate
  val request_satisfied = hit || (state === s_resp && io.out.resp.fire())
  val current_level =
    Mux(hit, entryArray(hit_index).level, io.out.resp.bits.level)
  val current_pte = Mux(hit, entryArray(hit_index).pte, io.out.resp.bits.pte)
  val final_pa = Cat(
    Mux(
      current_level === 0.U,
      current_pte(53, 10),
      Mux(
        current_level === 1.U,
        Cat(current_pte(53, 19), io.in.va(20, 12)),
        Cat(current_pte(53, 28), io.in.va(29, 12))
      )
    ),
    io.in.va(11, 0)
  )

  val prot_check = (!violate_pte_u_prot(io.in.current_p, current_pte, io.in.is_inst, io.in.sum) &&
    !violate_pte_v_rw(current_pte) &&
    !violate_pte_ad(current_pte, io.in.is_store) &&
    !violate_pte_rwx(current_pte, io.in.is_inst, io.in.is_load, io.in.mxr) &&
    !misaligned_spage(current_level, current_pte) &&
    pass_pmp_pma(current_pte)
    )

  //  val prot_check = pass_protection_check(
  //    current_pte,
  //    io.in.is_inst,
  //    io.in.is_load,
  //    io.in.is_store,
  //    io.in.mxr.orR,
  //  ) &&
  //    !misaligned_spage(current_level, current_pte) &&
  //    pass_pmp_pma(current_pte) &&
  //    !sum_is_zero_fault(
  //      io.in.sum,
  //      io.in.force_s_mode,
  //      io.in.mpp_s,
  //      io.in.current_p,
  //      current_pte
  //    ) && check_user_prot(io.in.current_p, current_pte)

  io.in.pf := need_translate && request_satisfied && (io.out.resp.bits.pf || illegal_va(
    io.in.va
  ) || !prot_check)
  io.in.af := false.B
  io.in.stall_req := need_ptw && !io.out.resp.valid
  io.in.pa := Mux(!need_translate || io.in.pf, io.in.va, final_pa)

  io.out.req.valid := state === s_req // || state === s_resp
  io.out.req.bits.va := io.in.va
  io.out.req.bits.satp_ppn := satp_ppn

  switch(state) {
    is(s_idle) {
      when(io.in.flush_all) {
        state := s_flush
      }.elsewhen(need_ptw) {
        state := s_req
      }
    }
    is(s_req) {
      when(io.out.req.fire()) {
        state := s_resp
      }
    }
    is(s_resp) {
      when(io.out.resp.fire()) {
        state := s_idle
      }
    }
    is(s_flush) {
      state := s_idle
    }
  }

  when(request_satisfied) {
    entryArray := update_meta(entryArray, access_index)
    when(!hit) {
      entryArray(victim_index).valid := true.B
      entryArray(victim_index).vpn := io.in.va(38, 12)
      entryArray(victim_index).asid := satp_asid
      entryArray(victim_index).level := io.out.resp.bits.level
      entryArray(victim_index).pte := io.out.resp.bits.pte
      // printf(
      //   p"[${GTimer()}]TLB write: vpn=${Hexadecimal(io.in.va(38, 12))}, asid=${Hexadecimal(
      //     satp_asid
      //   )}, level=${io.out.resp.bits.level}, pte=${io.out.resp.bits.pte}\n"
      // )
    }
  }

  when(state === s_flush) {
    for (i <- 0 until nEntry) {
      entryArray(i).valid := false.B
    }
  }

  if (pipeTrace) {
    printf(p"[${GTimer()}]: ${mmuName} TLB Debug Info\n")
    printf(
      p"state=${state}, prot_check=${prot_check}, hit=${hit}, hit_index=${hit_index}, victim_index=${victim_index}\n"
    )
    printf(
      p"need_translate=${need_translate}, current_level=${current_level}, current_pte=${Hexadecimal(current_pte)}, final_pa=${Hexadecimal(final_pa)}\n"
    )
    printf(
      p"satp_mode=${satp_mode}, satp_asid=${Hexadecimal(satp_asid)}, satp_ppn=${Hexadecimal(satp_ppn)}\n"
    )
    printf(p"io.in: ${io.in}\n")
    //    printf(p"io.out: ${io.out}\n")
    printf(p"entryArray=${entryArray}\n")
    printf("-----------------------------------------------\n")
  }

}
