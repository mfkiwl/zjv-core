package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import rv64_3stage.ControlConst._
import bus._
import device._
import utils._

class ICacheForwardSplitReorg(implicit val cacheConfig: CacheConfig)
    extends Module
    with CacheParameters {
  val io = IO(new CacheIO)

  // Module Used
  val metaArray = List.fill(nWays)(Mem(nSets, new MetaData))
  val dataArray = List.fill(nWays)(Mem(nSets * nLine, UInt(blockBits.W)))
  val stall = Wire(Bool())
  val need_forward = Wire(Bool())
  val write_meta = Wire(Vec(nWays, new MetaData))
  val write_data = Wire(UInt(blockBits.W))

  /* stage1 signals */
  val s1_valid = WireInit(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  val s1_index = Wire(UInt(indexLength.W))
  val s1_data = Wire(UInt(blockBits.W))
  val s1_wen = WireInit(Bool(), false.B)
  val s1_memtype = Wire(UInt(xlen.W))
  val s1_data_index =
    s1_addr(indexLength + offsetLength - 1, offsetLength - lineLength)

  s1_valid := io.in.req.valid
  s1_addr := io.in.req.bits.addr
  s1_index := s1_addr(indexLength + offsetLength - 1, offsetLength)
  s1_data := io.in.req.bits.data
  s1_wen := io.in.req.bits.wen
  s1_memtype := io.in.req.bits.memtype

  /* stage2 registers */
  val s2_valid = RegInit(Bool(), false.B)
  val s2_addr = RegInit(UInt(xlen.W), 0.U)
  val s2_index = RegInit(UInt(indexLength.W), 0.U)
  val s2_data = RegInit(UInt(blockBits.W), 0.U)
  val s2_wen = RegInit(Bool(), false.B)
  val s2_memtype = RegInit(UInt(xlen.W), 0.U)
  val s2_meta = Reg(Vec(nWays, new MetaData))
  val s2_cacheline = Reg(Vec(nWays, UInt(blockBits.W)))
  val s2_tag = Wire(UInt(tagLength.W))
  val s2_lineoffset = Wire(UInt(lineLength.W))
  val s2_wordoffset = Wire(UInt((offsetLength - lineLength).W))
  val access_index = Wire(UInt((log2Ceil(nWays)).W))

  when(!io.in.stall) {
    s2_valid := s1_valid
    s2_addr := s1_addr
    s2_index := s1_index
    s2_data := s1_data
    s2_wen := s1_wen
    s2_memtype := s1_memtype
    when(need_forward) {
      s2_meta := write_meta
    }.otherwise {
      for (i <- 0 until nWays) {
        s2_meta(i) := metaArray(i).read(s1_index)
      }
    }
    // for (i <- 0 until nWays) {
    //   when(need_forward && data_hazard && i.U === access_index) {
    //     s2_cacheline(i) := write_data
    //   }.otherwise {
    //     s2_cacheline(i) := dataArray(i).read(s1_data_index)
    //   }
    // }
  }

  s2_tag := s2_addr(xlen - 1, xlen - tagLength)
  s2_lineoffset := s2_addr(offsetLength - 1, offsetLength - lineLength)
  s2_wordoffset := s2_addr(offsetLength - lineLength - 1, 0)
  val s2_data_index = Cat(s2_index, s2_lineoffset)

  val hitVec = VecInit(s2_meta.map(m => m.valid && m.tag === s2_tag)).asUInt
  val hit_index = PriorityEncoder(hitVec)
  val victim_index = policy.choose_victim(s2_meta)
  val victim_vec = UIntToOH(victim_index)
  val hit = hitVec.orR
  val result = Wire(UInt(blockBits.W))
  access_index := Mux(hit, hit_index, victim_index)
  val access_vec = UIntToOH(access_index)
  val cacheline_meta = s2_meta(access_index)
  val cacheline_data = s2_cacheline(access_index)

  val s_idle :: s_memReadReq :: s_memReadResp :: s_flush :: Nil = Enum(4)
  val state = RegInit(s_idle)
  val read_address = Cat(s2_tag, s2_index, 0.U(offsetLength.W))
  val flush_counter = Counter(nSets)
  val flush_finish = flush_counter.value === (nSets - 1).U

  val mem_valid = state === s_memReadResp && io.mem.resp.valid
  val request_satisfied = hit || mem_valid
  val meta_hazard = s1_valid && s2_valid && s1_index === s2_index
  val data_hazard = s1_valid && s2_valid && s1_data_index === s2_data_index
  stall := s2_valid && !request_satisfied // wait for data
  need_forward := meta_hazard && request_satisfied

  io.in.resp.valid := s2_valid && request_satisfied
  io.in.resp.bits.data := result
  io.in.req.ready := !stall // && !need_forward
  io.in.flush_ready := state =/= s_flush || (state === s_flush && flush_finish)

  io.mem.stall := false.B
  io.mem.flush := false.B
  io.mem.req.valid := s2_valid && (state === s_memReadReq || state === s_memReadResp)
  io.mem.req.bits.addr := read_address
  io.mem.req.bits.data := DontCare
  io.mem.req.bits.wen := false.B
  io.mem.req.bits.memtype := DontCare
  io.mem.resp.ready := s2_valid && state === s_memReadResp

  switch(state) {
    is(s_idle) {
      when(!hit && s2_valid && !io.in.flush) {
        state := s_memReadReq
      }.elsewhen(io.in.flush) {
        state := s_flush
      }
    }
    is(s_memReadReq) {
      when(io.mem.req.fire()) { state := s_memReadResp }
    }
    is(s_memReadResp) {
      when(io.mem.resp.fire()) { state := s_idle }
    }
    is(s_flush) { when(flush_finish) { state := s_idle } }
  }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * blockBits - 1, i * blockBits)
  }

  val target_data = Mux(hit, cacheline_data, fetched_vec.data(s2_lineoffset))
  result := DontCare
  write_data := DontCare
  write_meta := DontCare
  when(s2_valid) {
    when(request_satisfied) {
      val result_data = target_data
      val offset = s2_wordoffset << 3
      val mask = WireInit(UInt(blockBits.W), 0.U)
      val real_data = WireInit(UInt(blockBits.W), 0.U)
      switch(s2_memtype) {
        is(memXXX) { result := result_data }
        is(memByte) {
          mask := Fill(8, 1.U(1.W)) << offset
          real_data := (result_data & mask) >> offset
          result := Cat(Fill(56, real_data(7)), real_data(7, 0))
        }
        is(memHalf) {
          mask := Fill(16, 1.U(1.W)) << offset
          real_data := (result_data & mask) >> offset
          result := Cat(Fill(48, real_data(15)), real_data(15, 0))
        }
        is(memWord) {
          mask := Fill(32, 1.U(1.W)) << offset
          real_data := (result_data & mask) >> offset
          result := Cat(Fill(32, real_data(31)), real_data(31, 0))
        }
        is(memDouble) { result := result_data }
        is(memByteU) {
          mask := Fill(8, 1.U(1.W)) << offset
          real_data := (result_data & mask) >> offset
          result := Cat(Fill(56, 0.U), real_data(7, 0))
        }
        is(memHalfU) {
          mask := Fill(16, 1.U(1.W)) << offset
          real_data := (result_data & mask) >> offset
          result := Cat(Fill(48, 0.U), real_data(15, 0))
        }
        is(memWordU) {
          mask := Fill(32, 1.U(1.W)) << offset
          real_data := (result_data & mask) >> offset
          result := Cat(Fill(32, 0.U), real_data(31, 0))
        }
      }
      // write_data := Mux(hit, s2_cacheline, fetched_vec)
      write_data := target_data
      // for (i <- 0 until nWays) {
      //   when(access_index === i.U) {
      //     write_data(i) := Mux(hit, s2_cacheline(i), fetched_vec.data(i))
      //   }.otherwise {
      //     write_data(i) := s2_cacheline(i)
      //   }
      // }
      when(!hit) {
        for (i <- 0 until nLine) {
          dataArray(i).write(
            Cat(s2_index, i.U(lineLength.W)),
            fetched_vec.data(i)
          )
        }
      }
      // dataArray.write(s2_index, write_data, access_vec.asBools)
      write_meta := policy.update_meta(s2_meta, access_index)
      write_meta(access_index).valid := true.B
      write_meta(access_index).tag := s2_tag
      // metaArray.write(s2_index, write_meta)
      for (i <- 0 until nWays) {
        metaArray(i).write(s2_index, write_meta(i))
      }
      // printf(
      //   p"[${GTimer()}]: icache read: offset=${Hexadecimal(offset)}, mask=${Hexadecimal(mask)}, real_data=${Hexadecimal(real_data)}\n"
      // )
      // printf(p"\twrite_data=${write_data}\n")
      // printf(p"\twrite_meta=${write_meta}\n")
    }
  }

  when(state === s_flush) {
    val new_meta = Wire(Vec(nWays, new MetaData))
    for (i <- 0 until nWays) {
      new_meta(i).valid := false.B
      new_meta(i).meta := DontCare
      new_meta(i).tag := DontCare
    }
    // metaArray.write(flush_counter.value, new_meta)
    for (i <- 0 until nWays) {
      metaArray(i).write(s2_index, write_meta(i))
    }
    flush_counter.inc()
  }

  // printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  printf(
    "stall=%d, need_forward=%d, state=%d, hit=%d, result=%x\n",
    stall,
    need_forward,
    state,
    hit,
    result
  )
  printf(
    "flush_counter.value=%x, flush_finish=%d\n",
    flush_counter.value,
    flush_finish
  )
  printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
  printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
  printf("s2_valid=%d, s2_addr=%x, s2_index=%x\n", s2_valid, s2_addr, s2_index)
  printf("s2_data=%x, s2_wen=%d, s2_memtype=%d\n", s2_data, s2_wen, s2_memtype)
  printf(
    "s2_tag=%x, s2_lineoffset=%x, s2_wordoffset=%x\n",
    s2_tag,
    s2_lineoffset,
    s2_wordoffset
  )
  printf(p"hitVec=${hitVec}, access_index=${access_index}\n")
  printf(
    p"victim_index=${victim_index}, victim_vec=${victim_vec}, access_vec = ${access_vec}\n"
  )
  printf(p"s2_cacheline=${s2_cacheline}\n")
  printf(p"s2_meta=${s2_meta}\n")
  // printf(
  //   p"cacheline_data=${cacheline_data}, cacheline_meta=${cacheline_meta}\n"
  // )
  // printf(
  //   p"dataArray(s1_index)=${dataArray(s1_index)},metaArray(s1_index)=${metaArray(s1_index)}\n"
  // )
  printf(p"----------${cacheName} io.in----------\n")
  printf(p"${io.in}\n")
  printf(p"----------${cacheName} io.mem----------\n")
  printf(p"${io.mem}\n")
  printf("-----------------------------------------------\n")
}
