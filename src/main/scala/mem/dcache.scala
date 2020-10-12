// package mem

// import chisel3._
// import chisel3.util._
// import rv64_3stage._
// import bus._
// import device._

// case class CacheConfig(
//     readOnly: Boolean = false,
//     name: String = "cache", // used for debug info
//     replacementPolicy: String = "random", // random or lru ... (unfinished)
//     userBits: Int = 0,
//     idBits: Int = 0,
//     // cacheLevel: Int = 1,
//     ways: Int = 1, // set associativity
//     lines: Int = 4, // number of `xlen`-bit blocks in each cache line
//     totalSize: Int = 32 // K Bytes
// )

// trait CacheParams extends phvntomParams {
//   implicit val cacheConfig: CacheConfig

//   val readOnly = cacheConfig.readOnly
//   val cacheName = cacheConfig.name
//   val replacementPolicy = cacheConfig.replacementPolicy
//   val userBits = cacheConfig.userBits
//   val idBits = cacheConfig.idBits
//   // val cacheLevel = cacheConfig.cacheLevel
//   val nWays = cacheConfig.ways
//   val nLine = cacheConfig.lines
//   val nBytes = cacheConfig.totalSize * 1024
//   val nBits = nBytes / 8
//   val lineBits = nLine * xlen
//   val lineBytes = lineBits / 8
//   val lineLength = log2Ceil(nLine)
//   val nSets = nBytes / lineBytes / nWays
//   val offsetLength = log2Ceil(lineBytes)
//   val indexLength = log2Ceil(nSets)
//   val tagLength = xlen - (indexLength + offsetLength)
//   val policy: ReplacementPolicyBase =
//     if (replacementPolicy == "lru") { LRUPolicy }
//     else { RandomPolicy }
// }

// class CacheIO(implicit val cacheConfig: CacheConfig)
//     extends Bundle
//     with CacheParams {
//   val in = new MemIO
//   val mem = Flipped(new MemIO(lineBits))
//   val mmio = Flipped(new MemIO)
// }

// class MetaData(implicit val cacheConfig: CacheConfig)
//     extends Bundle
//     with CacheParams {
//   val valid = Bool()
//   val dirty = if (!readOnly) { Bool() }
//   else { null }
//   val replacementMeta = if (replacementPolicy == "lru") {
//     UInt(log2Ceil(nWays).W)
//   } else if (replacementPolicy == "plru") { Bool() }
//   else if (replacementPolicy == "nmru") { Bool() }
//   else { null }
//   val tag = UInt(tagLength.W)
// }

// class CacheLineData(implicit val cacheConfig: CacheConfig)
//     extends Bundle
//     with CacheParams {
//   val data = Vec(nLine, UInt(xlen.W)) // UInt(lineBits.W)
// }

// class DCache(implicit val cacheConfig: CacheConfig)
//     extends Module
//     with CacheParams {
//   val io = IO(new CacheIO)

//   // Module Used
//   val metaArray = SyncReadMem(nSets, Vec(nWays, new MetaData))
//   val dataArray = SyncReadMem(nSets, Vec(nWays, new CacheLineData))
//   val stall = Wire(Bool())
//   val isForward = false.B

//   /* stage1 registers */
//   // val s1_valid = RegInit(Bool(), false.B)
//   // val s1_addr = RegInit(UInt(xlen.W), 0.U)
//   // val s1_index = RegInit(UInt(indexLength.W), 0.U)
//   // val s1_data = RegInit(UInt(xlen.W), 0.U)
//   // val s1_wen = RegInit(Bool(), false.B)
//   // val s1_memtype = RegInit(UInt(xlen.W), 0.U)

//   /* stage2 registers */
//   val s2_valid = RegInit(Bool(), false.B)
//   val s2_addr = RegInit(UInt(xlen.W), 0.U)
//   val s2_index = RegInit(UInt(indexLength.W), 0.U)
//   val s2_data = RegInit(UInt(xlen.W), 0.U)
//   val s2_wen = RegInit(Bool(), false.B)
//   val s2_memtype = RegInit(UInt(xlen.W), 0.U)
//   val s2_meta = Reg(Vec(nWays, new MetaData))
//   val s2_cacheline = Reg(Vec(nWays, new CacheLineData))

//   // ******************************
//   //           Stage1
//   // ******************************
//   val s1_valid = io.in.req.valid
//   val s1_addr = io.in.req.bits.addr
//   val s1_index =
//     io.in.req.bits.addr(indexLength + offsetLength - 1, indexLength)
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
//   val s2_tag = io.in.req.bits.addr(xlen - 1, xlen - 1 - tagLength)
//   val s2_lineoffset =
//     io.in.req.bits.addr(offsetLength - 1, offsetLength - lineLength)
//   val s2_wordoffset = io.in.req.bits.addr(offsetLength - lineLength - 1, 0)

//   val hitVec = VecInit(s2_meta.map(m => m.valid && m.tag === s2_tag)).asUInt
//   val invalidVec = VecInit(s2_meta.map(m => !m.valid)).asUInt
//   val victim_index = policy.choose_victim(s2_meta)
//   val cacheline_meta = Mux1H(hitVec, s2_meta)
//   val cacheline_data = Mux1H(hitVec, s2_cacheline)
//   val ismmio = AddressSpace.isMMIO(s2_addr)
//   val hit = hitVec.orR && !ismmio
//   val result = Wire(UInt(xlen.W))
//   stall := s2_valid && !hit && !io.mem.resp.valid // wait for data

//   val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: s_release :: Nil =
//     Enum(9)
//   val state = RegInit(s_idle)
//   val read_address = s2_addr(xlen - 1, lineLength)
//   val write_address =
//     Cat(s2_meta(victim_index).tag, s2_index, 0.U(lineLength.W))

//   io.in.resp.valid := s2_valid && (hit || io.mem.resp.valid)
//   io.in.resp.bits.data := result

//   switch(s2_memtype) {
//     is(memXXX) { io.out.w.bits.data := io.in.req.bits.data }
//     is(memByte) { io.out.w.bits.data := Fill(8, io.in.req.bits.data(7, 0)) }
//     is(memHalf) {
//       io.out.w.bits.data := Fill(4, io.in.req.bits.data(15, 0))
//     }
//     is(memWord) {
//       io.out.w.bits.data := Fill(2, io.in.req.bits.data(31, 0))
//     }
//     is(memDouble) { io.out.w.bits.data := io.in.req.bits.data }
//     is(memByteU) {
//       io.out.w.bits.data := Fill(8, io.in.req.bits.data(7, 0))
//     }
//     is(memHalfU) {
//       io.out.w.bits.data := Fill(4, io.in.req.bits.data(15, 0))
//     }
//     is(memWordU) {
//       io.out.w.bits.data := Fill(2, io.in.req.bits.data(31, 0))
//     }
//   }
//   val offset = io.in.req.bits.addr(blen - 1, 0)
//   switch(s2_memtype) {
//     is(memXXX) { io.out.w.bits.strb := Fill(xlen / 8, 1.U(1.W)) }
//     is(memByte) { io.out.w.bits.strb := Fill(1, 1.U(1.W)) << offset }
//     is(memHalf) { io.out.w.bits.strb := Fill(2, 1.U(1.W)) << offset }
//     is(memWord) { io.out.w.bits.strb := Fill(4, 1.U(1.W)) << offset }
//     is(memDouble) { io.out.w.bits.strb := Fill(xlen / 8, 1.U(1.W)) }
//     is(memByteU) { io.out.w.bits.strb := Fill(1, 1.U(1.W)) << offset }
//     is(memHalfU) { io.out.w.bits.strb := Fill(2, 1.U(1.W)) << offset }
//     is(memWordU) { io.out.w.bits.strb := Fill(4, 1.U(1.W)) << offset }
//   }

//   switch(s2_memtype) {
//       is(memXXX) { io.in.resp.bits.data := io.out.r.bits.data }
//       is(memByte) {
//         mask := Fill(8, 1.U(1.W)) << offset
//         realdata := (io.out.r.bits.data & mask) >> offset
//         io.in.resp.bits.data := Cat(Fill(56, realdata(7)), realdata(7, 0))
//       }
//       is(memHalf) {
//         mask := Fill(16, 1.U(1.W)) << offset
//         realdata := (io.out.r.bits.data & mask) >> offset
//         io.in.resp.bits.data := Cat(Fill(48, realdata(15)), realdata(15, 0))
//       }
//       is(memWord) {
//         mask := Fill(32, 1.U(1.W)) << offset
//         realdata := (io.out.r.bits.data & mask) >> offset
//         io.in.resp.bits.data := Cat(Fill(32, realdata(31)), realdata(31, 0))
//       }
//       is(memDouble) { io.in.resp.bits.data := io.out.r.bits.data }
//       is(memByteU) {
//         mask := Fill(8, 1.U(1.W)) << offset
//         realdata := (io.out.r.bits.data & mask) >> offset
//         io.in.resp.bits.data := Cat(Fill(56, 0.U), realdata(7, 0))
//       }
//       is(memHalfU) {
//         mask := Fill(16, 1.U(1.W)) << offset
//         realdata := (io.out.r.bits.data & mask) >> offset
//         io.in.resp.bits.data := Cat(Fill(48, 0.U), realdata(15, 0))
//       }
//       is(memWordU) {
//         mask := Fill(32, 1.U(1.W)) << offset
//         realdata := (io.out.r.bits.data & mask) >> offset
//         io.in.resp.bits.data := Cat(Fill(32, 0.U), realdata(31, 0))
//       }
//     }

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
//       when(!hit) {
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
//         when(io.mem.resp.fire()) { state := s_wait_resp }
//       }
//     }
//     is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
//     is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_memReadReq } }
//     is(s_mmioReq) { when(io.mmio.req.fire()) { state := s_mmioResp } }
//     is(s_mmioResp) { when(io.mmio.resp.fire()) { state := s_wait_resp } }
//     is(s_wait_resp) { when(io.in.resp.fire()) { state := s_idle } }
//     is(s_release) {}
//   }

//   val fetched_data = io.mem.resp.bits.data
//   val fetched_vec = new CacheLineData
//   for (i <- 0 until nLine) {
//     fetched_vec.data(i) := fetched_data(i * (xlen + 1) - 1, i * xlen)
//   }

//   val target_data = Mux(hit, cacheline_data, fetched_vec)
//   result := DontCare
//   when(s2_wen) {
//     val newdata = target_data
//     newdata.data(s2_lineoffset) := s2_data
//     val writeData = VecInit(Seq.fill(nWays)(newdata))
//     dataArray.write(s2_index, writeData, hitVec.asBools)
//     val new_meta = policy.update_meta(s2_meta, hitVec, victim_index)
//     new_meta(victim_index).dirty := true.B
//     metaArray.write(s2_index, new_meta, hitVec.asBools)
//   }.otherwise {
//     result := target_data.data(s2_lineoffset)
//     val writeData = VecInit(Seq.fill(nWays)(target_data))
//     dataArray.write(s2_index, writeData, hitVec.asBools)
//     val new_meta = policy.update_meta(s2_meta, hitVec, victim_index)
//     metaArray.write(s2_index, new_meta, hitVec.asBools)
//   }

//   printf(p"----------${cacheName} Stage 2----------\n")
//   printf("s2_valid=%d, s2_addr=%x, s2_index=%x\n", s2_valid, s2_addr, s2_index)
//   printf("s2_data=%x, s2_wen=%d, s2_memtype=%d\n", s2_data, s2_wen, s2_memtype)
//   printf("-----------------------------------------------\n")
// }
