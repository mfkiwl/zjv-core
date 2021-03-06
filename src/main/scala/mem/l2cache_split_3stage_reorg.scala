package mem

import chisel3._
import chisel3.util._
import tile._
import tile.common.control.ControlConst._
import bus._
import device._
import utils._

class L2CacheSplit3StageReorg(val n_sources: Int = 1)(implicit
    val cacheConfig: CacheConfig
) extends Module
    with CacheParameters {
  val io = IO(new L2CacheIO(n_sources))

  val numWords = nWords / 2

  // Module Used
  val metaArray =
    List.fill(nWays)(Module(new S011HD1P_X128Y2D54_Wrapper(nSets, 54)))
  val dataArray = List.fill(nWays)(
    List.fill(numWords)(Module(new S011HD1P_X128Y2D128_Wrapper(nSets, xlen * 2)))
  )

  for (i <- 0 until nWays) {
    metaArray(i).io.A := 0.U
    metaArray(i).io.D := 0.U
    metaArray(i).io.WEN := true.B
    metaArray(i).io.CEN := false.B
    metaArray(i).io.CLK := clock
    for (j <- 0 until numWords) {
      dataArray(i)(j).io.A := 0.U
      dataArray(i)(j).io.D := 0.U
      dataArray(i)(j).io.WEN := true.B
      dataArray(i)(j).io.CEN := false.B
      dataArray(i)(j).io.CLK := clock
    }
  }

  val stall = Wire(Bool())

  val arbiter = Module(new L2CacheXbar(n_sources))
  for (i <- 0 until n_sources) {
    io.in(i) <> arbiter.io.in(i)
  }
  val current_request = arbiter.io.out

  /* stage1 signals */
  val s1_valid = WireDefault(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  val s1_index = WireDefault(UInt(indexLength.W), 0.U(indexLength.W))
  val s1_data = Wire(UInt(blockBits.W))
  val s1_wen = WireDefault(Bool(), false.B)
  val s1_memtype = Wire(UInt(xlen.W))

  s1_valid := current_request.req.valid
  s1_addr := current_request.req.bits.addr
  s1_index := s1_addr(indexLength + offsetLength - 1, offsetLength)
  s1_data := current_request.req.bits.data
  s1_wen := current_request.req.bits.wen
  s1_memtype := current_request.req.bits.memtype

  /* stage2 registers */
  val s2_valid = RegInit(Bool(), false.B)
  val s2_addr = RegInit(UInt(xlen.W), 0.U)
  val s2_data = RegInit(UInt(blockBits.W), 0.U)
  val s2_wen = RegInit(Bool(), false.B)
  val s2_meta = Wire(Vec(nWays, new MetaData))
  val s2_cacheline = Wire(Vec(nWays, new CacheLineData))
  val s2_tag = Wire(UInt(tagLength.W))

  when(!stall) {
    s2_valid := s1_valid
    s2_addr := s1_addr
    s2_data := s1_data
    s2_wen := s1_wen
  }
  for (i <- 0 until nWays) {
    s2_meta(i) := metaArray(i).io.Q.asTypeOf(new MetaData)
    val read_data = Wire(Vec(numWords, UInt((xlen * 2).W)))
    for (j <- 0 until numWords) {
      read_data(j) := dataArray(i)(j).io.Q
    }
    s2_cacheline(i) := read_data.asUInt.asTypeOf(new CacheLineData)
  }
  s2_tag := s2_addr(xlen - 1, xlen - tagLength)

  val s2_hitVec = VecInit(s2_meta.map(m => m.valid && m.tag === s2_tag)).asUInt
  val s2_hit_index = PriorityEncoder(s2_hitVec)
  val s2_victim_index = policy.choose_victim(s2_meta)
  val s2_victim_vec = UIntToOH(s2_victim_index)
  val s2_hit = s2_hitVec.orR
  val s2_access_index = Mux(s2_hit, s2_hit_index, s2_victim_index)
  val s2_access_vec = UIntToOH(s2_access_index)

  /* stage3 registers */
  val s3_valid = RegInit(Bool(), false.B)
  val s3_addr = RegInit(UInt(xlen.W), 0.U)
  val s3_data = RegInit(UInt(blockBits.W), 0.U)
  val s3_wen = RegInit(Bool(), false.B)
  val s3_meta = Reg(Vec(nWays, new MetaData))
  val s3_cacheline = Reg(Vec(nWays, new CacheLineData))
  val s3_index = WireDefault(UInt(indexLength.W), 0.U)
  val s3_tag = Wire(UInt(tagLength.W))
  val s3_lineoffset = Wire(UInt(lineLength.W))
  val s3_wordoffset = Wire(UInt((offsetLength - lineLength).W))
  val s3_access_index = Reg(chiselTypeOf(s2_access_index))
  val s3_access_vec = Reg(chiselTypeOf(s2_access_vec))
  val s3_hit = Reg(chiselTypeOf(s2_hit))

  when(!stall) {
    s3_valid := s2_valid
    s3_addr := s2_addr
    s3_data := s2_data
    s3_wen := s2_wen
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
  val flush_counter = Counter(nSets)
  val flush_finish = flush_counter.value === (nSets - 1).U

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_flush :: Nil =
    Enum(6)
  val state = RegInit(s_flush)
  val read_address = Cat(s3_tag, s3_index, 0.U(offsetLength.W))
  val write_address = Cat(cacheline_meta.tag, s3_index, 0.U(offsetLength.W))
  val mem_valid = state === s_memReadResp && io.mem.resp.valid
  val request_satisfied = s3_hit || mem_valid
  stall := s3_valid && !request_satisfied // wait for data

  current_request.resp.valid := s3_valid && request_satisfied
  current_request.resp.bits.data := result
  current_request.req.ready := !stall && state =/= s_flush
  current_request.flush_ready := true.B

  io.mem.stall := false.B
  io.mem.flush := false.B
  io.mem.req.valid := s3_valid && (state === s_memReadReq || state === s_memWriteReq)
  io.mem.req.bits.addr := Mux(
    state === s_memWriteReq || state === s_memWriteResp,
    write_address,
    read_address
  )
  io.mem.req.bits.data := cacheline_data.asUInt
  io.mem.req.bits.wen := state === s_memWriteReq || state === s_memWriteResp
  io.mem.req.bits.memtype := DontCare
  io.mem.resp.ready := s3_valid && (state === s_memReadResp || state === s_memWriteResp)

  switch(state) {
    is(s_idle) {
      when(reset.asBool) {
        state := s_flush
      }.elsewhen(!s3_hit && s3_valid) {
        state := Mux(
          cacheline_meta.valid && cacheline_meta.dirty,
          s_memWriteReq,
          s_memReadReq
        )
      }
    }
    is(s_memReadReq) { when(io.mem.req.fire()) { state := s_memReadResp } }
    is(s_memReadResp) { when(io.mem.resp.fire()) { state := s_idle } }
    is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
    is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_memReadReq } }
    is(s_flush) { when(flush_finish) { state := s_idle } }
  }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * blockBits - 1, i * blockBits)
  }

  val target_data = Mux(s3_hit, cacheline_data, fetched_vec)
  val write_meta = Wire(Vec(nWays, new MetaData))
  val new_data = Wire(new CacheLineData)
  val meta_index = Wire(UInt(indexLength.W))
  meta_index := DontCare
  result := DontCare
  write_meta := DontCare
  new_data := DontCare
  when(s3_valid) {
    when(request_satisfied) {
      when(s3_wen) {
        val write_data = Wire(Vec(nWays, new CacheLineData))
        new_data := target_data
        new_data.data(s3_lineoffset) := s3_data
        for (i <- 0 until nWays) {
          when(s3_access_index === i.U) {
            write_data(i) := new_data
          }.otherwise {
            write_data(i) := s3_cacheline(i)
          }
        }
        write_meta := policy.update_meta(s3_meta, s3_access_index)
        write_meta(s3_access_index).valid := true.B
        write_meta(s3_access_index).dirty := true.B
        write_meta(s3_access_index).tag := s3_tag
        meta_index := s3_index
        // printf(
        //   p"l2cache write: s3_index=${s3_index}, s3_access_index=${s3_access_index}\n"
        // )
        // printf(p"\tnew_data=${new_data}\n")
        // printf(p"\twrite_meta=${write_meta}\n")
      }.otherwise {
        val result_data = target_data.data(s3_lineoffset)
        val write_data = Wire(Vec(nWays, new CacheLineData))
        write_data := DontCare
        result := result_data
        new_data := target_data
        when(!s3_hit) {
          for (i <- 0 until nWays) {
            when(s3_access_index === i.U) {
              write_data(i) := target_data
            }.otherwise {
              write_data(i) := s3_cacheline(i)
            }
          }
        }
        write_meta := policy.update_meta(s3_meta, s3_access_index)
        write_meta(s3_access_index).valid := true.B
        when(!s3_hit) {
          write_meta(s3_access_index).dirty := false.B
        }
        write_meta(s3_access_index).tag := s3_tag
        meta_index := s3_index
        // printf(
        //   p"l2cache read update: s3_index=${s3_index}, s3_access_index=${s3_access_index}\n"
        // )
        // printf(p"\ttarget_data=${target_data}\n")
        // printf(p"\twrite_meta=${write_meta}\n")
      }
    }
  }

  when(state === s_flush) {
    for (i <- 0 until nWays) {
      write_meta(i).valid := false.B
      write_meta(i).dirty := false.B
      write_meta(i).meta := 0.U
      write_meta(i).tag := 0.U
    }
    meta_index := flush_counter.value
    flush_counter.inc()
  }

  when(state === s_flush || (s3_valid && request_satisfied)) {
    for (i <- 0 until nWays) {
      metaArray(i).io.A := meta_index
      metaArray(i).io.D := write_meta(i).asUInt
      metaArray(i).io.WEN := false.B
      // metaArray(i).write(meta_index, write_meta(i))
    }
  }.otherwise {
    for (i <- 0 until nWays) {
      metaArray(i).io.A := s1_index
    }
  }

  when(s3_valid && request_satisfied) {
    for (i <- 0 until nWays) {
      when(s3_access_index === i.U) {
        val db_data = new_data.data.asUInt.asTypeOf(Vec(numWords, UInt((xlen * 2).W)))
        for (j <- 0 until numWords) {
          dataArray(i)(j).io.A := s3_index
          dataArray(i)(j).io.D := db_data(j)
          dataArray(i)(j).io.WEN := false.B
          // dataArray(i)(j).write(s3_index, db_data(j))
        }
      }
    }
  }.otherwise {
    for (i <- 0 until nWays) {
      for (j <- 0 until numWords) {
        dataArray(i)(j).io.A := s1_index
      }
    }
  }

  // printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  // printf("state=%d, stall=%d, s3_hit=%d, result=%x\n", state, stall, s3_hit, result)
  // printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
  // printf("s1_data=%x, s1_wen=%d\n", s1_data, s1_wen)
  // printf("s2_valid=%d, s2_addr=%x, s2_index=%x\n", s2_valid, s2_addr, s2_addr(indexLength + offsetLength - 1, offsetLength))
  // printf("s2_data=%x, s2_wen=%d\n", s2_data, s2_wen)
  // printf("s3_valid=%d, s3_addr=%x, s3_index=%x\n", s3_valid, s3_addr, s3_index)
  // printf("s3_data=%x, s3_wen=%d\n", s3_data, s3_wen)
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
  // printf(p"s2_cacheline=${s2_cacheline}\n")
  // printf(p"s2_meta=${s2_meta}\n")
  // printf(p"s3_cacheline=${s3_cacheline}\n")
  // printf(p"s3_meta=${s3_meta}\n")
  // printf(p"----------${cacheName} io.mem----------\n")
  // printf(p"${io.mem}\n")
  // printf("-----------------------------------------------\n")
}
