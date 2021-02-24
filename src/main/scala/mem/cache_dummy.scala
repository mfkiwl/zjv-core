package mem

import chisel3._
import chisel3.util._
import tile._
import config.projectConfig
import tile.common.control.ControlConst._
import bus._
import device._
import utils._

class CacheDummy(implicit
    val cacheConfig: CacheConfig
) extends Module
    with CacheParameters {
  val io = IO(new CacheIO)

  // Module Used
  val stall = Wire(Bool())
  val write_data = Wire(UInt(xlen.W))

  /* stage1 signals */
  val s1_valid = WireDefault(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  val s1_data = Wire(UInt(blockBits.W))
  val s1_wen = WireDefault(Bool(), false.B)
  val s1_memtype = Wire(UInt(xlen.W))

  s1_valid := io.in.req.valid
  s1_addr := io.in.req.bits.addr
  s1_data := io.in.req.bits.data
  s1_wen := io.in.req.bits.wen
  s1_memtype := io.in.req.bits.memtype

  /* stage2 registers */
  val s2_valid = RegInit(Bool(), false.B)
  val s2_addr = RegInit(UInt(xlen.W), 0.U)
  val s2_data = RegInit(UInt(blockBits.W), 0.U)
  val s2_wen = RegInit(Bool(), false.B)
  val s2_memtype = RegInit(UInt(xlen.W), 0.U)

  when(!io.in.stall) {
    s2_valid := s1_valid
    s2_addr := s1_addr
    s2_data := s1_data
    s2_wen := s1_wen
    s2_memtype := s1_memtype
  }

  /* stage3 registers */
  val s3_valid = RegInit(Bool(), false.B)
  val s3_addr = RegInit(UInt(xlen.W), 0.U)
  val s3_data = RegInit(UInt(blockBits.W), 0.U)
  val s3_wen = RegInit(Bool(), false.B)
  val s3_memtype = RegInit(UInt(xlen.W), 0.U)

  when(!io.in.stall) {
    s3_valid := s2_valid
    s3_addr := s2_addr
    s3_data := s2_data
    s3_wen := s2_wen
    s3_memtype := s2_memtype
  }

  val result = Wire(UInt(blockBits.W))
  val s3_ismmio = AddressSpace.isMMIO(s3_addr)

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_finish :: Nil =
    Enum(8)
  val state = RegInit(s_idle)

  val mem_valid = (state === s_memReadResp || state === s_memWriteResp) && io.mem.resp.valid
  val mem_request_satisfied = mem_valid
  val mmio_request_satisfied = state === s_mmioResp && io.mmio.resp.valid
  val request_satisfied = mem_request_satisfied || mmio_request_satisfied
  stall := s3_valid && !request_satisfied && state =/= s_finish
  val external_stall = io.in.stall && !stall
  val hold_assert = external_stall && request_satisfied

  io.in.resp.valid := HoldCond(
    s3_valid && request_satisfied,
    hold_assert,
    state === s_finish
  )
  io.in.resp.bits.data := HoldCond(result, hold_assert, state === s_finish)
  io.in.req.ready := !stall
  io.in.flush_ready := true.B

  io.mem.stall := false.B
  io.mem.flush := false.B
  io.mem.req.valid := s3_valid && (state === s_memReadReq || state === s_memWriteReq)
  io.mem.req.bits.addr := s3_addr
  io.mem.req.bits.data := s3_data
  io.mem.req.bits.wen := s3_wen
  io.mem.req.bits.memtype := s3_memtype
  io.mem.resp.ready := s3_valid && (state === s_memReadResp || state === s_memWriteResp)

  io.mmio.stall := false.B
  io.mmio.flush := false.B
  io.mmio.req.valid := s3_valid && state === s_mmioReq
  io.mmio.req.bits.addr := s3_addr
  io.mmio.req.bits.data := s3_data
  io.mmio.req.bits.wen := s3_wen
  io.mmio.req.bits.memtype := s3_memtype
  io.mmio.resp.ready := s3_valid && state === s_mmioResp

  switch(state) {
    is(s_idle) {
      when(s3_valid) {
        state := Mux(
          s3_ismmio,
          s_mmioReq,
          Mux(s3_wen, s_memWriteReq, s_memReadReq)
        )
      }
    }
    is(s_memReadReq) { when(io.mem.req.fire()) { state := s_memReadResp } }
    is(s_memReadResp) {
      when(io.mem.resp.fire()) {
        state := Mux(external_stall, s_finish, s_idle)
      }
    }
    is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
    is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_idle } }
    is(s_mmioReq) { when(io.mmio.req.fire()) { state := s_mmioResp } }
    is(s_mmioResp) {
      when(io.mmio.resp.fire()) {
        state := Mux(external_stall, s_finish, s_idle)
      }
    }
    is(s_finish) { when(!external_stall) { state := s_idle } }
  }

  val target_data = io.mem.resp.bits.data
  result := DontCare
  write_data := DontCare
  when(s3_valid) {
    when(s3_wen) {
      val filled_data = WireDefault(UInt(blockBits.W), 0.U(blockBits.W))
      val offset = s3_addr(2, 0) << 3
      val mask = WireDefault(UInt(blockBits.W), 0.U)
      switch(s3_memtype) {
        is(memXXX) {
          filled_data := s3_data
          mask := Fill(blockBits, 1.U(1.W)) << offset
        }
        is(memByte) {
          filled_data := Fill(8, s3_data(7, 0))
          mask := Fill(8, 1.U(1.W)) << offset
        }
        is(memHalf) {
          filled_data := Fill(4, s3_data(15, 0))
          mask := Fill(16, 1.U(1.W)) << offset
        }
        is(memWord) {
          filled_data := Fill(2, s3_data(31, 0))
          mask := Fill(32, 1.U(1.W)) << offset
        }
        is(memDouble) {
          filled_data := s3_data
          mask := Fill(blockBits, 1.U(1.W)) << offset
        }
        is(memByteU) {
          filled_data := Fill(8, s3_data(7, 0))
          mask := Fill(8, 1.U(1.W)) << offset
        }
        is(memHalfU) {
          filled_data := Fill(4, s3_data(15, 0))
          mask := Fill(16, 1.U(1.W)) << offset
        }
        is(memWordU) {
          filled_data := Fill(2, s3_data(31, 0))
          mask := Fill(32, 1.U(1.W)) << offset
        }
      }
      write_data := filled_data
    }.otherwise {
      when(request_satisfied) {
        val result_data = target_data
        val offset = s3_addr(2, 0) << 3
        val mask = WireDefault(UInt(blockBits.W), 0.U)
        val real_data = WireDefault(UInt(blockBits.W), 0.U)
        val mem_result = WireDefault(UInt(blockBits.W), 0.U)
        switch(s3_memtype) {
          is(memXXX) { mem_result := result_data }
          is(memByte) {
            mask := Fill(8, 1.U(1.W)) << offset
            real_data := (result_data & mask) >> offset
            mem_result := Cat(Fill(56, real_data(7)), real_data(7, 0))
          }
          is(memHalf) {
            mask := Fill(16, 1.U(1.W)) << offset
            real_data := (result_data & mask) >> offset
            mem_result := Cat(Fill(48, real_data(15)), real_data(15, 0))
          }
          is(memWord) {
            mask := Fill(32, 1.U(1.W)) << offset
            real_data := (result_data & mask) >> offset
            mem_result := Cat(Fill(32, real_data(31)), real_data(31, 0))
          }
          is(memDouble) { mem_result := result_data }
          is(memByteU) {
            mask := Fill(8, 1.U(1.W)) << offset
            real_data := (result_data & mask) >> offset
            mem_result := Cat(Fill(56, 0.U), real_data(7, 0))
          }
          is(memHalfU) {
            mask := Fill(16, 1.U(1.W)) << offset
            real_data := (result_data & mask) >> offset
            mem_result := Cat(Fill(48, 0.U), real_data(15, 0))
          }
          is(memWordU) {
            mask := Fill(32, 1.U(1.W)) << offset
            real_data := (result_data & mask) >> offset
            mem_result := Cat(Fill(32, 0.U), real_data(31, 0))
          }
        }
        result := Mux(s3_ismmio, io.mmio.resp.bits.data, io.mem.resp.bits.data)
        // result := Mux(s3_ismmio, io.mmio.resp.bits.data, mem_result)
      }
    }
  }
  // result := Mux(s3_ismmio, io.mmio.resp.bits.data, io.mem.resp.bits.data)

  // printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  // printf(
  //   "stall=%d, state=%d, result=%x\n",
  //   stall,
  //   state,
  //   result
  // )
  // printf("s1_valid=%d, s1_addr=%x\n", s1_valid, s1_addr)
  // printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
  // printf("s2_valid=%d, s2_addr=%x\n", s2_valid, s2_addr)
  // printf("s2_data=%x, s2_wen=%d, s2_memtype=%d\n", s2_data, s2_wen, s2_memtype)
  // printf("s3_valid=%d, s3_addr=%x\n", s3_valid, s3_addr)
  // printf("s3_data=%x, s3_wen=%d, s3_memtype=%d\n", s3_data, s3_wen, s3_memtype)
  // printf(p"----------${cacheName} io.in----------\n")
  // printf(p"${io.in}\n")
  // printf(p"----------${cacheName} io.mem----------\n")
  // printf(p"${io.mem}\n")
  // printf(p"----------${cacheName} io.mmio----------\n")
  // printf(p"${io.mmio}\n")
  // printf("-----------------------------------------------\n")
}
