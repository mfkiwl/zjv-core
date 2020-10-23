package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import rv64_3stage.ControlConst._
import bus._
import device._
import utils._

class DCacheWriteThroughSplit(implicit val cacheConfig: CacheConfig)
    extends Module
    with CacheParameters {
  val io = IO(new CacheIO)

  // Module Used
  val metaArray = List.fill(nWays)(Mem(nSets, new MetaData))
  val dataArray = List.fill(nWays)(Mem(nSets, new CacheLineData))
  val stall = Wire(Bool())
  val need_forward = Wire(Bool())
  val write_meta = Wire(Vec(nWays, new MetaData))
  val write_data = Reg(Vec(nWays, new CacheLineData))
  val writeData = Wire(Vec(nWays, new CacheLineData))

  /* stage1 signals */
  val s1_valid = WireInit(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  val s1_index = Wire(UInt(indexLength.W))
  val s1_data = Wire(UInt(blockBits.W))
  val s1_wen = WireInit(Bool(), false.B)
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
  val s2_index = RegInit(UInt(indexLength.W), 0.U)
  val s2_data = RegInit(UInt(blockBits.W), 0.U)
  val s2_wen = RegInit(Bool(), false.B)
  val s2_memtype = RegInit(UInt(xlen.W), 0.U)
  val s2_meta = Reg(Vec(nWays, new MetaData))
  val s2_cacheline = Reg(Vec(nWays, new CacheLineData))
  val s2_tag = Wire(UInt(tagLength.W))
  val s2_lineoffset = Wire(UInt(lineLength.W))
  val s2_wordoffset = Wire(UInt((offsetLength - lineLength).W))

  when(!io.in.stall) {
    s2_valid := s1_valid
    s2_addr := s1_addr
    s2_index := s1_index
    s2_data := s1_data
    s2_wen := s1_wen
    s2_memtype := s1_memtype
    when(need_forward) {
      s2_meta := write_meta
      s2_cacheline := writeData
    }.otherwise {
      for (i <- 0 until nWays) {
        s2_meta(i) := metaArray(i).read(s1_index)
        s2_cacheline(i) := dataArray(i).read(s1_index)
      }
    }
  }

  s2_tag := s2_addr(xlen - 1, xlen - tagLength)
  s2_lineoffset := s2_addr(offsetLength - 1, offsetLength - lineLength)
  s2_wordoffset := s2_addr(offsetLength - lineLength - 1, 0)

  val hitVec = VecInit(s2_meta.map(m => m.valid && m.tag === s2_tag)).asUInt
  val hit_index = PriorityEncoder(hitVec)
  val victim_index = policy.choose_victim(s2_meta)
  val victim_vec = UIntToOH(victim_index)
  val ismmio = AddressSpace.isMMIO(s2_addr)
  val hit = hitVec.orR && !ismmio
  val result = Wire(UInt(blockBits.W))
  val access_index = Mux(hit, hit_index, victim_index)
  val access_vec = UIntToOH(access_index)
  val cacheline_meta = s2_meta(access_index)
  val cacheline_data = s2_cacheline(access_index)

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: Nil =
    Enum(7)
  val state = RegInit(s_idle)
  val read_address = Cat(s2_addr(xlen - 1, offsetLength), 0.U(offsetLength.W))
  val write_address =
    Cat(Mux(hit, cacheline_meta.tag, s2_tag), s2_index, 0.U(offsetLength.W))
  val mem_read_valid = state === s_memReadResp && io.mem.resp.valid
  val mem_write_valid = state === s_memWriteResp && io.mem.resp.valid
  val mem_read_request_satisfied = !s2_wen && (hit || mem_read_valid)
  val mem_write_request_satisfied = mem_write_valid
  val mem_request_satisfied =
    mem_read_request_satisfied || mem_write_request_satisfied
  val mmio_request_satisfied = state === s_mmioResp && io.mmio.resp.valid
  val request_satisfied = mem_request_satisfied || mmio_request_satisfied
  val hazard = s1_valid && s2_valid && s1_index === s2_index
  stall := s2_valid && !request_satisfied // wait for data
  need_forward := hazard && mem_request_satisfied

  io.in.resp.valid := s2_valid && request_satisfied
  io.in.resp.bits.data := result
  io.in.req.ready := !stall // && !need_forward
  io.in.flush_ready := true.B

  io.mem.stall := false.B
  io.mem.flush := false.B
  io.mem.req.valid := s2_valid && (state === s_memReadReq || state === s_memReadResp || state === s_memWriteReq || state === s_memWriteResp)
  io.mem.req.bits.addr := Mux(
    state === s_memWriteReq || state === s_memWriteResp,
    write_address,
    read_address
  )
  io.mem.req.bits.data := write_data(access_index).data.asUInt
  io.mem.req.bits.wen := state === s_memWriteReq || state === s_memWriteResp
  io.mem.req.bits.memtype := DontCare
  io.mem.resp.ready := s2_valid && (state === s_memReadResp || state === s_memWriteResp)

  io.mmio.stall := false.B
  io.mmio.flush := false.B
  io.mmio.req.valid := s2_valid && (state === s_mmioReq || state === s_mmioResp)
  io.mmio.req.bits.addr := s2_addr
  io.mmio.req.bits.data := s2_data
  io.mmio.req.bits.wen := s2_wen
  io.mmio.req.bits.memtype := s2_memtype
  io.mmio.resp.ready := s2_valid && state === s_mmioResp

  switch(state) {
    is(s_idle) {
      when(s2_valid) {
        when(!hit) {
          state := Mux(ismmio, s_mmioReq, s_memReadReq)
        }.elsewhen(s2_wen) {
          state := s_memWriteReq
        }
      }
    }
    is(s_memReadReq) { when(io.mem.req.fire()) { state := s_memReadResp } }
    is(s_memReadResp) {
      when(io.mem.resp.fire()) {
        when(s2_wen) {
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
  }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * blockBits - 1, i * blockBits)
  }

  val target_data = Mux(hit, cacheline_data, fetched_vec)
  result := DontCare
  writeData := DontCare
  write_meta := DontCare
  when(s2_valid) {
    when(s2_wen) {
      when(hit || mem_read_valid) {
        val new_data = Wire(new CacheLineData)
        val filled_data = WireInit(UInt(blockBits.W), 0.U(blockBits.W))
        val offset = s2_wordoffset << 3
        val mask = WireInit(UInt(blockBits.W), 0.U)
        switch(s2_memtype) {
          is(memXXX) {
            filled_data := s2_data
            mask := Fill(blockBits, 1.U(1.W)) << offset
          }
          is(memByte) {
            filled_data := Fill(8, s2_data(7, 0))
            mask := Fill(8, 1.U(1.W)) << offset
          }
          is(memHalf) {
            filled_data := Fill(4, s2_data(15, 0))
            mask := Fill(16, 1.U(1.W)) << offset
          }
          is(memWord) {
            filled_data := Fill(2, s2_data(31, 0))
            mask := Fill(32, 1.U(1.W)) << offset
          }
          is(memDouble) {
            filled_data := s2_data
            mask := Fill(blockBits, 1.U(1.W)) << offset
          }
          is(memByteU) {
            filled_data := Fill(8, s2_data(7, 0))
            mask := Fill(8, 1.U(1.W)) << offset
          }
          is(memHalfU) {
            filled_data := Fill(4, s2_data(15, 0))
            mask := Fill(16, 1.U(1.W)) << offset
          }
          is(memWordU) {
            filled_data := Fill(2, s2_data(31, 0))
            mask := Fill(32, 1.U(1.W)) << offset
          }
        }
        new_data := target_data
        new_data.data(
          s2_lineoffset
        ) := (mask & filled_data) | (~mask & target_data.data(s2_lineoffset))
        for (i <- 0 until nWays) {
          when(access_index === i.U) {
            writeData(i) := new_data
            dataArray(i).write(s2_index, new_data)
          }.otherwise {
            writeData(i) := s2_cacheline(i)
          }
        }
        // dataArray.write(s2_index, writeData, access_vec.asBools)
        write_data := writeData
        write_meta := policy.update_meta(s2_meta, access_index)
        write_meta(access_index).valid := true.B
        write_meta(access_index).dirty := false.B
        write_meta(access_index).tag := s2_tag
        // metaArray.write(s2_index, write_meta)
        for (i <- 0 until nWays) {
          metaArray(i).write(s2_index, write_meta(i))
        }
        // printf(
        //   p"[${GTimer()}]: dcache write: offset=${Hexadecimal(offset)}, mask=${Hexadecimal(mask)}, filled_data=${Hexadecimal(filled_data)}\n"
        // )
        // printf(p"\twriteData=${writeData}\n")
        // printf(p"\twrite_meta=${write_meta}\n")
      }
    }.otherwise {
      when(hit || mem_read_valid || mmio_request_satisfied) {
        val result_data = target_data.data(s2_lineoffset)
        val offset = s2_wordoffset << 3
        val mask = WireInit(UInt(blockBits.W), 0.U)
        val real_data = WireInit(UInt(blockBits.W), 0.U)
        val mem_result = WireInit(UInt(blockBits.W), 0.U)
        switch(s2_memtype) {
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
        result := Mux(ismmio, io.mmio.resp.bits.data, mem_result)
        // printf(
        //   p"[${GTimer()}]: dcache read: offset=${Hexadecimal(offset)}, mask=${Hexadecimal(mask)}, real_data=${Hexadecimal(real_data)}\n"
        // )
        when(!ismmio) {
          when(!hit) {
            for (i <- 0 until nWays) {
              when(access_index === i.U) {
                writeData(i) := target_data
                dataArray(i).write(s2_index, target_data)
              }.otherwise {
                writeData(i) := s2_cacheline(i)
              }
            }
            // dataArray.write(s2_index, writeData, access_vec.asBools)
          }
          write_meta := policy.update_meta(s2_meta, access_index)
          write_meta(access_index).valid := true.B
          // when(!hit) {
          write_meta(access_index).dirty := false.B
          // }
          write_meta(access_index).tag := s2_tag
          // metaArray.write(s2_index, write_meta)
          for (i <- 0 until nWays) {
            metaArray(i).write(s2_index, write_meta(i))
          }
          // printf(
          //   p"dcache write: mask=${Hexadecimal(mask)}, mem_result=${Hexadecimal(mem_result)}, s2_index=0x${Hexadecimal(s2_index)}\n"
          // )
          // printf(p"\twriteData=${writeData}\n")
          // printf(p"\twrite_meta=${write_meta}\n")
        }
      }
    }
  }

  // printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  // printf(
  //   "stall=%d, need_forward=%d, state=%d, ismmio=%d, hit=%d, result=%x\n",
  //   stall,
  //   need_forward,
  //   state,
  //   ismmio,
  //   hit,
  //   result
  // )
  // printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
  // printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
  // printf("s2_valid=%d, s2_addr=%x, s2_index=%x\n", s2_valid, s2_addr, s2_index)
  // printf("s2_data=%x, s2_wen=%d, s2_memtype=%d\n", s2_data, s2_wen, s2_memtype)
  // printf(
  //   "s2_tag=%x, s2_lineoffset=%x, s2_wordoffset=%x\n",
  //   s2_tag,
  //   s2_lineoffset,
  //   s2_wordoffset
  // )
  // printf(p"hitVec=${hitVec}, access_index=${access_index}\n")
  // printf(
  //   p"victim_index=${victim_index}, victim_vec=${victim_vec}, access_vec = ${access_vec}\n"
  // )
  // printf(p"s2_cacheline=${s2_cacheline}\n")
  // printf(p"s2_meta=${s2_meta}\n")
  // printf(p"cacheline_data=${cacheline_data}\n")
  // printf(p"cacheline_meta=${cacheline_meta}\n")
  // printf(p"write_meta=${write_meta}\n")
  // printf(p"write_data=${write_data}\n")
  // printf(p"fetched_data=${Hexadecimal(fetched_data)}\n")
  // printf(p"fetched_vec=${fetched_vec}\n")
  // printf(p"----------${cacheName} io.in----------\n")
  // printf(p"${io.in}\n")
  // printf(p"----------${cacheName} io.mem----------\n")
  // printf(p"${io.mem}\n")
  // // printf(p"----------${cacheName} io.mmio----------\n")
  // // printf(p"${io.mmio}\n")
  // printf("-----------------------------------------------\n")
}
