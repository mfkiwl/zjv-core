package rv64_nstage.mmu

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams
import utils._
import rv64_nstage.register.SATP
import rv64_nstage.register.CSR
import mem._
import device._

class PTWReq(implicit val mmuConfig: MMUConfig)
    extends Bundle
    with MMUParameters {
  val va = Output(UInt(xlen.W))
  val satp_ppn = Output(UInt(44.W))

  override def toPrintable: Printable =
    p"va = 0x${Hexadecimal(va)}, satp_ppn = 0x${Hexadecimal(satp_ppn)}"
}

class PTWResp(implicit val mmuConfig: MMUConfig)
    extends Bundle
    with MMUParameters {
  val pte = Output(UInt(xlen.W))
  val level = Output(UInt(2.W))
  val pf = Output(Bool())

  override def toPrintable: Printable =
    p"pte = 0x${Hexadecimal(pte)}, level = ${level}, pf = ${pf}"
}

class PTWIO(implicit val mmuConfig: MMUConfig)
    extends Bundle
    with MMUParameters {
  val req = Flipped(Valid(new PTWReq))
  val resp = Valid(new PTWResp)
  override def toPrintable: Printable =
    p"req: valid=${req.valid}, ${req.bits}\nresp: valid=${resp.valid}, ${resp.bits}"
}

class PTWalker(implicit val mmuConfig: MMUConfig)
    extends Module
    with MMUParameters {
  val io = IO(new Bundle {
    val in = new PTWIO
    val out = new MMUBackIO
  })

  // Some Constants
  val level = 3.U(2.W)
  val l_pte_size = 3.U // PTE SIZE = 8
  val l_page_size = 12.U // PAGE SIZE = 2^12

  def is_pte_valid(pte: UInt): Bool = {
    !(pte(0) === 0.U || (pte(1) === 0.U && pte(2) === 1.U))
  }

  def is_final_pte(pte: UInt): Bool = {
    pte(1) === 1.U || pte(3) === 1.U
  }

  val s_idle :: s_req :: s_resp :: Nil = Enum(3)
  val state = RegInit(s_idle)
  val finish = WireInit(false.B)
  val page_fault = WireInit(false.B)
  val lev = RegInit(UInt(2.W), level - 1.U)
  val last_pte = RegInit(UInt(xlen.W), 0.U)

  // L2 cache interface
  val pte_addr = Reg(UInt(xlen.W))
  val cache_rdata = MuxLookup(
    io.out.mmu.req.bits.addr(4, 3),
    "hdeadbeef".U,
    Seq(
      "b00".U -> io.out.mmu.resp.bits.data(63, 0),
      "b01".U -> io.out.mmu.resp.bits.data(2 * 64 - 1, 64),
      "b10".U -> io.out.mmu.resp.bits.data(3 * 64 - 1, 2 * 64),
      "b11".U -> io.out.mmu.resp.bits.data(4 * 64 - 1, 3 * 64)
    )
  )
  val pte_ppn = cache_rdata(53, 10)

  io.in.resp.valid := finish || page_fault
  io.in.resp.bits.pte := cache_rdata // last_pte
  io.in.resp.bits.level := lev
  io.in.resp.bits.pf := page_fault

  io.out.mmu.stall := false.B
  io.out.mmu.flush := false.B
  io.out.mmu.resp.ready := true.B
  io.out.mmu.req.bits.addr := pte_addr
  io.out.mmu.req.bits.data := DontCare
  io.out.mmu.req.valid := state === s_req // || state === s_resp
  io.out.mmu.req.bits.wen := false.B
  io.out.mmu.req.bits.memtype := DontCare

  switch(state) {
    is(s_idle) {
      when(io.in.req.valid) {
        state := s_req
        lev := level - 1.U
        last_pte := io.in.req.bits.va
        pte_addr := (io.in.req.bits.satp_ppn << l_page_size) + (io.in.req.bits
          .va(38, 30) << l_pte_size)
      }
    }
    is(s_req) {
      when(io.out.mmu.req.fire()) {
        state := s_resp
      }
    }
    is(s_resp) {
      when(io.out.mmu.resp.fire()) {
        when(is_pte_valid(cache_rdata)) {
          when(is_final_pte(cache_rdata)) {
            state := s_idle
            finish := true.B
            last_pte := cache_rdata
          }.elsewhen(lev === 0.U) {
            page_fault := true.B
          }.otherwise {
            state := s_req
            pte_addr := (pte_ppn << l_page_size) + (Mux(
              lev === 2.U,
              io.in.req.bits.va(29, 21),
              io.in.req.bits.va(20, 12)
            ) << l_pte_size)
          }
        }.otherwise {
          page_fault := true.B
          state := s_idle
        }
        lev := lev - 1.U
      }
    }
  }

  // if (pipeTrace || isdmmu) {
  //   when(GTimer() > 480000000.U) {
  //     printf(p"[${GTimer()}]: ${mmuName} PTW Debug Info\n")
  //     printf(p"state=${state}, lev=${lev}, last_pte=${Hexadecimal(last_pte)}\n")
  //     printf(
  //       p"cache_rdata=${Hexadecimal(cache_rdata)}, pte_ppn=${Hexadecimal(pte_ppn)}\n"
  //     )
  //     printf(p"io.in: ${io.in}\n")
  //     printf(p"io.out: ${io.out}\n")
  //     printf("-----------------------------------------------\n")
  //   }
  // }
}
