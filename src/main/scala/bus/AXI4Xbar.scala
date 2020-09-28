package bus

import chisel3._
import chisel3.util._

class SimpleBusCrossbar1toN(addressSpace: List[(Long, Long)]) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new AXI4Bundle)
    val out = Vec(addressSpace.length, new AXI4Bundle)
  })

  // val s_idle :: s_resp :: s_error :: Nil = Enum(3)
  // val state = RegInit(s_idle)

  // // select the output channel according to the address
  // val addr = io.in.req.bits.addr
  // val outSelVec = VecInit(addressSpace.map(
  //   range => (addr >= range._1.U && addr < (range._1 + range._2).U)))
  // val outSelIdx = PriorityEncoder(outSelVec)
  // val outSel = io.out(outSelIdx)
  // val outSelIdxResp = RegEnable(outSelIdx, outSel.req.fire() && (state === s_idle))
  // val outSelResp = io.out(outSelIdxResp)
  // val reqInvalidAddr = io.in.req.valid && !outSelVec.asUInt.orR

  // when(!(!io.in.req.valid || outSelVec.asUInt.orR) || !(!(io.in.req.valid && outSelVec.asUInt.andR))){
  //   Debug(){
  //     printf("crossbar access bad addr %x, time %d\n", addr, GTimer())
  //   }
  // }
  // // assert(!io.in.req.valid || outSelVec.asUInt.orR, "address decode error, bad addr = 0x%x\n", addr)
  // assert(!(io.in.req.valid && outSelVec.asUInt.andR), "address decode error, bad addr = 0x%x\n", addr)

  // // bind out.req channel
  // (io.out zip outSelVec).map { case (o, v) => {
  //   o.req.bits := io.in.req.bits
  //   o.req.valid := v && (io.in.req.valid && (state === s_idle))
  //   o.resp.ready := v
  // }}

  // switch (state) {
  //   is (s_idle) {
  //     when (outSel.req.fire()) { state := s_resp }
  //     when (reqInvalidAddr) { state := s_error }
  //   }
  //   is (s_resp) { when (outSelResp.resp.fire()) { state := s_idle } }
  //   is (s_error) { when(io.in.resp.fire()){ state := s_idle } }
  // }

  // io.in.resp.valid := outSelResp.resp.fire() || state === s_error
  // io.in.resp.bits <> outSelResp.resp.bits
  // // io.in.resp.bits.exc.get := state === s_error
  // outSelResp.resp.ready := io.in.resp.ready
  // io.in.req.ready := outSel.req.ready || reqInvalidAddr
  io.in <> io.out(0)
}

class SimpleBusCrossbarNto1(n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new AXI4Bundle))
    val out = new AXI4Bundle
  })

  if (n > 1) {
    val arbIdBits = log2Up(n)

    val ar_arb = Module(new Arbiter(new AXI4BundleAR, n))
    val aw_arb = Module(new Arbiter(new AXI4BundleAW, n))

    val out_r_arb_id = io.out.r.bits.id(arbIdBits - 1, 0)
    val out_b_arb_id = io.out.b.bits.id(arbIdBits - 1, 0)

    val w_chosen = Reg(UInt(arbIdBits.W))
    val w_done = RegInit(true.B)

    when(aw_arb.io.out.fire()) {
      w_chosen := aw_arb.io.chosen
      w_done := false.B
    }

    when(io.out.w.fire() && io.out.w.bits.last) {
      w_done := true.B
    }

    for (i <- 0 until n) {
      val m_ar = io.in(i).ar
      val m_aw = io.in(i).aw
      val m_r = io.in(i).r
      val m_b = io.in(i).b
      val a_ar = ar_arb.io.in(i)
      val a_aw = aw_arb.io.in(i)
      val m_w = io.in(i).w

      a_ar <> m_ar
      a_ar.bits.id := Cat(m_ar.bits.id, i.U(arbIdBits.W))

      a_aw <> m_aw
      a_aw.bits.id := Cat(m_aw.bits.id, i.U(arbIdBits.W))

      m_r.valid := io.out.r.valid && out_r_arb_id === i.U
      m_r.bits := io.out.r.bits
      m_r.bits.id := io.out.r.bits.id >> arbIdBits.U

      m_b.valid := io.out.b.valid && out_b_arb_id === i.U
      m_b.bits := io.out.b.bits
      m_b.bits.id := io.out.b.bits.id >> arbIdBits.U

      m_w.ready := io.out.w.ready && w_chosen === i.U && !w_done
    }

    io.out.r.ready := io.in(out_r_arb_id).r.ready
    io.out.b.ready := io.in(out_b_arb_id).b.ready

    io.out.w.bits := io.in(w_chosen).w.bits
    io.out.w.valid := io.in(w_chosen).w.valid && !w_done

    io.out.ar <> ar_arb.io.out

    io.out.aw.bits <> aw_arb.io.out.bits
    io.out.aw.valid := aw_arb.io.out.valid && w_done
    aw_arb.io.out.ready := io.out.aw.ready && w_done

  } else { io.out <> io.in.head }
}

class AXI4Xbar(n: Int, addressSpace: List[(Long, Long)]) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new AXI4Bundle))
    val out = Vec(addressSpace.length, new AXI4Bundle)
  })

  val inXbar = Module(new SimpleBusCrossbarNto1(n))
  val outXbar = Module(new SimpleBusCrossbar1toN(addressSpace))
  inXbar.io.in <> io.in
  outXbar.io.in <> inXbar.io.out
  io.out <> outXbar.io.out
}
