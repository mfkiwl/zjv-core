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
//   val metaArray = Mem(nSets, new MetaData)
//   val dataArray = Mem(nSets, new CacheLineData)

//   /* stage2 registers */
//   val s1_valid = WireInit(Bool(), false.B)
//   val s1_addr = WireInit(UInt(xlen.W), 0.U)
//   val s1_index = WireInit(UInt(indexLength.W), 0.U)
//   val s1_data = WireInit(UInt(xlen.W), 0.U)
//   val s1_wen = WireInit(Bool(), false.B)
//   val s1_memtype = WireInit(UInt(xlen.W), 0.U)
//   val s1_meta = Wire(new MetaData)
//   val s1_cacheline = Wire(new CacheLineData)
//   val s1_tag = WireInit(UInt(tagLength.W), 0.U)
//   val s1_wordoffset = WireInit(UInt((offsetLength - lineLength).W), 0.U)

//   // ******************************
//   //           Stage1
//   // ******************************
//   s1_valid := io.in.req.valid
//   s1_addr := io.in.req.bits.addr
//   s1_index := s1_addr(indexLength + offsetLength - 1, offsetLength)
//   s1_data := io.in.req.bits.data
//   s1_wen := io.in.req.bits.wen
//   s1_memtype := io.in.req.bits.memtype
//   s1_meta := metaArray(s1_index)
//   s1_cacheline := dataArray(s1_index)
//   s1_tag := s1_addr(xlen - 1, xlen - tagLength)
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

//   io.mem.req.valid := s1_valid && (state === s_memReadReq || state === s_memWriteReq) // (!hit && !ismmio)
//   io.mem.req.bits.addr := Mux(
//     state === s_memWriteReq,
//     write_address,
//     read_address
//   )
//   io.mem.req.bits.data := cacheline_data.data(victim_index).asUInt
//   io.mem.req.bits.wen := (state == s_memWriteReq).B
//   io.mem.req.bits.memtype := ControlConst.memDouble

//   io.mmio.req.valid := s1_valid && ismmio
//   io.mmio.req.bits.addr := s1_addr
//   io.mmio.req.bits.data := s1_data
//   io.mmio.req.bits.wen := s1_wen
//   io.mmio.req.bits.memtype := s1_memtype

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

//   val fetched_data = Wire(UInt(xlen.W))
//   when(ismmio) { fetched_data := io.mmio.resp.bits.data }
//     .otherwise { fetched_data := io.mem.resp.bits.data }
//   val fetched_vec = Wire(new CacheLineData)
//   fetched_vec.data := fetched_data

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
//         newdata.data := (mask & filled_data) | (~mask & target_data.data)
//         dataArray(s1_index) := newdata
//         val new_meta = Wire(new MetaData)
//         new_meta.valid := true.B
//         new_meta.dirty := true.B
//         new_meta.tag := s1_tag
//         metaArray(s1_index) := new_meta
//         printf(
//           p"dcache write: s1_index=0x${Hexadecimal(s1_index)}, new_data=${newdata}, new_meta=${new_meta}\n"
//         )
//       }
//     }.otherwise {
//       when(hit || io.mem.resp.valid || io.mmio.resp.valid) {
//         val offset = s1_wordoffset << 3
//         val mask = WireInit(UInt(xlen.W), 0.U)
//         val realdata = WireInit(UInt(xlen.W), 0.U)
//         switch(s1_memtype) {
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
//           dataArray(s1_index) := target_data
//           val new_meta = Wire(new MetaData)
//           new_meta.valid := true.B
//           new_meta.dirty := false.B
//           new_meta.tag := s1_tag
//           metaArray(s1_index) := new_meta
//           printf(
//             p"dcache read: offset=${offset}, mask=${mask}, realdata=${realdata}\n"
//           )
//         }

//       }
//     }
//   }

//   printf(p"----------${cacheName} Debug Info----------\n")
//   printf(
//     "state=%d, ismmio=%d, hit=%d, result=%x\n",
//     state,
//     ismmio,
//     hit,
//     result
//   )
//   printf("s1_valid=%d, s1_addr=%x, s1_index=%x\n", s1_valid, s1_addr, s1_index)
//   printf("s1_data=%x, s1_wen=%d, s1_memtype=%d\n", s1_data, s1_wen, s1_memtype)
//   printf("s1_tag=%x, s1_wordoffset=%x\n", s1_tag, s1_wordoffset)
//   printf(p"hitVec=${hitVec}, invalidVec=${invalidVec}\n")
//   printf(p"s1_cacheline=${s1_cacheline}, s1_meta=${s1_meta}\n")
//   printf(
//     p"cacheline_data=${cacheline_data}, cacheline_meta=${cacheline_meta}\n"
//   )
//   // printf(
//   //   p"dataArray(0x0)=${dataArray(0x0.U)},metaArray(0x0)=${metaArray(0x0.U)}\n"
//   // )
//   // printf(
//   //   p"dataArray(0x200)=${dataArray(0x200.U)},metaArray(0x200)=${metaArray(0x200.U)}\n"
//   // )
//   // printf(
//   //   p"dataArray(0x201)=${dataArray(0x201.U)},metaArray(0x201)=${metaArray(0x201.U)}\n"
//   // )
//   // printf(
//   //   p"dataArray(0x204)=${dataArray(0x204.U)},metaArray(0x204)=${metaArray(0x204.U)}\n"
//   // )
//   printf(
//     p"dataArray(s1_index)=${dataArray(s1_index)},metaArray(s1_index)=${metaArray(s1_index)}\n"
//   )
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
//   printf(p"----------${cacheName} io.mmio----------\n")
//   printf(
//     "req.valid = %d, req.addr = %x, req.data = %x, req.wen = %d, req.memtype = %d, resp.valid = %d, resp.data = %x\n",
//     io.mmio.req.valid,
//     io.mmio.req.bits.addr,
//     io.mmio.req.bits.data,
//     io.mmio.req.bits.wen,
//     io.mmio.req.bits.memtype,
//     io.mmio.resp.valid,
//     io.mmio.resp.bits.data
//   )
//   printf("-----------------------------------------------\n")
// }
