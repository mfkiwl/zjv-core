package rv64_nstage.tlb

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams
import utils._

class TLBConfig extends phvntomParams {
  val Level = 3
  val offLen = 12
  val ppn0Len = 9
  val ppn1Len = 9
  val ppn2Len = 64 - offLen - ppn0Len - ppn1Len // 2
  val ppnLen = ppn2Len + ppn1Len + ppn0Len
  val vpn2Len = 9
  val vpn1Len = 9
  val vpn0Len = 9
  val vpnLen = vpn2Len + vpn1Len + vpn0Len

  //val paddrLen = PAddrBits
  //val vaddrLen = VAddrBits
  val satpLen = xlen
  val satpModeLen = 4
  val asidLen = 16
  val flagLen = 8

  val ptEntryLen = xlen
  val satpResLen = xlen - ppnLen - satpModeLen - asidLen
  //val vaResLen = 25 // unused
  //val paResLen = 25 // unused
  val pteResLen = xlen - ppnLen - 2 - flagLen

  def vaBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
    val off = UInt(offLen.W)
  }

  def vaBundle2 = new Bundle {
    val vpn = UInt(vpnLen.W)
    val off = UInt(offLen.W)
  }

  def vaBundle3 = new Bundle {
    val vpn = UInt(vpnLen.W)
    val off = UInt(offLen.W)
  }

  def vpnBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
  }

  def paBundle = new Bundle {
    val ppn2 = UInt(ppn2Len.W)
    val ppn1 = UInt(ppn1Len.W)
    val ppn0 = UInt(ppn0Len.W)
    val off = UInt(offLen.W)
  }

  def paBundle2 = new Bundle {
    val ppn = UInt(ppnLen.W)
    val off = UInt(offLen.W)
  }

  def paddrApply(ppn: UInt, vpnn: UInt): UInt = {
    Cat(Cat(ppn, vpnn), 0.U(3.W))
  }

  def pteBundle = new Bundle {
    val reserved = UInt(pteResLen.W)
    val ppn = UInt(ppnLen.W)
    val rsw = UInt(2.W)
    val flag = new Bundle {
      val d = UInt(1.W)
      val a = UInt(1.W)
      val g = UInt(1.W)
      val u = UInt(1.W)
      val x = UInt(1.W)
      val w = UInt(1.W)
      val r = UInt(1.W)
      val v = UInt(1.W)
    }
  }

  def satpBundle = new Bundle {
    val mode = UInt(satpModeLen.W)
    val asid = UInt(asidLen.W)
    val res = UInt(satpResLen.W)
    val ppn = UInt(ppnLen.W)
  }

  def flagBundle = new Bundle {
    val d = Bool() //UInt(1.W)
    val a = Bool() //UInt(1.W)
    val g = Bool() //UInt(1.W)
    val u = Bool() //UInt(1.W)
    val x = Bool() //UInt(1.W)
    val w = Bool() //UInt(1.W)
    val r = Bool() //UInt(1.W)
    val v = Bool() //UInt(1.W)
  }

  def maskPaddr(ppn: UInt, vaddr: UInt, mask: UInt) = {
    MaskData(vaddr, Cat(ppn, 0.U(offLen.W)), Cat(Fill(ppn2Len, 1.U(1.W)), mask, 0.U(offLen.W)))
  }

  def MaskEQ(mask: UInt, pattern: UInt, vpn: UInt) = {
    (Cat("h1ff".U(vpn2Len.W), mask) & pattern) === (Cat("h1ff".U(vpn2Len.W), mask) & vpn)
  }

}

// TODO This should be an Arbiter when TLB is setup
// The privilege Mode should flow with the pipeline because
// CSR is in Mem1 but this DMMU might be in later Stage
//   [ITLB]      [DTLB]
//       \        /
//        \      /
//         \    /
//       [MMU & PTW]  [ICACHE]   [DCACHE/UNCACHE]
//            |           |            |
//            |           |            |
//            |           |            |
//        ||||||||   AXI X Bar   ||||||||||
class PTWalkerIO extends Bundle with TLBConfig {
  val valid = Input(Bool())
  val va = Input(UInt(xlen.W))
  val flush_all = Input(Bool()) // TODO flush TLB, do nothing now
  val satp_val = Input(UInt(xlen.W))
  val current_p = Input(UInt(2.W))
  // Output
  val stall_req = Output(Bool())
  val pa = Output(UInt(xlen.W))
  val pf = Output(Bool())
  val af = Output(Bool()) // TODO PMA PMP to generate access fault
  // Memory Interface
}

class PTWalker extends Module with TLBConfig {
  val io = IO(new PTWalkerIO)
  val uncache_wrapper = Module(new)

  // TODO Wrap an UNCACHE Here
  val s_idle = 0.U(2.W)
  val s_busy = 1.U(2.W)
  val s_check = 2.U(2.W)
  val s_finish = 3.U(2.W)

  val satp_mode = satp_val(xlen - 1, 60)
  val satp_asid = sapt_val(59, 44)
  val satp_ppn = sapt_val(43, 0)

  // To AXI Uncache Wrapper
  val pte_addr = RegInit(UInt(xlen.W), 0.U)
  val valid_access = RegInit(Bool(), false.B)

  // From AXI Uncache Wrapper
  val axi_valid = Wire(Bool())
  val axi_rdara = Wire(UInt(xlen.W))
  val pte_ppn = axi_rdata(53, 10)

  val level = Level
  val entry_recv = RegInit(UInt(xlen.W), 0.U)
  val next_pte_pa = RegInit(UInt(xlen.W), 0.U)
  val state = RegInit(UInt(s_idle.getWidth.W), s_idle)
  val next_state = Wire(UInt(s_idle.getWidth.W))
  val page_fault = Wire(Bool())
  val lev = RegInit(UInt(2.W), level - 1.U)
  val last_pte = RegInit(UInt(xlen.W), 0.U)
  val final_pa = Cat(Mux(lev === 0.U, last_pte(53, 10), Mux(lev === 1.U, Cat(last_pte(53, 19), io.va(20, 12)), Cat(last_pte(53, 28), io.va(29, 12)))), io.va(11, 0))

  // Some Constants
  val l_pte_size = 8.U
  val l_page_size = 12.U

  def illegal_va(va: UInt): Bool = {
    Mux(va(validVABits - 1), !va(xlen - 1, validVABits).andR, va(xlen - 1, validVABits).orR)
  }

  def is_pte_valid(pte: UInt): Bool = {
    !(pte(0) === 0.U || (pte(1) === 0.U && pte(2) === 1.U))
  }

  def is_final_pte(pte: UInt): Bool = {
    pte(1) === 1.U && pte(3) === 1.U
  }

  def pass_protection_check(pte: UInt): Bool = {
    // TODO step 6
    true.B
  }

  def misaligned_spage(lev: UInt, last_pte: UInt): Bool = {
    Mux(lev === 0.U, false.B, Mux(lev === 1.U, !(last_pte(18, 10).orR), !(last_pte(27, 10).orR)))
  }

  def pass_pmp_pma(last_pte: UInt): Bool = {
    // TODO
    true.B
  }

  // TODO ***CAUTION***
  // TODO If accessing pte violates a PMA or PMP check, raise an access-fault exception corresponding
  // TODO to the original access type.
  // Sequential Logic
  state := next_state
  when(state === idle) {
    last_pte := io.va
    when(next_state === s_busy) {
      pte_addr := (satp_ppn << l_page_size) + (io.va(38, 30) << l_pte_size)
      lev := level - 1.U
      valid_access := true.B
    }
  }.elsewhen(state === s_busy) {
    when(next_state === s_idle) {
      valid_access := false.B
    }.elsewhen(next_state === s_check) {
      valid_access := false.B
      last_pte := axi_rdata
    }.elsewhen(axi_valid) {
      pte_addr := (pte_ppn << l_page_size) + (Mux(lev === 2.U, io.va(29, 21), io.va(20, 12)) << l_pte_size)
      lev := lev - 1.U
    }
  }.elsewhen(state === s_check) {
    last_pte := final_pa
  }

  // Combinational Logic
  when(state === s_idle) {
    when(!io.valid || satp_mode === CSR.Bare) {
      next_state := s_idle
      page_fault := false.B
    }.otherwise {
      when(illegal_va(io.va)) {
        page_fault := true.B
        next_state := s_idle
      }.otherwise {
        page_fault := false.B
        next_state := s_busy
      }
    }
  }.elsewhen(state === s_busy) {
    when(axi_valid) {
      when(is_pte_valid(axi_rdata)) {
        when(is_final_pte(axi_rdata)) {
          page_fault := false.B
          next_state := s_check
        }.elsewhen(lev =/= 0.U) {
          page_fault := true.B
          next_state := s_idle
        }.otherwise {
          page_fault := false.B
          next_state := s_busy
        }
      }.otherwise {
        page_fault := true.B
        next_state := s_idle
      }
    }.otherwise {
      page_fault := false.B
      next_state := s_busy
    }
  }.elsewhen(state === s_check) {
    when(pass_protection_check(last_pte)) {
      when(misaligned_spage(lev, last_pte) || !pass_pmp_pma(last_pte)) {
        page_fault := true.B
        next_state := s_idle
      }.otherwise {
        page_fault := false.B
        next_state := s_finish
      }
    }.otherwise {
      page_fault := true.B
      next_state := s_idle
    }
  }.otherwise {
    page_fault := false.B
    next_state := s_idle
  }

  io.pf := page_fault
  io.af := false.B
  io.stall_req := next_state =/= s_idle
  io.pa := last_pte
}

