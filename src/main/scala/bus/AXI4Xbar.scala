package bus

import chisel3._
import chisel3.util._

class SimpleBusCrossbar1toN(addressSpace: List[(Long, Long)]) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new AXI4Bundle)
    val out = Vec(addressSpace.length, new AXI4Bundle)
  })

  val s_idle :: s_resp :: s_error :: Nil = Enum(3)
  val r_state = RegInit(s_idle)
  val w_state = RegInit(s_idle)

  // read channel
  // select the output channel according to the address
  val raddr = io.in.ar.bits.addr
  val routSelVec = VecInit(
    addressSpace.map(range =>
      (raddr >= range._1.U && raddr < (range._1 + range._2).U)
    )
  )
  val routSelIdx = PriorityEncoder(routSelVec)
  val routSel = io.out(routSelIdx)
  val routSelIdxResp = RegEnable(routSelIdx, routSel.ar.fire() && (r_state === s_idle))
  val routSelResp = io.out(routSelIdxResp)
  // val reqInvalidAddr = io.in.ar.valid && !routSelVec.asUInt.orR

  // bind out.req channel
  (io.out zip routSelVec).map {
    case (o, v) => {
      o.ar.bits := io.in.ar.bits
      o.ar.valid := v && (io.in.ar.valid && (r_state === s_idle))
      o.r.ready := v
    }
  }

  switch(r_state) {
    is(s_idle) {
      when(routSel.ar.fire()) { r_state := s_resp }
      // when(reqInvalidAddr) { r_state := s_error }
    }
    is(s_resp) { when(routSelResp.r.bits.last) { r_state := s_idle } }
    // is(s_error) { when(io.in.r.fire()) { r_state := s_idle } }
  }

  io.in.r.valid := routSelResp.r.valid // || r_state === s_error
  io.in.r.bits <> routSelResp.r.bits
  // io.in.resp.bits.exc.get := r_state === s_error
  routSelResp.r.ready := io.in.r.ready
  io.in.ar.ready := routSel.ar.ready // || reqInvalidAddr

  // printf("-----------Xbar1toN Debug Start-----------\n")
  // printf(p"routSelVec = ${routSelVec}, routSelIdx = ${routSelIdx}\n")
  // printf(
  //   "aw.valid = %d, w.valid = %d, b.valid = %d, ar.valid = %d, r.valid = %d\n",
  //   routSel.aw.valid,
  //   routSel.w.valid,
  //   routSel.b.valid,
  //   routSel.ar.valid,
  //   routSel.r.valid
  // )
  // printf(
  //   "aw.ready = %d, w.ready = %d, b.ready = %d, ar.ready = %d, r.ready = %d\n",
  //   routSel.aw.ready,
  //   routSel.w.ready,
  //   routSel.b.ready,
  //   routSel.ar.ready,
  //   routSel.r.ready
  // )
  // printf(p"routSel.aw.bits: ${routSel.aw.bits}\n")
  // printf(p"routSel.w.bits: ${routSel.w.bits}\n")
  // printf(p"routSel.b.bits: ${routSel.b.bits}\n")
  // printf(p"routSel.ar.bits: ${routSel.ar.bits}\n")
  // printf(p"routSel.r.bits: ${routSel.r.bits}\n")
  // printf("-----------Xbar1toN Debug Done-----------\n")

  // write channel
  // select the output channel according to the address
  val waddr = io.in.aw.bits.addr
  val woutSelVec = VecInit(
    addressSpace.map(range =>
      (waddr >= range._1.U && waddr < (range._1 + range._2).U)
    )
  )
  val woutSelIdx = PriorityEncoder(woutSelVec)
  val woutSel = io.out(woutSelIdx)
  val woutSelIdxResp = RegEnable(woutSelIdx, woutSel.aw.fire() && (w_state === s_idle))
  val woutSelResp = io.out(woutSelIdxResp)
  // val reqInvalidAddr = io.in.aw.valid && !woutSelVec.asUInt.orR

  // bind out.req channel
  (io.out zip woutSelVec).map {
    case (o, v) => {
      o.aw.bits := io.in.aw.bits
      o.aw.valid := v && (io.in.aw.valid && (w_state === s_idle))
      o.w.bits := io.in.w.bits
      o.w.valid := v && io.in.w.valid
      o.b.ready := v
    }
  }

  switch(w_state) {
    is(s_idle) {
      when(woutSel.aw.fire()) { w_state := s_resp }
      // when(reqInvalidAddr) { w_state := s_error }
    }
    is(s_resp) { when(woutSelResp.b.fire()) { w_state := s_idle } }
    // is(s_error) { when(io.in.r.fire()) { w_state := s_idle } }
  }

  io.in.b.valid := woutSelResp.b.valid // || w_state === s_error
  io.in.b.bits <> woutSelResp.b.bits
  // io.in.resp.bits.exc.get := w_state === s_error
  woutSelResp.b.ready := io.in.b.ready
  io.in.aw.ready := woutSel.aw.ready // || reqInvalidAddr
  io.in.w.ready := woutSel.w.ready

  // when(!(!io.in.req.valid || routSelVec.asUInt.orR) || !(!(io.in.req.valid && routSelVec.asUInt.andR))){
  //   Debug(){
  //     printf("crossbar access bad addr %x, time %d\n", addr, GTimer())
  //   }
  // }
  // assert(!io.in.req.valid || routSelVec.asUInt.orR, "address decode error, bad addr = 0x%x\n", addr)
  // assert(!(io.in.req.valid && routSelVec.asUInt.andR), "address decode error, bad addr = 0x%x\n", addr)
  // io.in.aw <> io.out(1).aw
  // io.in.w <> io.out(1).w
  // io.in.b <> io.out(1).b
  // io.out(0).ar := DontCare
  // io.out(0).r.ready := false.B
  // io.out(0).aw := DontCare
  // io.out(0).w := DontCare
  // io.out(0).b.ready := false.B
  // io.in <> io.out(1)
}

class SimpleBusCrossbarNto1(n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new AXI4Bundle))
    val out = new AXI4Bundle
  })

  if (n > 1) {
    val arbIdBits = log2Up(n)

    val ar_arb = Module(new RRArbiter(new AXI4BundleAR, n)) // or RRArbiter
    val aw_arb = Module(new RRArbiter(new AXI4BundleAW, n)) // or RRArbiter

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

    // printf("out_r_arb_id = %d, out_b_arb_id = %d\n", out_r_arb_id, out_b_arb_id)
  } else { io.out <> io.in.head }

  // printf("-----------XbarNto1 Debug Start-----------\n")
  // printf(
  //   "aw.valid = %d, w.valid = %d, b.valid = %d, ar.valid = %d, r.valid = %d\n",
  //   io.out.aw.valid,
  //   io.out.w.valid,
  //   io.out.b.valid,
  //   io.out.ar.valid,
  //   io.out.r.valid
  // )
  // printf(
  //   "aw.ready = %d, w.ready = %d, b.ready = %d, ar.ready = %d, r.ready = %d\n",
  //   io.out.aw.ready,
  //   io.out.w.ready,
  //   io.out.b.ready,
  //   io.out.ar.ready,
  //   io.out.r.ready
  // )
  // printf(p"out.aw.bits: ${io.out.aw.bits}\n")
  // printf(p"out.w.bits: ${io.out.w.bits}\n")
  // printf(p"out.b.bits: ${io.out.b.bits}\n")
  // printf(p"out.ar.bits: ${io.out.ar.bits}\n")
  // printf(p"out.r.bits: ${io.out.r.bits}\n")
  // printf("-----------XbarNto1 Debug Done-----------\n")
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
