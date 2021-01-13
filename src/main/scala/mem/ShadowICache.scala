package mem

import chisel3._
import chisel3.util._
import rv64_nstage.core._
import rv64_nstage.control.ControlConst._
import bus._
import chisel3.util.experimental.BoringUtils
import device._
import utils._

// TODO Update and fill shadow bytes
class ShadowICache(implicit val cacheConfig: CacheConfig)
  extends Module
    with CacheParameters {
  val io = IO(new CacheIO)

  // Module Used
  val metaArray = List.fill(nWays)(SyncReadMem(nSets, new MetaData))
  val dataArray = List.fill(nWays)(SyncReadMem(nSets, new CacheLineData))
  val stall = Wire(Bool())
  val s2_need_forward = Wire(Bool())
  val write_meta = Wire(Vec(nWays, new MetaData))
  val write_data = Wire(Vec(nWays, new CacheLineData))
  val write_meta_tmp = Wire(Vec(nWays, new MetaData))
  val write_data_tmp = Wire(Vec(nWays, new CacheLineData))
  val need_write = Wire(Bool())

  /* stage1 signals */
  val s1_valid = WireDefault(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  val s1_index = Wire(UInt(indexLength.W))
  val s1_data = Wire(UInt(blockBits.W))
  val s1_wen = WireDefault(Bool(), false.B)
  val s1_memtype = Wire(UInt(xlen.W))

  s1_valid := io.in.req.valid
  s1_addr := io.in.req.bits.addr
  s1_index := s1_addr(indexLength + offsetLength - 1, offsetLength)
  s1_data := io.in.req.bits.data
  s1_wen := io.in.req.bits.wen
  s1_memtype := io.in.req.bits.memtype

  /* stage2 registers */
  val s2_valid = RegInit(Bool(), false.B)
  val s2_addr = RegInit(UInt(xlen.W), 0.U)
  val s2_data = RegInit(UInt(blockBits.W), 0.U)
  val s2_wen = RegInit(Bool(), false.B)
  val s2_memtype = RegInit(UInt(xlen.W), 0.U)
  val s2_meta = Wire(Vec(nWays, new MetaData))
  val s2_cacheline = Wire(Vec(nWays, new CacheLineData))
  val array_meta = Wire(Vec(nWays, new MetaData))
  val array_cacheline = Wire(Vec(nWays, new CacheLineData))
  val forward_meta = Wire(Vec(nWays, new MetaData))
  val forward_cacheline = Wire(Vec(nWays, new CacheLineData))
  val s2_index = Wire(UInt(indexLength.W))
  val s2_tag = Wire(UInt(tagLength.W))
  val read_index = Mux(io.in.stall, s2_index, s1_index)
  val s3_valid = RegInit(Bool(), false.B)
  val s3_index = Wire(UInt(indexLength.W))

  val last_s3_valid = RegNext(s3_valid)
  val last_s3_need_write = RegNext(need_write)
  val last_s3_index = RegNext(s3_index)
  val last_s3_write_line = RegNext(write_data)
  val last_s3_write_meta = RegNext(write_meta)
  val s2_hazard_low_prio = s2_valid && last_s3_valid && s2_index === last_s3_index && last_s3_need_write

  when(!io.in.stall) {
    s2_valid := s1_valid
    s2_addr := s1_addr
    s2_data := s1_data
    s2_wen := s1_wen
    s2_memtype := s1_memtype
  }
  for (i <- 0 until nWays) {
    array_meta(i) := metaArray(i).read(read_index, !(read_index === s3_index && need_write))
    array_cacheline(i) := dataArray(i).read(read_index, !(read_index === s3_index && need_write))
  }
  forward_meta := Mux(s2_hazard_low_prio, last_s3_write_meta, array_meta)
  forward_cacheline := Mux(s2_hazard_low_prio, last_s3_write_line, array_cacheline)
  s2_meta := Mux(s2_need_forward, write_meta, forward_meta)
  s2_cacheline := Mux(s2_need_forward, write_data, forward_cacheline)
  s2_index := s2_addr(indexLength + offsetLength - 1, offsetLength)
  s2_tag := s2_addr(xlen - 1, xlen - tagLength)

  val s2_hitVec = VecInit(s2_meta.map(m => m.valid && m.tag === s2_tag)).asUInt
  val s2_shadow_hitVec = VecInit(s2_meta.map(m => m.svalid && m.tag === s2_tag)).asUInt
  val s2_hit_index = PriorityEncoder(s2_hitVec)
  val s2_victim_index = policy.choose_victim(s2_meta)
  val s2_victim_vec = UIntToOH(s2_victim_index)
  val s2_ismmio = AddressSpace.isMMIO(s2_addr)
  val s2_hit = s2_hitVec.orR && !s2_ismmio
  val s2_read_shadow = (s2_addr + 2.U)(indexLength + offsetLength - 1, offsetLength) =/= s2_index
  val s2_shadow_hit = s2_shadow_hitVec.orR || !s2_read_shadow
  val s2_access_index = Mux(s2_hit, s2_hit_index, s2_victim_index)
  val s2_access_vec = UIntToOH(s2_access_index)

  /* stage3 registers */
  val s3_addr = RegInit(UInt(xlen.W), 0.U)
  val s3_data = RegInit(UInt(blockBits.W), 0.U)
  val s3_wen = RegInit(Bool(), false.B)
  val s3_memtype = RegInit(UInt(xlen.W), 0.U)
  val s3_meta = Reg(Vec(nWays, new MetaData))
  val s3_cacheline = Reg(Vec(nWays, new CacheLineData))
  val s3_tag = Wire(UInt(tagLength.W))
  val s3_lineoffset = Wire(UInt(lineLength.W))
  val s3_wordoffset = Wire(UInt((offsetLength - lineLength).W))
  val s3_access_index = Reg(chiselTypeOf(s2_access_index))
  val s3_access_vec = Reg(chiselTypeOf(s2_access_vec))
  val s3_hit = Reg(chiselTypeOf(s2_hit))
  val s3_shadow_hit = Reg(chiselTypeOf(s2_shadow_hit))
  val s3_read_shadow = Reg(chiselTypeOf(s2_read_shadow))

  when(!io.in.stall) {
    s3_valid := s2_valid
    s3_addr := s2_addr
    s3_data := s2_data
    s3_wen := s2_wen
    s3_memtype := s2_memtype
    s3_meta := s2_meta
    s3_cacheline := s2_cacheline
    s3_access_index := s2_access_index
    s3_access_vec := s2_access_vec
    s3_hit := s2_hit
    s3_shadow_hit := s2_shadow_hit
    s3_read_shadow := s2_read_shadow
  }
  s3_index := s3_addr(indexLength + offsetLength - 1, offsetLength)
  s3_tag := s3_addr(xlen - 1, xlen - tagLength)
  s3_lineoffset := s3_addr(offsetLength - 1, offsetLength - lineLength)
  s3_wordoffset := s3_addr(offsetLength - lineLength - 1, 0)

  val result = Wire(UInt(blockBits.W))
  val cacheline_meta = s3_meta(s3_access_index)
  val cacheline_data = s3_cacheline(s3_access_index)
  val s3_ismmio = AddressSpace.isMMIO(s3_addr)

  val s_idle :: s_memReadReq :: s_memReadResp :: s_mmioReq :: s_mmioResp :: s_finish :: s_flush :: Nil =
    Enum(7)
  val state = RegInit(s_flush)
  val read_address = Mux(!s3_hit, Cat(s3_tag, s3_index, 0.U(offsetLength.W)), Cat(Cat(s3_tag, s3_index) + 1.U, 0.U(offsetLength.W)))
  val flush_counter = Counter(nSets)
  val flush_finish = flush_counter.value === (nSets - 1).U

  val mem_valid = state === s_memReadResp && io.mem.resp.valid
  val mem_request_satisfied = (s3_hit && s3_shadow_hit) || mem_valid
  val mmio_request_satisfied = state === s_mmioResp && io.mmio.resp.valid
  val request_satisfied = mem_request_satisfied || mmio_request_satisfied
  val s2_hazard = s2_valid && s3_valid && s2_index === s3_index
  stall := s3_valid && !request_satisfied && state =/= s_finish // wait for data
  val external_stall = io.in.stall && !stall
  val hold_assert = external_stall && request_satisfied
  s2_need_forward := HoldCond(
    s2_hazard && mem_request_satisfied,
    hold_assert,
    state === s_finish
  )

  io.in.resp.valid := HoldCond(
    s3_valid && request_satisfied,
    hold_assert,
    state === s_finish
  )
  io.in.resp.bits.data := HoldCond(result, hold_assert, state === s_finish)
  io.in.req.ready := !stall
  io.in.flush_ready := state =/= s_flush || (state === s_flush && flush_finish)

  io.mem.stall := false.B
  io.mem.flush := false.B
  io.mem.req.valid := s3_valid && state === s_memReadReq
  io.mem.req.bits.addr := read_address
  io.mem.req.bits.data := DontCare
  io.mem.req.bits.wen := false.B
  io.mem.req.bits.memtype := DontCare
  io.mem.resp.ready := s3_valid && state === s_memReadResp

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
      when(io.in.flush || reset.asBool) {
        state := s_flush
      }.elsewhen(s3_valid && !s3_hit) {
        state := Mux(s3_ismmio, s_mmioReq, s_memReadReq)
      }.elsewhen(s3_valid && !s3_shadow_hit) {
        state := s_memReadReq
      }
    }
    is(s_memReadReq) { when(io.mem.req.fire()) { state := s_memReadResp } }
    is(s_memReadResp) {
      when(io.mem.resp.fire()) {
        state := Mux(external_stall, s_finish, s_idle)
      }
    }
    is(s_mmioReq) { when(io.mmio.req.fire()) { state := s_mmioResp } }
    is(s_mmioResp) {
      when(io.mmio.resp.fire()) {
        state := Mux(external_stall, s_finish, s_idle)
      }
    }
    is(s_finish) { when(!external_stall) { state := s_idle } }
    is(s_flush) { when(flush_finish) { state := s_idle } }
  }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * blockBits - 1, i * blockBits)
  }
  fetched_vec.shadow := fetched_data(16, 0)

  val target_data = Mux(s3_hit, cacheline_data, fetched_vec)
  val meta_index = Wire(UInt(indexLength.W))
  meta_index := DontCare
  result := DontCare
  write_data := HoldCond(write_data_tmp, hold_assert, state === s_finish)
  write_meta := HoldCond(write_meta_tmp, hold_assert, state === s_finish)
  write_data_tmp := DontCare
  write_meta_tmp := DontCare
  need_write := false.B
  when(s3_valid) {
    when(request_satisfied) {
      val result_data = Cat(target_data.data(s3_lineoffset + 1.U), target_data.data(s3_lineoffset))
      val result_shadow_data = Mux(s3_shadow_hit, target_data.shadow, fetched_data(15, 0))
      val offset = s3_wordoffset << 3
      val mask = WireDefault(UInt((16 + blockBits).W), 0.U)
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
        is(memDouble) { result := result_data }
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
          mem_result := Cat(Fill(32, 0.U), Mux(s3_read_shadow, Cat(result_shadow_data, real_data(15, 0)), real_data(31, 0)))
        }
      }
      result := Mux(s3_ismmio, io.mmio.resp.bits.data, mem_result)
      when(!s3_ismmio) {
        when(!s3_hit) {
          for (i <- 0 until nWays) {
            when(s3_access_index === i.U && !s3_hit) { // TODO UPDATE SHADOW DATA
              write_data_tmp(i) := target_data
              dataArray(i).write(s3_index, target_data)
              need_write := true.B
            }.otherwise {
              write_data_tmp(i) := s3_cacheline(i)
            }
          }
        }
        write_meta_tmp := policy.update_meta(s3_meta, s3_access_index)
        write_meta_tmp(s3_access_index).valid := true.B
        write_meta_tmp(s3_access_index).tag := s3_tag
        meta_index := s3_index
      }
    }
  }

  when(state === s_flush) {
    for (i <- 0 until nWays) {
      write_meta_tmp(i).valid := false.B
      write_meta_tmp(i).meta := 0.U
      write_meta_tmp(i).tag := 0.U
    }
    meta_index := flush_counter.value
    flush_counter.inc()
  }

  when(state === s_flush || (s3_valid && mem_request_satisfied)) {
    for (i <- 0 until nWays) {
      metaArray(i).write(meta_index, write_meta_tmp(i))
    }
  }

  // FixMe !!!!!!!! need to detect next page and may raise page fault
  val half_fetched = !s3_shadow_hit && !s3_hit
//  when (half_fetched && io.in.resp.valid) {
//    printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
//    printf("cache\n")
//  }
  BoringUtils.addSource(half_fetched, "half_fetched_if3")
}
