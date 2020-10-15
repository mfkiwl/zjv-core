// package mem

// import chisel3._
// import chisel3.util._
// import rv64_3stage._
// import rv64_3stage.ControlConst._
// import bus._
// import device._

// trait CacheParameters extends phvntomParams {
//   val cacheName = "dcache" // used for debug info
//   val userBits = 0
//   val idBits = 0
//   val nWays = 1
//   val nLine = 1
//   val nBytes = 32 * 1024
//   val nBits = nBytes * 8
//   val lineBits = nLine * xlen
//   val lineBytes = lineBits / 8
//   val lineLength = log2Ceil(nLine)
//   val nSets = nBytes / lineBytes / nWays
//   val offsetLength = log2Ceil(lineBytes)
//   val indexLength = log2Ceil(nSets)
//   val tagLength = xlen - (indexLength + offsetLength)
// }

// class CacheSimpleIO extends Bundle with CacheParameters {
//   val in = new MemIO
//   val mem = Flipped(new MemIO)
//   val mmio = Flipped(new MemIO)
// }

// class MetaData extends Bundle with CacheParameters {
//   val valid = Bool()
//   val dirty = Bool()
//   val tag = UInt(tagLength.W)
//   override def toPrintable: Printable =
//     p"MetaData(valid = ${valid}, dirty = ${dirty}, tag = 0x${Hexadecimal(tag)})"
// }

// class CacheLineData extends Bundle with CacheParameters {
//   val data = UInt(xlen.W) // UInt(lineBits.W)
//   override def toPrintable: Printable =
//     p"CacheLineData(data = 0x${Hexadecimal(data)})"
// }

// class DCacheSimple extends Module with CacheParameters {
//   val io = IO(new CacheSimpleIO)

//   // printf(p"----------${cacheName} Parameters----------\n")
//   // printf(
//   //   p"nBytes=${nBytes}, nBits=${nBits}, lineBits=${lineBits}, lineBytes=${lineBytes}, lineLength=${lineLength}\n"
//   // )
//   // printf(
//   //   p"nSets=${nSets}, offsetLength=${offsetLength}, indexLength=${indexLength}, tagLength=${tagLength}\n"
//   // )
//   // printf("-----------------------------------------------\n")

//   // Module Used
//   val metaArray = SyncReadMem(nSets, new MetaData)
//   val dataArray = SyncReadMem(nSets, new CacheLineData)
//   val stall = Wire(Bool())
//   val isForward = false.B

//   /* stage2 registers */
//   val s2_valid = RegInit(Bool(), false.B)
//   val s2_addr = RegInit(UInt(xlen.W), 0.U)
//   val s2_index = RegInit(UInt(indexLength.W), 0.U)
//   val s2_data = RegInit(UInt(xlen.W), 0.U)
//   val s2_wen = RegInit(Bool(), false.B)
//   val s2_memtype = RegInit(UInt(xlen.W), 0.U)
//   val s2_meta = Reg(new MetaData)
//   val s2_cacheline = Reg(new CacheLineData)

//   // ******************************
//   //           Stage1
//   // ******************************
//   val s1_valid = io.in.req.fire()
//   val s1_addr = io.in.req.bits.addr
//   val s1_index = s1_addr(indexLength + offsetLength - 1, offsetLength)
//   val s1_data = io.in.req.bits.data
//   val s1_wen = io.in.req.bits.wen
//   val s1_memtype = io.in.req.bits.memtype

//   printf(p"----------${cacheName} Stage 1----------\n")
//   printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
//   printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
//   printf("-----------------------------------------------\n")

//   when(!stall) {
//     s2_valid := s1_valid
//     s2_addr := s1_addr
//     s2_index := s1_index
//     s2_data := s1_data
//     s2_wen := s1_wen
//     s2_memtype := s1_memtype
//     s2_meta := metaArray.read(s1_index)
//     s2_cacheline := dataArray.read(s1_index)
//   }

//   // ******************************
//   //           Stage2
//   // ******************************
//   val s2_tag = s2_addr(xlen - 1, xlen - tagLength)
//   // val s2_lineoffset = s2_addr(offsetLength - 1, offsetLength - lineLength)
//   val s2_wordoffset = s2_addr(offsetLength - lineLength - 1, 0)

//   val hitVec = s2_meta.valid && s2_meta.tag === s2_tag
//   val invalidVec = !s2_meta.valid
//   val victim_index = 0.U
//   val cacheline_meta = s2_meta
//   val cacheline_data = s2_cacheline
//   val ismmio = AddressSpace.isMMIO(s2_addr)
//   val hit = hitVec.orR && !ismmio
//   val result = Wire(UInt(xlen.W))

//   val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: s_release :: Nil =
//     Enum(9)
//   val state = RegInit(s_idle)
//   val read_address = Cat(s2_addr(xlen - 1, offsetLength), 0.U(offsetLength.W))
//   val write_address = Cat(s2_meta.tag, s2_index, 0.U(offsetLength.W))

//   stall := s2_valid && !hit && (state =/= s_idle) // s2_valid && !hit && (!ismmio && !io.mem.resp.valid) || (ismmio && !io.mmio.resp.valid) // wait for data
//   io.in.resp.valid := s2_valid && (hit || io.mem.resp.valid || io.mmio.resp.valid)
//   io.in.resp.bits.data := result

//   io.mem.req.valid := s2_valid && (state === s_memReadReq || state === s_memWriteReq) // (!hit && !ismmio)
//   io.mem.req.bits.addr := Mux(
//     state === s_memWriteReq,
//     write_address,
//     read_address
//   )
//   io.mem.req.bits.data := cacheline_data.data(victim_index).asUInt
//   io.mem.req.bits.wen := (state == s_memWriteReq).B
//   io.mem.req.bits.memtype := ControlConst.memHex

//   io.mmio.req.valid := s2_valid && ismmio
//   io.mmio.req.bits.addr := s2_addr
//   io.mmio.req.bits.data := s2_data
//   io.mmio.req.bits.wen := s2_wen
//   io.mmio.req.bits.memtype := s2_memtype

//   switch(state) {
//     is(s_idle) {
//       when(!hit && s2_valid) {
//         state := Mux(
//           ismmio,
//           s_mmioReq,
//           Mux(cacheline_meta.dirty, s_memWriteReq, s_memReadReq)
//         )
//       }
//     }
//     is(s_memReadReq) {
//       when(io.mem.req.fire()) {
//         state := s_memReadResp
//       }
//     }
//     is(s_memReadResp) {
//       when(io.mem.resp.fire()) {
//         when(io.mem.resp.fire()) { state := s_idle }
//       }
//     }
//     is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
//     is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_memReadReq } }
//     is(s_mmioReq) { when(io.mmio.req.fire()) { state := s_mmioResp } }
//     is(s_mmioResp) { when(io.mmio.resp.fire()) { state := s_idle } }
//     is(s_wait_resp) { when(io.in.resp.fire()) { state := s_idle } }
//     is(s_release) {}
//   }

//   when(!s2_valid) { state := s_idle }

//   val fetched_data = io.mem.resp.bits.data
//   val fetched_vec = Wire(new CacheLineData)
//   fetched_vec.data := fetched_data

//   val target_data = Mux(hit, cacheline_data, fetched_vec)
//   result := DontCare
//   when(s2_valid) {
//     when(s2_wen) {
//       when(state === s_memReadResp) {
//         val newdata = Wire(new CacheLineData)
//         val filled_data = WireInit(UInt(xlen.W), 0.U(xlen.W))
//         val offset = s2_addr(offsetLength - lineLength - 1, 0) << 3
//         val mask = WireInit(UInt(xlen.W), 0.U)
//         switch(s2_memtype) {
//           is(memXXX) {
//             filled_data := s2_data
//             mask := Fill(xlen, 1.U(1.W)) << offset
//           }
//           is(memByte) {
//             filled_data := Fill(8, s2_data(7, 0))
//             mask := Fill(8, 1.U(1.W)) << offset
//           }
//           is(memHalf) {
//             filled_data := Fill(4, s2_data(15, 0))
//             mask := Fill(16, 1.U(1.W)) << offset
//           }
//           is(memWord) {
//             filled_data := Fill(2, s2_data(31, 0))
//             mask := Fill(32, 1.U(1.W)) << offset
//           }
//           is(memDouble) {
//             filled_data := s2_data
//             mask := Fill(xlen, 1.U(1.W)) << offset
//           }
//           is(memByteU) {
//             filled_data := Fill(8, s2_data(7, 0))
//             mask := Fill(8, 1.U(1.W)) << offset
//           }
//           is(memHalfU) {
//             filled_data := Fill(4, s2_data(15, 0))
//             mask := Fill(16, 1.U(1.W)) << offset
//           }
//           is(memWordU) {
//             filled_data := Fill(2, s2_data(31, 0))
//             mask := Fill(32, 1.U(1.W)) << offset
//           }
//         }
//         newdata.data := (mask & filled_data) | (~mask & target_data.data)
//         dataArray.write(s2_index, newdata)
//         val new_meta = Wire(new MetaData)
//         new_meta.valid := true.B
//         new_meta.dirty := true.B
//         new_meta.tag := s2_tag
//         metaArray.write(s2_index, new_meta)
//         printf(
//           p"dcache write: s2_index=0x${Hexadecimal(s2_index)}, new_data=${newdata}, new_meta=${new_meta}\n"
//         )
//       }
//     }.otherwise {
//       when(state === s_memReadResp || state === s_mmioResp) {
//         val offset = s2_addr(offsetLength - lineLength - 1, 0) << 3
//         val mask = WireInit(UInt(xlen.W), 0.U)
//         val realdata = WireInit(UInt(xlen.W), 0.U)
//         switch(s2_memtype) {
//           is(memXXX) { result := target_data.data }
//           is(memByte) {
//             mask := Fill(8, 1.U(1.W)) << offset
//             realdata := (target_data.data & mask) >> offset
//             result := Cat(Fill(56, realdata(7)), realdata(7, 0))
//           }
//           is(memHalf) {
//             mask := Fill(16, 1.U(1.W)) << offset
//             realdata := (target_data.data & mask) >> offset
//             result := Cat(Fill(48, realdata(15)), realdata(15, 0))
//           }
//           is(memWord) {
//             mask := Fill(32, 1.U(1.W)) << offset
//             realdata := (target_data.data & mask) >> offset
//             result := Cat(Fill(32, realdata(31)), realdata(31, 0))
//           }
//           is(memDouble) { result := target_data.data }
//           is(memByteU) {
//             mask := Fill(8, 1.U(1.W)) << offset
//             realdata := (target_data.data & mask) >> offset
//             result := Cat(Fill(56, 0.U), realdata(7, 0))
//           }
//           is(memHalfU) {
//             mask := Fill(16, 1.U(1.W)) << offset
//             realdata := (target_data.data & mask) >> offset
//             result := Cat(Fill(48, 0.U), realdata(15, 0))
//           }
//           is(memWordU) {
//             mask := Fill(32, 1.U(1.W)) << offset
//             realdata := (target_data.data & mask) >> offset
//             result := Cat(Fill(32, 0.U), realdata(31, 0))
//           }
//         }
//         when(!ismmio) {
//           dataArray.write(s2_index, target_data)
//           val new_meta = Wire(new MetaData)
//           new_meta.valid := true.B
//           new_meta.dirty := false.B
//           new_meta.tag := s2_tag
//           metaArray.write(s2_index, new_meta)
//         }
//         printf(
//           p"dcache read: offset=${offset}, mask=${mask}, realdata=${realdata}\n"
//         )
//       }
//     }
//   }

//   printf(p"----------${cacheName} Stage 2----------\n")
//   printf(
//     "state=%d, stall=%d, ismmio=%d, hit=%d, result=%x\n",
//     state,
//     stall,
//     ismmio,
//     hit,
//     result
//   )
//   printf("s2_valid=%d, s2_addr=%x, s2_index=%x\n", s2_valid, s2_addr, s2_index)
//   printf("s2_data=%x, s2_wen=%d, s2_memtype=%d\n", s2_data, s2_wen, s2_memtype)
//   printf("s2_tag=%x, s2_wordoffset=%x\n", s2_tag, s2_wordoffset)
//   printf(p"hitVec=${hitVec}, invalidVec=${invalidVec}\n")
//   printf(p"s2_meta=${s2_meta}, s2_cacheline=${s2_cacheline}\n")
//   printf(p"cacheline_meta=${cacheline_meta},cacheline_data=${cacheline_data}\n")
//   printf(p"dataArray(0x200)=${dataArray(0x200)},metaArray(0x200)=${metaArray(0x200)}\n")
//   printf("-----------------------------------------------\n")

//   printf(p"----------${cacheName} io.in----------\n")
//   printf(
//     "req.valid = %d, req.addr = %x, req.data = %x, req.wen = %d, req.memtype = %d, resp.valid = %d, resp.data = %x\n",
//     io.in.req.valid,
//     io.in.req.bits.addr,
//     io.in.req.bits.data,
//     io.in.req.bits.wen,
//     io.in.req.bits.memtype,
//     io.in.resp.valid,
//     io.in.resp.bits.data
//   )
//   printf("-----------------------------------------------\n")
//   printf(p"----------${cacheName} io.mem----------\n")
//   printf(
//     "req.valid = %d, req.addr = %x, req.data = %x, req.wen = %d, req.memtype = %d, resp.valid = %d, resp.data = %x\n",
//     io.mem.req.valid,
//     io.mem.req.bits.addr,
//     io.mem.req.bits.data,
//     io.mem.req.bits.wen,
//     io.mem.req.bits.memtype,
//     io.mem.resp.valid,
//     io.mem.resp.bits.data
//   )
//   printf("-----------------------------------------------\n")
// }
