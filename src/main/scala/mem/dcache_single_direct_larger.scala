// package mem

// import chisel3._
// import chisel3.util._
// import rv64_3stage._
// import rv64_3stage.ControlConst._
// import bus._
// import device._
// import utils._

// trait CacheParameters extends phvntomParams {
//   val cacheName = "dcache" // used for debug info
//   val userBits = 0
//   val idBits = 0
//   val nWays = 1
//   val nLine = 4
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
//   val mem = Flipped(new MemIO(lineBits))
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
//   val data = Vec(nLine, UInt(xlen.W)) // UInt(lineBits.W)
//   override def toPrintable: Printable =
//     p"CacheLineData(data = ${data})"
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
//   val metaArray = Mem(nSets, new MetaData)
//   val dataArray = Mem(nSets, new CacheLineData)

//   /* stage2 registers */
//   val s1_valid = WireInit(Bool(), false.B)
//   val s1_addr = Wire(UInt(xlen.W))
//   val s1_index = Wire(UInt(indexLength.W))
//   val s1_data = Wire(UInt(xlen.W))
//   val s1_wen = WireInit(Bool(), false.B)
//   val s1_memtype = Wire(UInt(xlen.W))
//   val s1_meta = Wire(new MetaData)
//   val s1_cacheline = Wire(new CacheLineData)
//   val s1_tag = Wire(UInt(tagLength.W))
//   val s1_lineoffset = Wire(UInt(lineLength.W))
//   val s1_wordoffset = Wire(UInt((offsetLength - lineLength).W))

//   s1_valid := io.in.req.valid
//   s1_addr := io.in.req.bits.addr
//   s1_index := s1_addr(indexLength + offsetLength - 1, offsetLength)
//   s1_data := io.in.req.bits.data
//   s1_wen := io.in.req.bits.wen
//   s1_memtype := io.in.req.bits.memtype
//   s1_meta := metaArray(s1_index)
//   s1_cacheline := dataArray(s1_index)
//   s1_tag := s1_addr(xlen - 1, xlen - tagLength)
//   s1_lineoffset := s1_addr(offsetLength - 1, offsetLength - lineLength)
//   s1_wordoffset := s1_addr(offsetLength - lineLength - 1, 0)

//   val hitVec = s1_meta.valid && s1_meta.tag === s1_tag
//   val invalidVec = !s1_meta.valid
//   val victim_index = 0.U
//   val cacheline_meta = s1_meta
//   val cacheline_data = s1_cacheline
//   val ismmio = AddressSpace.isMMIO(s1_addr)
//   val hit = hitVec.orR && !ismmio
//   val result = Wire(UInt(xlen.W))

//   val s_idle :: s_memReadReq :: s_memReadWaitResp :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: s_release :: Nil =
//     Enum(10)
//   val state = RegInit(s_idle)
//   val read_address = Cat(s1_addr(xlen - 1, offsetLength), 0.U(offsetLength.W))
//   val write_address = Cat(s1_meta.tag, s1_index, 0.U(offsetLength.W))

//   io.in.resp.valid := s1_valid && (hit || io.mem.resp.valid || io.mmio.resp.valid)
//   io.in.resp.bits.data := result
//   io.in.req.ready := state === s_idle

//   io.mem.req.valid := s1_valid && (state === s_memReadReq || state === s_memReadResp || state === s_memWriteReq || state === s_memWriteResp) // (!hit && !ismmio)
//   io.mem.req.bits.addr := Mux(
//     state === s_memWriteReq,
//     write_address,
//     read_address
//   )
//   io.mem.req.bits.data := cacheline_data.data(victim_index).asUInt
//   io.mem.req.bits.wen := (state == s_memWriteReq).B
//   io.mem.req.bits.memtype := ControlConst.memOcto
//   io.mem.resp.ready := s1_valid && state === s_memReadResp

//   io.mmio.req.valid := s1_valid && (state === s_mmioReq || state === s_mmioResp)
//   io.mmio.req.bits.addr := s1_addr
//   io.mmio.req.bits.data := s1_data
//   io.mmio.req.bits.wen := s1_wen
//   io.mmio.req.bits.memtype := s1_memtype
//   io.mmio.resp.ready := s1_valid && state === s_mmioResp

//   switch(state) {
//     is(s_idle) {
//       when(!hit && s1_valid) {
//         state := Mux(
//           ismmio,
//           s_mmioReq,
//           Mux(cacheline_meta.dirty, s_memWriteReq, s_memReadReq)
//         )
//       }
//     }
//     is(s_memReadReq) {
//       when(io.mem.req.fire()) { state := s_memReadResp }
//     }
//     is(s_memReadResp) {
//       when(io.mem.resp.fire()) { state := s_idle }
//     }
//     // is(s_memReadResp) {
//     //   state := s_idle
//     // }
//     is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
//     is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_memReadReq } }
//     is(s_mmioReq) { when(io.mmio.req.fire()) { state := s_mmioResp } }
//     is(s_mmioResp) { when(io.mmio.resp.fire()) { state := s_idle } }
//     is(s_wait_resp) { when(io.in.resp.fire()) { state := s_idle } }
//     is(s_release) {}
//   }

//   when(!s1_valid) { state := s_idle }

//   val fetched_data = io.mem.resp.bits.data
//   val fetched_vec = Wire(new CacheLineData)
//   // fetched_vec.data := fetched_data
//   for (i <- 0 until nLine) {
//     fetched_vec.data(i) := fetched_data((i + 1) * xlen - 1, i * xlen)
//   }

//   val target_data = Mux(hit, cacheline_data, fetched_vec)
//   result := DontCare
//   when(s1_valid) {
//     when(s1_wen) {
//       when(hit || io.mem.resp.valid) {
//         val newdata = Wire(new CacheLineData)
//         val filled_data = WireInit(UInt(xlen.W), 0.U(xlen.W))
//         val offset = s1_wordoffset << 3
//         val mask = WireInit(UInt(xlen.W), 0.U)
//         switch(s1_memtype) {
//           is(memXXX) {
//             filled_data := s1_data
//             mask := Fill(xlen, 1.U(1.W)) << offset
//           }
//           is(memByte) {
//             filled_data := Fill(8, s1_data(7, 0))
//             mask := Fill(8, 1.U(1.W)) << offset
//           }
//           is(memHalf) {
//             filled_data := Fill(4, s1_data(15, 0))
//             mask := Fill(16, 1.U(1.W)) << offset
//           }
//           is(memWord) {
//             filled_data := Fill(2, s1_data(31, 0))
//             mask := Fill(32, 1.U(1.W)) << offset
//           }
//           is(memDouble) {
//             filled_data := s1_data
//             mask := Fill(xlen, 1.U(1.W)) << offset
//           }
//           is(memByteU) {
//             filled_data := Fill(8, s1_data(7, 0))
//             mask := Fill(8, 1.U(1.W)) << offset
//           }
//           is(memHalfU) {
//             filled_data := Fill(4, s1_data(15, 0))
//             mask := Fill(16, 1.U(1.W)) << offset
//           }
//           is(memWordU) {
//             filled_data := Fill(2, s1_data(31, 0))
//             mask := Fill(32, 1.U(1.W)) << offset
//           }
//         }
//         newdata := target_data
//         newdata.data(
//           s1_lineoffset
//         ) := (mask & filled_data) | (~mask & target_data.data(s1_lineoffset))
//         dataArray(s1_index) := newdata
//         val new_meta = Wire(new MetaData)
//         new_meta.valid := true.B
//         new_meta.dirty := true.B
//         new_meta.tag := s1_tag
//         metaArray(s1_index) := new_meta
//         // printf(
//         //   p"dcache write: s1_index=0x${Hexadecimal(s1_index)}, new_meta=${new_meta}\n"
//         // )
//         // printf(p"\ttarget_data=${target_data}\n")
//       }
//     }.otherwise {
//       when(hit || io.mem.resp.valid || io.mmio.resp.valid) {
//         val result_data = target_data.data(s1_lineoffset)
//         val offset = s1_wordoffset << 3
//         val mask = WireInit(UInt(xlen.W), 0.U)
//         val realdata = WireInit(UInt(xlen.W), 0.U)
//         val mem_result = WireInit(UInt(xlen.W), 0.U)
//         switch(s1_memtype) {
//           is(memXXX) { mem_result := result_data }
//           is(memByte) {
//             mask := Fill(8, 1.U(1.W)) << offset
//             realdata := (result_data & mask) >> offset
//             mem_result := Cat(Fill(56, realdata(7)), realdata(7, 0))
//           }
//           is(memHalf) {
//             mask := Fill(16, 1.U(1.W)) << offset
//             realdata := (result_data & mask) >> offset
//             mem_result := Cat(Fill(48, realdata(15)), realdata(15, 0))
//           }
//           is(memWord) {
//             mask := Fill(32, 1.U(1.W)) << offset
//             realdata := (result_data & mask) >> offset
//             mem_result := Cat(Fill(32, realdata(31)), realdata(31, 0))
//           }
//           is(memDouble) { mem_result := result_data }
//           is(memByteU) {
//             mask := Fill(8, 1.U(1.W)) << offset
//             realdata := (result_data & mask) >> offset
//             mem_result := Cat(Fill(56, 0.U), realdata(7, 0))
//           }
//           is(memHalfU) {
//             mask := Fill(16, 1.U(1.W)) << offset
//             realdata := (result_data & mask) >> offset
//             mem_result := Cat(Fill(48, 0.U), realdata(15, 0))
//           }
//           is(memWordU) {
//             mask := Fill(32, 1.U(1.W)) << offset
//             realdata := (result_data & mask) >> offset
//             mem_result := Cat(Fill(32, 0.U), realdata(31, 0))
//           }
//         }
//         result := Mux(ismmio, io.mmio.resp.bits.data, mem_result)
// //         printf(
// //           p"[${GTimer()}]: dcache read: offset=${Hexadecimal(offset)}, mask=${Hexadecimal(mask)}, realdata=${Hexadecimal(realdata)}\n"
// //         )
//         when(!ismmio) {
//           dataArray(s1_index) := target_data
//           val new_meta = Wire(new MetaData)
//           new_meta.valid := true.B
//           new_meta.dirty := false.B
//           new_meta.tag := s1_tag
//           metaArray(s1_index) := new_meta
//           // printf(
//           //   p"dcache write: s1_index=0x${Hexadecimal(s1_index)}, new_meta=${new_meta}\n"
//           // )
//           // printf(p"\ttarget_data=${target_data}\n")
//         }
//       }
//     }
//   }

// //   printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
// //   printf(
// //     "state=%d, ismmio=%d, hit=%d, result=%x\n",
// //     state,
// //     ismmio,
// //     hit,
// //     result
// //   )
// //   printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
// //   printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
// //    printf(
// //      "s1_tag=%x, s1_lineoffset=%x, s1_wordoffset=%x\n",
// //      s1_tag,
// //      s1_lineoffset,
// //      s1_wordoffset
// //    )
// //   printf(p"hitVec=${hitVec}, invalidVec=${invalidVec}\n")
// //    printf(p"s1_cacheline=${s1_cacheline}\n")
// //    printf(p"s1_meta=${s1_meta}\n")
// //    printf(p"cacheline_data=${cacheline_data}\n")
// //    printf(p"cacheline_meta=${cacheline_meta}\n")
// //    printf(p"dataArray(s1_index)=${dataArray(s1_index)}\n")
// //    printf(p"metaArray(s1_index)=${metaArray(s1_index)}\n")
// //   printf(p"fetched_data=${Hexadecimal(fetched_data)}\n")
// //   printf(p"fetched_vec=${fetched_vec}\n")
// //   printf(p"----------${cacheName} io.in----------\n")
// //   printf(p"${io.in}\n")
// //    printf(p"----------${cacheName} io.mem----------\n")
// //    printf(p"${io.mem}\n")
// //   printf(p"----------${cacheName} io.mmio----------\n")
// //   printf(p"${io.mmio}\n")
// //   printf("-----------------------------------------------\n")
// }
