package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import rv64_3stage.ControlConst._
import bus._
import device._
import utils._

class ICacheSimple(implicit val cacheConfig: CacheConfig)
    extends Module
    with CacheParameters {
  val io = IO(new CacheIO)

  // Module Used
  val metaArray = Mem(nSets, Vec(nWays, new MetaData))
  val dataArray = Mem(nSets, Vec(nWays, new CacheLineData))

  /* stage1 registers */
  val s1_valid = WireInit(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  val s1_index = Wire(UInt(indexLength.W))
  val s1_data = Wire(UInt(xlen.W))
  val s1_wen = WireInit(Bool(), false.B)
  val s1_memtype = Wire(UInt(xlen.W))
  val s1_meta = Wire(Vec(nWays, new MetaData))
  val s1_cacheline = Wire(Vec(nWays, new CacheLineData))
  val s1_tag = Wire(UInt(tagLength.W))
  val s1_lineoffset = Wire(UInt(lineLength.W))
  val s1_wordoffset = Wire(UInt((offsetLength - lineLength).W))

  s1_valid := io.in.req.valid
  s1_addr := io.in.req.bits.addr
  s1_index := s1_addr(indexLength + offsetLength - 1, offsetLength)
  s1_data := io.in.req.bits.data
  s1_wen := io.in.req.bits.wen
  s1_memtype := io.in.req.bits.memtype
  s1_meta := metaArray(s1_index)
  s1_cacheline := dataArray(s1_index)
  s1_tag := s1_addr(xlen - 1, xlen - tagLength)
  s1_lineoffset := s1_addr(offsetLength - 1, offsetLength - lineLength)
  s1_wordoffset := s1_addr(offsetLength - lineLength - 1, 0)

  val hitVec = VecInit(s1_meta.map(m => m.valid && m.tag === s1_tag)).asUInt
  val hit_index = PriorityEncoder(hitVec)
  val victim_index = policy.choose_victim(s1_meta)
  val victim_vec = UIntToOH(victim_index)
  val cacheline_meta = Mux1H(hitVec, s1_meta)
  val cacheline_data = Mux1H(hitVec, s1_cacheline)
  val hit = hitVec.orR
  val result = Wire(UInt(xlen.W))
  val access_index = Mux(hit, hit_index, victim_index)
  val access_vec = UIntToOH(access_index)

  val s_idle :: s_memReadReq :: s_memReadResp :: s_wait_resp :: s_release :: Nil =
    Enum(5)
  val state = RegInit(s_idle)
  val read_address = Cat(s1_addr(xlen - 1, offsetLength), 0.U(offsetLength.W))

  io.in.resp.valid := s1_valid && (hit || io.mem.resp.valid)
  io.in.resp.bits.data := result
  io.in.req.ready := state === s_idle

  io.mem.stall := false.B
  io.mem.req.valid := s1_valid && (state === s_memReadReq || state === s_memReadResp)
  io.mem.req.bits.addr := read_address
  io.mem.req.bits.data := DontCare
  io.mem.req.bits.wen := false.B
  io.mem.req.bits.memtype := DontCare
  io.mem.resp.ready := s1_valid && state === s_memReadResp

  switch(state) {
    is(s_idle) {
      when(!hit && s1_valid) {
        state := s_memReadReq
      }
    }
    is(s_memReadReq) {
      when(io.mem.req.fire()) { state := s_memReadResp }
    }
    is(s_memReadResp) {
      when(io.mem.resp.fire()) { state := s_idle }
    }
    is(s_release) {}
  }

  when(!s1_valid) { state := s_idle }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * xlen - 1, i * xlen)
  }

  val target_data = Mux(hit, cacheline_data, fetched_vec)
  result := DontCare
  when(s1_valid) {
    when(hit || io.mem.resp.valid) {
      val result_data = target_data.data(s1_lineoffset)
      val offset = s1_wordoffset << 3
      val mask = WireInit(UInt(xlen.W), 0.U)
      val realdata = WireInit(UInt(xlen.W), 0.U)
      switch(s1_memtype) {
        is(memXXX) { result := result_data }
        is(memByte) {
          mask := Fill(8, 1.U(1.W)) << offset
          realdata := (result_data & mask) >> offset
          result := Cat(Fill(56, realdata(7)), realdata(7, 0))
        }
        is(memHalf) {
          mask := Fill(16, 1.U(1.W)) << offset
          realdata := (result_data & mask) >> offset
          result := Cat(Fill(48, realdata(15)), realdata(15, 0))
        }
        is(memWord) {
          mask := Fill(32, 1.U(1.W)) << offset
          realdata := (result_data & mask) >> offset
          result := Cat(Fill(32, realdata(31)), realdata(31, 0))
        }
        is(memDouble) { result := result_data }
        is(memByteU) {
          mask := Fill(8, 1.U(1.W)) << offset
          realdata := (result_data & mask) >> offset
          result := Cat(Fill(56, 0.U), realdata(7, 0))
        }
        is(memHalfU) {
          mask := Fill(16, 1.U(1.W)) << offset
          realdata := (result_data & mask) >> offset
          result := Cat(Fill(48, 0.U), realdata(15, 0))
        }
        is(memWordU) {
          mask := Fill(32, 1.U(1.W)) << offset
          realdata := (result_data & mask) >> offset
          result := Cat(Fill(32, 0.U), realdata(31, 0))
        }
      }
      val writeData = VecInit(Seq.fill(nWays)(target_data))
      dataArray.write(s1_index, writeData, access_vec.asBools)
      val new_meta = Wire(Vec(nWays, new MetaData))
      new_meta := policy.update_meta(s1_meta, access_index)
      new_meta(access_index).valid := true.B
      new_meta(access_index).tag := s1_tag
      metaArray.write(s1_index, new_meta)
      // printf(
      //     p"[${GTimer()}]: icache read: offset=${Hexadecimal(offset)}, mask=${Hexadecimal(mask)}, realdata=${Hexadecimal(realdata)}\n"
      //   )
      // printf(p"\ttarget_data=${target_data}\n")
    }
  }

  // printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  // printf("state=%d, hit=%d, result=%x\n", state, hit, result)
  // printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
  // printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
  // printf("s1_tag=%x, s1_wordoffset=%x\n", s1_tag, s1_wordoffset)
  // printf(p"hitVec=${hitVec}, access_index=${access_index}\n")
  // printf(
  //   p"victim_index=${victim_index}, victim_vec=${victim_vec}, access_vec = ${access_vec}\n"
  // )
  // printf(p"s1_cacheline=${s1_cacheline}\n")
  // printf(p"s1_meta=${s1_meta}\n")
  // // printf(
  // //   p"cacheline_data=${cacheline_data}, cacheline_meta=${cacheline_meta}\n"
  // // )
  // // printf(
  // //   p"dataArray(s1_index)=${dataArray(s1_index)},metaArray(s1_index)=${metaArray(s1_index)}\n"
  // // )
  // printf(p"----------${cacheName} io.in----------\n")
  // printf(p"${io.in}\n")
  // printf(p"----------${cacheName} io.mem----------\n")
  // printf(p"${io.mem}\n")
  // printf("-----------------------------------------------\n")
}