package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import rv64_3stage.ControlConst._
import bus._
import device._
import utils._

class DCacheSimple(implicit val cacheConfig: CacheConfig)
    extends Module
    with CacheParameters {
  val io = IO(new CacheIO)

  // Module Used
  val metaArray = Mem(nSets, Vec(nWays, new MetaData))
  val dataArray = Mem(nSets, Vec(nWays, new CacheLineData))

  /* stage2 registers */
  val s1_valid = WireInit(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  val s1_index = Wire(UInt(indexLength.W))
  val s1_data = Wire(UInt(blockBits.W))
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
  val ismmio = AddressSpace.isMMIO(s1_addr)
  val hit = hitVec.orR && !ismmio
  val result = Wire(UInt(blockBits.W))
  val access_index = Mux(hit, hit_index, victim_index)
  val access_vec = UIntToOH(access_index)
  val cacheline_meta = s1_meta(access_index)
  val cacheline_data = s1_cacheline(access_index)

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: s_release :: Nil =
    Enum(9)
  val state = RegInit(s_idle)
  val read_address = Cat(s1_addr(xlen - 1, offsetLength), 0.U(offsetLength.W))
  val write_address = Cat(cacheline_meta.tag, s1_index, 0.U(offsetLength.W))

  io.in.resp.valid := s1_valid && (hit || io.mem.resp.valid && state === s_memReadResp) || (io.mmio.resp.valid && state === s_mmioResp)
  io.in.resp.bits.data := result
  io.in.req.ready := state === s_idle

  io.mem.stall := false.B
  io.mem.req.valid := s1_valid && (state === s_memReadReq || state === s_memReadResp || state === s_memWriteReq || state === s_memWriteResp) // (!hit && !ismmio)
  io.mem.req.bits.addr := Mux(
    state === s_memWriteReq || state === s_memWriteResp,
    write_address,
    read_address
  )
  io.mem.req.bits.data := cacheline_data.asUInt
  io.mem.req.bits.wen := state === s_memWriteReq || state === s_memWriteResp
  io.mem.req.bits.memtype := DontCare
  io.mem.resp.ready := s1_valid && (state === s_memReadResp || state === s_memWriteResp)

  io.mmio.stall := false.B
  io.mmio.req.valid := s1_valid && (state === s_mmioReq || state === s_mmioResp)
  io.mmio.req.bits.addr := s1_addr
  io.mmio.req.bits.data := s1_data
  io.mmio.req.bits.wen := s1_wen
  io.mmio.req.bits.memtype := s1_memtype
  io.mmio.resp.ready := s1_valid && state === s_mmioResp

  switch(state) {
    is(s_idle) {
      when(!hit && s1_valid) {
        state := Mux(
          ismmio,
          s_mmioReq,
          Mux(cacheline_meta.dirty, s_memWriteReq, s_memReadReq)
        )
      }
    }
    is(s_memReadReq) { when(io.mem.req.fire()) { state := s_memReadResp } }
    is(s_memReadResp) { when(io.mem.resp.fire()) { state := s_idle } }
    is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
    is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_memReadReq } }
    is(s_mmioReq) { when(io.mmio.req.fire()) { state := s_mmioResp } }
    is(s_mmioResp) { when(io.mmio.resp.fire()) { state := s_idle } }
    is(s_wait_resp) { when(io.in.resp.fire()) { state := s_idle } }
    is(s_release) {}
  }

  when(!s1_valid) { state := s_idle }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * blockBits - 1, i * blockBits)
  }

  val target_data = Mux(hit, cacheline_data, fetched_vec)
  result := DontCare
  when(s1_valid) {
    when(s1_wen) {
      when(hit || (io.mem.resp.valid && state === s_memReadResp)) {
        val newdata = Wire(new CacheLineData)
        val filled_data = WireInit(UInt(blockBits.W), 0.U(blockBits.W))
        val offset = s1_wordoffset << 3
        val mask = WireInit(UInt(blockBits.W), 0.U)
        switch(s1_memtype) {
          is(memXXX) {
            filled_data := s1_data
            mask := Fill(blockBits, 1.U(1.W)) << offset
          }
          is(memByte) {
            filled_data := Fill(8, s1_data(7, 0))
            mask := Fill(8, 1.U(1.W)) << offset
          }
          is(memHalf) {
            filled_data := Fill(4, s1_data(15, 0))
            mask := Fill(16, 1.U(1.W)) << offset
          }
          is(memWord) {
            filled_data := Fill(2, s1_data(31, 0))
            mask := Fill(32, 1.U(1.W)) << offset
          }
          is(memDouble) {
            filled_data := s1_data
            mask := Fill(blockBits, 1.U(1.W)) << offset
          }
          is(memByteU) {
            filled_data := Fill(8, s1_data(7, 0))
            mask := Fill(8, 1.U(1.W)) << offset
          }
          is(memHalfU) {
            filled_data := Fill(4, s1_data(15, 0))
            mask := Fill(16, 1.U(1.W)) << offset
          }
          is(memWordU) {
            filled_data := Fill(2, s1_data(31, 0))
            mask := Fill(32, 1.U(1.W)) << offset
          }
        }
        newdata := target_data
        newdata.data(
          s1_lineoffset
        ) := (mask & filled_data) | (~mask & target_data.data(s1_lineoffset))
        val writeData = VecInit(Seq.fill(nWays)(newdata))
        dataArray.write(s1_index, writeData, access_vec.asBools)
        val new_meta = Wire(Vec(nWays, new MetaData))
        new_meta := policy.update_meta(s1_meta, access_index)
        new_meta(access_index).valid := true.B
        new_meta(access_index).dirty := true.B
        new_meta(access_index).tag := s1_tag
        metaArray.write(s1_index, new_meta)
        // printf(
        //   p"dcache write: mask=${Hexadecimal(mask)}, filled_data=${Hexadecimal(filled_data)}, s1_index=0x${Hexadecimal(s1_index)}\n"
        // )
        // printf(p"\tnewdata=${newdata}\n")
        // printf(p"\tnew_meta=${new_meta}\n")
      }
    }.otherwise {
      when(
        hit || (io.mem.resp.valid && state === s_memReadResp) || (io.mmio.resp.valid && state === s_mmioResp)
      ) {
        val result_data = target_data.data(s1_lineoffset)
        val offset = s1_wordoffset << 3
        val mask = WireInit(UInt(blockBits.W), 0.U)
        val realdata = WireInit(UInt(blockBits.W), 0.U)
        val mem_result = WireInit(UInt(blockBits.W), 0.U)
        switch(s1_memtype) {
          is(memXXX) { mem_result := result_data }
          is(memByte) {
            mask := Fill(8, 1.U(1.W)) << offset
            realdata := (result_data & mask) >> offset
            mem_result := Cat(Fill(56, realdata(7)), realdata(7, 0))
          }
          is(memHalf) {
            mask := Fill(16, 1.U(1.W)) << offset
            realdata := (result_data & mask) >> offset
            mem_result := Cat(Fill(48, realdata(15)), realdata(15, 0))
          }
          is(memWord) {
            mask := Fill(32, 1.U(1.W)) << offset
            realdata := (result_data & mask) >> offset
            mem_result := Cat(Fill(32, realdata(31)), realdata(31, 0))
          }
          is(memDouble) { mem_result := result_data }
          is(memByteU) {
            mask := Fill(8, 1.U(1.W)) << offset
            realdata := (result_data & mask) >> offset
            mem_result := Cat(Fill(56, 0.U), realdata(7, 0))
          }
          is(memHalfU) {
            mask := Fill(16, 1.U(1.W)) << offset
            realdata := (result_data & mask) >> offset
            mem_result := Cat(Fill(48, 0.U), realdata(15, 0))
          }
          is(memWordU) {
            mask := Fill(32, 1.U(1.W)) << offset
            realdata := (result_data & mask) >> offset
            mem_result := Cat(Fill(32, 0.U), realdata(31, 0))
          }
        }
        result := Mux(ismmio, io.mmio.resp.bits.data, mem_result)
        // printf(
        //   p"[${GTimer()}]: dcache read: offset=${Hexadecimal(offset)}, mask=${Hexadecimal(mask)}, realdata=${Hexadecimal(realdata)}\n"
        // )
        when(!ismmio) {
          val writeData = VecInit(Seq.fill(nWays)(target_data))
          dataArray.write(s1_index, writeData, access_vec.asBools)
          val new_meta = Wire(Vec(nWays, new MetaData))
          new_meta := policy.update_meta(s1_meta, access_index)
          new_meta(access_index).valid := true.B
          when(!hit) {
            new_meta(access_index).dirty := false.B
          }
          new_meta(access_index).tag := s1_tag
          metaArray.write(s1_index, new_meta)
          // printf(
          //   p"dcache write: mask=${Hexadecimal(mask)}, mem_result=${Hexadecimal(mem_result)}, s1_index=0x${Hexadecimal(s1_index)}\n"
          // )
          // printf(p"\ttarget_data=${target_data}\n")
          // printf(p"\tnew_meta=${new_meta}\n")
        }
      }
    }
  }

  // printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  // printf(
  //   "state=%d, ismmio=%d, hit=%d, result=%x\n",
  //   state,
  //   ismmio,
  //   hit,
  //   result
  // )
  // printf(
  //   "s1_valid=%d, s1_addr=%x, s1_index=%x\n",
  //   s1_valid,
  //   s1_addr,
  //   s1_index
  // )
  // printf(
  //   "s1_data=%x, s1_wen=%d, s1_memtype=%d\n",
  //   s1_data,
  //   s1_wen,
  //   s1_memtype
  // )
  // printf(
  //   "s1_tag=%x, s1_lineoffset=%x, s1_wordoffset=%x\n",
  //   s1_tag,
  //   s1_lineoffset,
  //   s1_wordoffset
  // )
  // printf(p"hitVec=${hitVec}, access_index=${access_index}\n")
  // printf(
  //   p"victim_index=${victim_index}, victim_vec=${victim_vec}, access_vec = ${access_vec}\n"
  // )
  // printf(p"s1_cacheline=${s1_cacheline}\n")
  // printf(p"s1_meta=${s1_meta}\n")
  // // printf(p"cacheline_data=${cacheline_data}\n")
  // // printf(p"cacheline_meta=${cacheline_meta}\n")
  // // printf(p"dataArray(s1_index)=${dataArray(s1_index)}\n")
  // // printf(p"metaArray(s1_index)=${metaArray(s1_index)}\n")
  // // printf(p"fetched_data=${Hexadecimal(fetched_data)}\n")
  // // printf(p"fetched_vec=${fetched_vec}\n")
  // printf(p"----------${cacheName} io.in----------\n")
  // printf(p"${io.in}\n")
  // printf(p"----------${cacheName} io.mem----------\n")
  // printf(p"${io.mem}\n")
  // // printf(p"----------${cacheName} io.mmio----------\n")
  // // printf(p"${io.mmio}\n")
  // printf("-----------------------------------------------\n")
}
