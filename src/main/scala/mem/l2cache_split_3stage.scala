package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import rv64_3stage.ControlConst._
import bus._
import device._
import utils._

class L2CacheSplit3Stage(val n_sources: Int = 1)(implicit
    val cacheConfig: CacheConfig
) extends Module
    with CacheParameters {
  val io = IO(new L2CacheIO(n_sources))

  // Module Used
  val metaArray = List.fill(nWays)(SyncReadMem(nSets, new MetaData))
  val dataArray = List.fill(nWays)(SyncReadMem(nSets, new CacheLineData))
  val stall = Wire(Bool())

  val arbiter = Module(new L2CacheXbar(n_sources))
  for (i <- 0 until n_sources) {
    io.in(i) <> arbiter.io.in(i)
  }
  val current_request = arbiter.io.out

  /* stage1 signals */
  val s1_valid = WireInit(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  val s1_index = Wire(UInt(indexLength.W))
  val s1_data = Wire(UInt(blockBits.W))
  val s1_wen = WireInit(Bool(), false.B)
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
  val s2_index = RegInit(UInt(indexLength.W), 0.U)
  val s2_data = RegInit(UInt(blockBits.W), 0.U)
  val s2_wen = RegInit(Bool(), false.B)
  val s2_memtype = RegInit(UInt(xlen.W), 0.U)
  val s2_meta = Wire(Vec(nWays, new MetaData))
  val s2_cacheline = Wire(Vec(nWays, new CacheLineData))
  val s2_tag = Wire(UInt(tagLength.W))

  when(!stall) {
    s2_valid := s1_valid
    s2_addr := s1_addr
    s2_index := s1_index
    s2_data := s1_data
    s2_wen := s1_wen
    s2_memtype := s1_memtype
  }
  for (i <- 0 until nWays) {
    s2_meta(i) := metaArray(i).read(s1_index)
    s2_cacheline(i) := dataArray(i).read(s1_index)
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
  val s3_index = RegInit(UInt(indexLength.W), 0.U)
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

  when(!stall) {
    s3_valid := s2_valid
    s3_addr := s2_addr
    s3_index := s2_index
    s3_data := s2_data
    s3_wen := s2_wen
    s3_memtype := s2_memtype
    s3_meta := s2_meta
    s3_cacheline := s2_cacheline
    s3_access_index := s2_access_index
    s3_access_vec := s2_access_vec
    s3_hit := s2_hit
  }
  s3_tag := s3_addr(xlen - 1, xlen - tagLength)
  s3_lineoffset := s3_addr(offsetLength - 1, offsetLength - lineLength)
  s3_wordoffset := s3_addr(offsetLength - lineLength - 1, 0)

  val result = Wire(UInt(blockBits.W))
  val cacheline_meta = s3_meta(s3_access_index)
  val cacheline_data = s3_cacheline(s3_access_index)

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: Nil =
    Enum(5)
  val state = RegInit(s_idle)
  val read_address = Cat(s3_tag, s3_index, 0.U(offsetLength.W))
  val write_address = Cat(cacheline_meta.tag, s3_index, 0.U(offsetLength.W))
  val mem_valid = state === s_memReadResp && io.mem.resp.valid
  val request_satisfied = s3_hit || mem_valid
  stall := s3_valid && !request_satisfied // wait for data

  current_request.resp.valid := s3_valid && request_satisfied
  current_request.resp.bits.data := result
  current_request.req.ready := state === s_idle
  current_request.flush_ready := true.B

  io.mem.stall := false.B
  io.mem.flush := false.B
  io.mem.req.valid := s3_valid && (state === s_memReadReq || state === s_memReadResp || state === s_memWriteReq || state === s_memWriteResp)
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
      when(!s3_hit && s3_valid) {
        state := Mux(cacheline_meta.dirty, s_memWriteReq, s_memReadReq)
      }
    }
    is(s_memReadReq) { when(io.mem.req.fire()) { state := s_memReadResp } }
    is(s_memReadResp) { when(io.mem.resp.fire()) { state := s_idle } }
    is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
    is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_memReadReq } }
  }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * blockBits - 1, i * blockBits)
  }

  val target_data = Mux(s3_hit, cacheline_data, fetched_vec)
  result := DontCare
  when(s3_valid) {
    when(request_satisfied) {
      when(s3_wen) {
        val new_data = Wire(new CacheLineData)
        val write_data = Wire(Vec(nWays, new CacheLineData))
        new_data := target_data
        new_data.data(s3_lineoffset) := s3_data
        for (i <- 0 until nWays) {
          when(s3_access_index === i.U) {
            write_data(i) := new_data
            dataArray(i).write(s3_index, new_data)
          }.otherwise {
            write_data(i) := s3_cacheline(i)
          }
        }
        val write_meta = Wire(Vec(nWays, new MetaData))
        write_meta := policy.update_meta(s3_meta, s3_access_index)
        write_meta(s3_access_index).valid := true.B
        write_meta(s3_access_index).dirty := true.B
        write_meta(s3_access_index).tag := s3_tag
        for (i <- 0 until nWays) {
          metaArray(i).write(s3_index, write_meta(i))
        }
        printf(
          p"l2cache write: s3_index=${s3_index}, s3_access_index=${s3_access_index}\n"
        )
        printf(p"\tnew_data=${new_data}\n")
        printf(p"\twrite_meta=${write_meta}\n")
      }.otherwise {
        val result_data = target_data.data(s3_lineoffset)
        val write_data = Wire(Vec(nWays, new CacheLineData))
        write_data := DontCare
        result := result_data
        when(!s3_hit) {
          for (i <- 0 until nWays) {
            when(s3_access_index === i.U) {
              write_data(i) := target_data
              dataArray(i).write(s3_index, target_data)
            }.otherwise {
              write_data(i) := s3_cacheline(i)
            }
          }
        }
        val write_meta = Wire(Vec(nWays, new MetaData))
        write_meta := policy.update_meta(s3_meta, s3_access_index)
        write_meta(s3_access_index).valid := true.B
        when(!s3_hit) {
          write_meta(s3_access_index).dirty := false.B
        }
        write_meta(s3_access_index).tag := s3_tag
        for (i <- 0 until nWays) {
          metaArray(i).write(s3_index, write_meta(i))
        }
        printf(
          p"l2cache read update: s3_index=${s3_index}, s3_access_index=${s3_access_index}\n"
        )
        printf(p"\ttarget_data=${target_data}\n")
        printf(p"\twrite_meta=${write_meta}\n")
      }
    }
  }

  printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  printf("state=%d, s3_hit=%d, result=%x\n", state, s3_hit, result)
  printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
  printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
  printf("s2_valid=%d, s2_addr=%x, s2_index=%x\n", s2_valid, s2_addr, s2_index)
  printf("s2_data=%x, s2_wen=%d, s2_memtype=%d\n", s2_data, s2_wen, s2_memtype)
  printf("s3_valid=%d, s3_addr=%x, s3_index=%x\n", s3_valid, s3_addr, s3_index)
  printf("s3_data=%x, s3_wen=%d, s3_memtype=%d\n", s3_data, s3_wen, s3_memtype)
  printf(
    "s3_tag=%x, s3_lineoffset=%x, s3_wordoffset=%x\n",
    s3_tag,
    s3_lineoffset,
    s3_wordoffset
  )
  printf(p"s2_hitVec=${s2_hitVec}, s3_access_index=${s3_access_index}\n")
  printf(
    p"s2_victim_index=${s2_victim_index}, s2_victim_vec=${s2_victim_vec}, s3_access_vec = ${s3_access_vec}\n"
  )
  printf(p"s2_cacheline=${s2_cacheline}\n")
  printf(p"s2_meta=${s2_meta}\n")
  printf(p"s3_cacheline=${s3_cacheline}\n")
  printf(p"s3_meta=${s3_meta}\n")
  printf(p"----------${cacheName} io.mem----------\n")
  printf(p"${io.mem}\n")
  printf("-----------------------------------------------\n")
}
