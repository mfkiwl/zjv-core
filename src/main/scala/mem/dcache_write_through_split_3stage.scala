package mem

import chisel3._
import chisel3.util._
import rv64_nstage.core._
import rv64_nstage.control.ControlConst._
import bus._
import device._
import utils._

class DCacheWriteThroughSplit3Stage(implicit val cacheConfig: CacheConfig)
    extends Module
    with CacheParameters {
  val io = IO(new CacheIO)

  // Module Used
  val metaArray = List.fill(nWays)(SyncReadMem(nSets, new MetaData))
  val dataArray = List.fill(nWays)(SyncReadMem(nSets, new CacheLineData))
  val stall = Wire(Bool())
  val s2_need_forward = Wire(Bool())
  val write_meta = Wire(Vec(nWays, new MetaData))
  val write_data = Reg(Vec(nWays, new CacheLineData))
  val writeData = Wire(Vec(nWays, new CacheLineData))
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
  val last_s3_write_line = RegNext(writeData)
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
  s2_cacheline := Mux(s2_need_forward, writeData, forward_cacheline)
  s2_index := s2_addr(indexLength + offsetLength - 1, offsetLength)
  s2_tag := s2_addr(xlen - 1, xlen - tagLength)

  val s2_hitVec = VecInit(s2_meta.map(m => m.valid && m.tag === s2_tag)).asUInt
  val s2_hit_index = PriorityEncoder(s2_hitVec)
  val s2_victim_index = policy.choose_victim(s2_meta)
  val s2_victim_vec = UIntToOH(s2_victim_index)
  val s2_ismmio = AddressSpace.isMMIO(s2_addr)
  val s2_hit = s2_hitVec.orR && !s2_ismmio
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
  }
  s3_index := s3_addr(indexLength + offsetLength - 1, offsetLength)
  s3_tag := s3_addr(xlen - 1, xlen - tagLength)
  s3_lineoffset := s3_addr(offsetLength - 1, offsetLength - lineLength)
  s3_wordoffset := s3_addr(offsetLength - lineLength - 1, 0)

  val result = Wire(UInt(blockBits.W))
  val cacheline_meta = s3_meta(s3_access_index)
  val cacheline_data = s3_cacheline(s3_access_index)
  val s3_ismmio = AddressSpace.isMMIO(s3_addr)
  val flush_counter = Counter(nSets)
  val flush_finish = flush_counter.value === (nSets - 1).U

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_flush :: Nil =
    Enum(8)
  val state = RegInit(s_flush)
  val read_address = Cat(s3_addr(xlen - 1, offsetLength), 0.U(offsetLength.W))
  val write_address =
    Cat(Mux(s3_hit, cacheline_meta.tag, s3_tag), s3_index, 0.U(offsetLength.W))
  val mem_read_valid = state === s_memReadResp && io.mem.resp.valid
  val mem_write_valid = state === s_memWriteResp && io.mem.resp.valid
  val mem_read_request_satisfied = !s3_wen && (s3_hit || mem_read_valid)
  val mem_write_request_satisfied = mem_write_valid
  val mem_request_satisfied =
    mem_read_request_satisfied || mem_write_request_satisfied
  val mmio_request_satisfied = state === s_mmioResp && io.mmio.resp.valid
  val request_satisfied = mem_request_satisfied || mmio_request_satisfied
  val s2_hazard = s2_valid && s3_valid && s2_index === s3_index
  stall := s3_valid && !request_satisfied // wait for data
  s2_need_forward := s2_hazard && mem_request_satisfied

  io.in.resp.valid := s3_valid && request_satisfied
  io.in.resp.bits.data := result
  io.in.req.ready := !stall
  io.in.flush_ready := state =/= s_flush || (state === s_flush && flush_finish)

  io.mem.stall := false.B
  io.mem.flush := false.B
  io.mem.req.valid := s3_valid && (state === s_memReadReq || state === s_memReadResp || state === s_memWriteReq || state === s_memWriteResp)
  io.mem.req.bits.addr := Mux(
    state === s_memWriteReq || state === s_memWriteResp,
    write_address,
    read_address
  )
  io.mem.req.bits.data := write_data(s3_access_index).data.asUInt
  io.mem.req.bits.wen := state === s_memWriteReq || state === s_memWriteResp
  io.mem.req.bits.memtype := DontCare
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
      when(reset.asBool) {
        state := s_flush
      }.elsewhen(s3_valid) {
        when(!s3_hit) {
          state := Mux(s3_ismmio, s_mmioReq, s_memReadReq)
        }.elsewhen(s3_wen) {
          state := s_memWriteReq
        }
      }
    }
    is(s_memReadReq) { when(io.mem.req.fire()) { state := s_memReadResp } }
    is(s_memReadResp) {
      when(io.mem.resp.fire()) {
        when(s3_wen) {
          state := s_memWriteReq
        }.otherwise {
          state := s_idle
        }
      }
    }
    is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
    is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_idle } }
    is(s_mmioReq) { when(io.mmio.req.fire()) { state := s_mmioResp } }
    is(s_mmioResp) { when(io.mmio.resp.fire()) { state := s_idle } }
    is(s_flush) { when(flush_finish) { state := s_idle } }
  }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * blockBits - 1, i * blockBits)
  }

  val target_data = Mux(s3_hit, cacheline_data, fetched_vec)
  val new_data = Wire(new CacheLineData)
  val meta_index = Wire(UInt(indexLength.W))
  meta_index := DontCare
  result := DontCare
  writeData := DontCare
  write_meta := DontCare
  new_data := DontCare
  when(s3_valid) {
    when(s3_wen) {
      when(s3_hit || mem_read_valid) {
        val filled_data = WireDefault(UInt(blockBits.W), 0.U(blockBits.W))
        val offset = s3_wordoffset << 3
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
        new_data := target_data
        new_data.data(
          s3_lineoffset
        ) := (mask & filled_data) | (~mask & target_data.data(s3_lineoffset))
        for (i <- 0 until nWays) {
          when(s3_access_index === i.U) {
            writeData(i) := new_data
          }.otherwise {
            writeData(i) := s3_cacheline(i)
          }
        }
        write_data := writeData
        write_meta := policy.update_meta(s3_meta, s3_access_index)
        write_meta(s3_access_index).valid := true.B
        // write_meta(s3_access_index).dirty := false.B
        write_meta(s3_access_index).tag := s3_tag
        meta_index := s3_index
        // printf(
        //   p"[${GTimer()}] dcache write: offset=${Hexadecimal(offset)}, mask=${Hexadecimal(mask)}, filled_data=${Hexadecimal(filled_data)}\n"
        // )
        // printf(p"\twriteData=${writeData}\n")
        // printf(p"\twrite_meta=${write_meta}\n")
      }
    }.otherwise {
      when(s3_hit || mem_read_valid || mmio_request_satisfied) {
        val result_data = target_data.data(s3_lineoffset)
        val offset = s3_wordoffset << 3
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
        result := Mux(s3_ismmio, io.mmio.resp.bits.data, mem_result)
        // printf(
        //   p"[${GTimer()}] dcache read: offset=${Hexadecimal(offset)}, mask=${Hexadecimal(mask)}, real_data=${Hexadecimal(real_data)}\n"
        // )
        when(!s3_ismmio) {
          when(!s3_hit) {
            for (i <- 0 until nWays) {
              when(s3_access_index === i.U) {
                writeData(i) := target_data
              }.otherwise {
                writeData(i) := s3_cacheline(i)
              }
            }
          }
          new_data := target_data
          write_meta := policy.update_meta(s3_meta, s3_access_index)
          write_meta(s3_access_index).valid := true.B
          write_meta(s3_access_index).tag := s3_tag
          meta_index := s3_index
          // printf(
          //   p"[${GTimer()}] dcache read: mask=${Hexadecimal(mask)}, mem_result=${Hexadecimal(mem_result)}, s3_index=0x${Hexadecimal(s3_index)}\n"
          // )
          // printf(p"\twriteData=${writeData}\n")
          // printf(p"\twrite_meta=${write_meta}\n")
        }
      }
    }
  }

  when(state === s_flush) {
    for (i <- 0 until nWays) {
      write_meta(i).valid := false.B
      // write_meta(i).dirty := false.B
      write_meta(i).meta := 0.U
      write_meta(i).tag := 0.U
    }
    meta_index := flush_counter.value
    flush_counter.inc()
  }

  when(
    state === s_flush || (s3_valid && ((s3_wen && ((s3_hit && state === s_idle) || (!s3_hit && mem_read_valid))) || (!s3_wen && !s3_ismmio && (s3_hit || mem_read_valid))))
  ) {
    for (i <- 0 until nWays) {
      metaArray(i).write(meta_index, write_meta(i))
    }
  }

  need_write := s3_valid && ((s3_wen && ((s3_hit && state === s_idle) || (!s3_hit && mem_read_valid))) || (!s3_wen && !s3_ismmio && mem_read_valid))
  when(need_write) {
    for (i <- 0 until nWays) {
      when(s3_access_index === i.U) {
        dataArray(i).write(s3_index, new_data)
      }
    }
  }

  // printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  // printf(
  //   "stall=%d, s2_need_forward=%d, state=%d, s3_ismmio=%d, s3_hit=%d, result=%x\n",
  //   stall,
  //   s2_need_forward,
  //   state,
  //   s3_ismmio,
  //   s3_hit,
  //   result
  // )
  // printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
  // printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
  // printf("s2_valid=%d, s2_addr=%x, s2_index=%x\n", s2_valid, s2_addr, s2_index)
  // printf("s2_data=%x, s2_wen=%d, s2_memtype=%d\n", s2_data, s2_wen, s2_memtype)
  // printf("s3_valid=%d, s3_addr=%x, s3_index=%x\n", s3_valid, s3_addr, s3_index)
  // printf("s3_data=%x, s3_wen=%d, s3_memtype=%d\n", s3_data, s3_wen, s3_memtype)
  // printf(
  //   "s3_tag=%x, s3_lineoffset=%x, s3_wordoffset=%x\n",
  //   s3_tag,
  //   s3_lineoffset,
  //   s3_wordoffset
  // )
  // printf(p"s2_hitVec=${s2_hitVec}, s3_access_index=${s3_access_index}\n")
  // printf(
  //   p"s2_victim_index=${s2_victim_index}, s2_victim_vec=${s2_victim_vec}, s3_access_vec = ${s3_access_vec}\n"
  // )
  // printf(p"last_s3_valid=${last_s3_valid}, last_s3_need_write=${last_s3_need_write}, last_s3_index=${last_s3_index}, s2_hazard_low_prio=${s2_hazard_low_prio}\n")
  // printf(p"last_s3_write_line=${last_s3_write_line}\n")
  // printf(p"last_s3_write_meta=${last_s3_write_meta}\n")
  // printf(p"array_cacheline=${array_cacheline}\n")
  // printf(p"array_meta=${array_meta}\n")
  // printf(p"forward_cacheline=${forward_cacheline}\n")
  // printf(p"forward_meta=${forward_meta}\n")
  // printf(p"s2_cacheline=${s2_cacheline}\n")
  // printf(p"s2_meta=${s2_meta}\n")
  // printf(p"s3_cacheline=${s3_cacheline}\n")
  // printf(p"s3_meta=${s3_meta}\n")
  // printf(p"----------${cacheName} io.in----------\n")
  // printf(p"${io.in}\n")
  // printf(p"----------${cacheName} io.mem----------\n")
  // printf(p"${io.mem}\n")
  // printf(p"----------${cacheName} io.mmio----------\n")
  // printf(p"${io.mmio}\n")
  // printf("-----------------------------------------------\n")
}
