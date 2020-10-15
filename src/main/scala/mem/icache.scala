package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import rv64_3stage.ControlConst._
import bus._
import device._
import utils._

trait ICacheParameters extends phvntomParams {
  val cacheName = "icache" // used for debug info
  val userBits = 0
  val idBits = 0
  val nWays = 1
  val nLine = 1
  val nBytes = 32 * 1024
  val nBits = nBytes * 8
  val lineBits = nLine * xlen
  val lineBytes = lineBits / 8
  val lineLength = log2Ceil(nLine)
  val nSets = nBytes / lineBytes / nWays
  val offsetLength = log2Ceil(lineBytes)
  val indexLength = log2Ceil(nSets)
  val tagLength = xlen - (indexLength + offsetLength)
}

class ICacheSimpleIO extends Bundle with ICacheParameters {
  val in = new MemIO
  val mem = Flipped(new MemIO)
}

class IMetaData extends Bundle with ICacheParameters {
  val valid = Bool()
  val dirty = Bool()
  val tag = UInt(tagLength.W)
  override def toPrintable: Printable =
    p"IMetaData(valid = ${valid}, dirty = ${dirty}, tag = 0x${Hexadecimal(tag)})"
}

class ICacheLineData extends Bundle with ICacheParameters {
  val data = UInt(xlen.W) // UInt(lineBits.W)
  override def toPrintable: Printable =
    p"ICacheLineData(data = 0x${Hexadecimal(data)})"
}

class ICacheSimple extends Module with ICacheParameters {
  val io = IO(new ICacheSimpleIO)

  // printf(p"----------${cacheName} Parameters----------\n")
  // printf(
  //   p"nBytes=${nBytes}, nBits=${nBits}, lineBits=${lineBits}, lineBytes=${lineBytes}, lineLength=${lineLength}\n"
  // )
  // printf(
  //   p"nSets=${nSets}, offsetLength=${offsetLength}, indexLength=${indexLength}, tagLength=${tagLength}\n"
  // )
  // printf("-----------------------------------------------\n")

  // Module Used
  val metaArray = Mem(nSets, new IMetaData)
  val dataArray = Mem(nSets, new ICacheLineData)

  /* stage2 registers */
  val s1_valid = WireInit(Bool(), false.B)
  val s1_addr = WireInit(UInt(xlen.W), 0.U)
  val s1_index = WireInit(UInt(indexLength.W), 0.U)
  val s1_data = WireInit(UInt(xlen.W), 0.U)
  val s1_wen = WireInit(Bool(), false.B)
  val s1_memtype = WireInit(UInt(xlen.W), 0.U)
  val s1_meta = Wire(new IMetaData)
  val s1_cacheline = Wire(new ICacheLineData)
  val s1_tag = WireInit(UInt(tagLength.W), 0.U)
  val s1_wordoffset = WireInit(UInt((offsetLength - lineLength).W), 0.U)

  // ******************************
  //           Stage1
  // ******************************
  s1_valid := io.in.req.valid
  s1_addr := io.in.req.bits.addr
  s1_index := s1_addr(indexLength + offsetLength - 1, offsetLength)
  s1_data := io.in.req.bits.data
  s1_wen := io.in.req.bits.wen
  s1_memtype := io.in.req.bits.memtype
  s1_meta := metaArray(s1_index)
  s1_cacheline := dataArray(s1_index)
  s1_tag := s1_addr(xlen - 1, xlen - tagLength)
  s1_wordoffset := s1_addr(offsetLength - lineLength - 1, 0)

  val hitVec = s1_meta.valid && s1_meta.tag === s1_tag
  val invalidVec = !s1_meta.valid
  val victim_index = 0.U
  val cacheline_meta = s1_meta
  val cacheline_data = s1_cacheline
  val hit = hitVec.orR
  val result = Wire(UInt(xlen.W))

  val s_idle :: s_memReadReq :: s_memReadResp :: s_wait_resp :: s_release :: Nil =
    Enum(5)
  val state = RegInit(s_idle)
  val read_address = Cat(s1_addr(xlen - 1, offsetLength), 0.U(offsetLength.W))

  io.in.resp.valid := s1_valid && (hit || io.mem.resp.valid)
  io.in.resp.bits.data := result
  io.in.req.ready := state === s_idle

  io.mem.req.valid := s1_valid && state === s_memReadReq
  io.mem.req.bits.addr := read_address
  io.mem.req.bits.data := cacheline_data.data(victim_index).asUInt
  io.mem.req.bits.wen := false.B
  io.mem.req.bits.memtype := ControlConst.memDouble
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

  val fetched_data = Wire(UInt(xlen.W))
  fetched_data := io.mem.resp.bits.data
  val fetched_vec = Wire(new ICacheLineData)
  fetched_vec.data := fetched_data

  val target_data = Mux(hit, cacheline_data, fetched_vec)
  result := DontCare
  when(s1_valid) {
    when(hit || io.mem.resp.valid) {
      val offset = s1_wordoffset << 3
      val mask = WireInit(UInt(xlen.W), 0.U)
      val realdata = WireInit(UInt(xlen.W), 0.U)
      switch(s1_memtype) {
        is(memXXX) { result := target_data.data }
        is(memByte) {
          mask := Fill(8, 1.U(1.W)) << offset
          realdata := (target_data.data & mask) >> offset
          result := Cat(Fill(56, realdata(7)), realdata(7, 0))
        }
        is(memHalf) {
          mask := Fill(16, 1.U(1.W)) << offset
          realdata := (target_data.data & mask) >> offset
          result := Cat(Fill(48, realdata(15)), realdata(15, 0))
        }
        is(memWord) {
          mask := Fill(32, 1.U(1.W)) << offset
          realdata := (target_data.data & mask) >> offset
          result := Cat(Fill(32, realdata(31)), realdata(31, 0))
        }
        is(memDouble) { result := target_data.data }
        is(memByteU) {
          mask := Fill(8, 1.U(1.W)) << offset
          realdata := (target_data.data & mask) >> offset
          result := Cat(Fill(56, 0.U), realdata(7, 0))
        }
        is(memHalfU) {
          mask := Fill(16, 1.U(1.W)) << offset
          realdata := (target_data.data & mask) >> offset
          result := Cat(Fill(48, 0.U), realdata(15, 0))
        }
        is(memWordU) {
          mask := Fill(32, 1.U(1.W)) << offset
          realdata := (target_data.data & mask) >> offset
          result := Cat(Fill(32, 0.U), realdata(31, 0))
        }
      }
      dataArray(s1_index) := target_data
      val new_meta = Wire(new IMetaData)
      new_meta.valid := true.B
      new_meta.dirty := false.B
      new_meta.tag := s1_tag
      metaArray(s1_index) := new_meta
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
  // printf(p"hitVec=${hitVec}, invalidVec=${invalidVec}\n")
  // printf(p"s1_cacheline=${s1_cacheline}, s1_meta=${s1_meta}\n")
  // printf(
  //   p"cacheline_data=${cacheline_data}, cacheline_meta=${cacheline_meta}\n"
  // )
  // printf(
  //   p"dataArray(s1_index)=${dataArray(s1_index)},metaArray(s1_index)=${metaArray(s1_index)}\n"
  // )
  // printf(p"----------${cacheName} io.in----------\n")
  // printf(p"${io.in}\n")
  // printf(p"----------${cacheName} io.mem----------\n")
  // printf(p"${io.mem}\n")
  // printf("-----------------------------------------------\n")
}
