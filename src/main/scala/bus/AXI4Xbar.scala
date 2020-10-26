package bus

import chisel3._
import chisel3.util._
import utils._

class Crossbar1toN(addressSpace: List[(Long, Long)]) extends Module {
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
  val routSelIdxResp =
    RegEnable(routSelIdx, routSel.ar.fire() && (r_state === s_idle))
  val routSelResp = io.out(routSelIdxResp)
  val rreqInvalidAddr = io.in.ar.valid && !routSelVec.asUInt.orR

  // bind out.req channel
  (io.out zip routSelVec).map {
    case (o, v) => {
      o.ar.bits := io.in.ar.bits
      o.ar.valid := v && (io.in.ar.valid && (r_state === s_idle))
      o.r.ready := v
    }
  }
  for (i <- 0 until addressSpace.length) {
    when(routSelIdx === i.U) { // minus base addr
      io.out(i).ar.bits.addr := io.in.ar.bits.addr - addressSpace(i)._1.U
    }
  }

  switch(r_state) {
    is(s_idle) {
      when(routSel.ar.fire()) { r_state := s_resp }
      when(rreqInvalidAddr) { r_state := s_error }
    }
    is(s_resp) { when(routSelResp.r.bits.last) { r_state := s_idle } }
    is(s_error) { when(io.in.r.fire()) { r_state := s_idle } }
  }

  io.in.r.valid := routSelResp.r.valid || r_state === s_error
  io.in.r.bits <> routSelResp.r.bits
  // io.in.resp.bits.exc.get := r_state === s_error
  routSelResp.r.ready := io.in.r.ready
  io.in.ar.ready := (routSel.ar.ready && r_state === s_idle) || rreqInvalidAddr

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
  val woutSelIdxResp =
    RegEnable(woutSelIdx, woutSel.aw.fire() && (w_state === s_idle))
  val woutSelResp = io.out(woutSelIdxResp)
  val wreqInvalidAddr = io.in.aw.valid && !woutSelVec.asUInt.orR

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
  for (i <- 0 until addressSpace.length) {
    when(woutSelIdx === i.U) { // minus base addr
      io.out(i).aw.bits.addr := io.in.aw.bits.addr - addressSpace(i)._1.U
    }
  }

  switch(w_state) {
    is(s_idle) {
      when(woutSel.aw.fire()) { w_state := s_resp }
      when(wreqInvalidAddr) { w_state := s_error }
    }
    is(s_resp) { when(woutSelResp.b.fire()) { w_state := s_idle } }
    is(s_error) { when(io.in.b.fire()) { w_state := s_idle } }
  }

  io.in.b.valid := woutSelResp.b.valid || w_state === s_error
  io.in.b.bits <> woutSelResp.b.bits
  // io.in.resp.bits.exc.get := w_state === s_error
  woutSelResp.b.ready := io.in.b.ready
  io.in.aw.ready := (woutSel.aw.ready && w_state === s_idle) || wreqInvalidAddr
  io.in.w.ready := woutSel.w.ready

  // printf(p"[${GTimer()}]: Xbar1toN Debug Start-----------\n")
  // printf(p"r_state = ${r_state}, routSelVec = ${routSelVec}, routSelIdx = ${routSelIdx}, rreqInvalidAddr = ${rreqInvalidAddr}\n")
  // printf(p"routSel: \n${woutSel}\n")
  // printf("--------------------------------------------------------\n")
  // printf(p"w_state = ${w_state}, woutSelVec = ${woutSelVec}, woutSelIdx = ${woutSelIdx}, wreqInvalidAddr = ${wreqInvalidAddr}\n")
  // printf(p"woutSel: \n${woutSel}\n")
  // printf("-----------Xbar1toN Debug Done-----------\n")
}

class CrossbarNto1(n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new AXI4Bundle))
    val out = new AXI4Bundle
  })

  val s_idle :: s_readResp :: s_writeResp :: Nil = Enum(3)
  val r_state = RegInit(s_idle)
  val inputArb_r = Module(new Arbiter(new AXI4BundleAR, n))
  (inputArb_r.io.in zip io.in.map(_.ar)).map { case (arb, in) => arb <> in }
  val thisReq_r = inputArb_r.io.out
  val inflightSrc_r = Reg(UInt(log2Ceil(n).W))

  io.out.ar.bits := thisReq_r.bits
  // bind correct valid and ready signals
  io.out.ar.valid := thisReq_r.valid && (r_state === s_idle)
  thisReq_r.ready := io.out.ar.ready && (r_state === s_idle)

  io.in.map(_.r.bits := io.out.r.bits)
  io.in.map(_.r.valid := false.B)
  (io.in(inflightSrc_r).r, io.out.r) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  switch(r_state) {
    is(s_idle) {
      when(thisReq_r.fire()) {
        inflightSrc_r := inputArb_r.io.chosen
        when(thisReq_r.valid) { r_state := s_readResp }
      }
    }
    is(s_readResp) {
      when(io.out.r.fire() && io.out.r.bits.last) { r_state := s_idle }
    }
  }

  val w_state = RegInit(s_idle)
  val inputArb_w = Module(new Arbiter(new AXI4BundleAW, n))
  (inputArb_w.io.in zip io.in.map(_.aw)).map { case (arb, in) => arb <> in }
  val thisReq_w = inputArb_w.io.out
  val inflightSrc_w = Reg(UInt(log2Ceil(n).W))

  io.out.aw.bits := thisReq_w.bits
  // bind correct valid and ready signals
  io.out.aw.valid := thisReq_w.valid && (w_state === s_idle)
  thisReq_w.ready := io.out.aw.ready && (w_state === s_idle)

  io.out.w.valid := io.in(inflightSrc_w).w.valid
  io.out.w.bits := io.in(inflightSrc_w).w.bits
  io.in.map(_.w.ready := false.B)
  io.in(inflightSrc_w).w.ready := io.out.w.ready

  io.in.map(_.b.bits := io.out.b.bits)
  io.in.map(_.b.valid := false.B)
  (io.in(inflightSrc_w).b, io.out.b) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  switch(w_state) {
    is(s_idle) {
      when(thisReq_w.fire()) {
        inflightSrc_w := inputArb_w.io.chosen
        when(thisReq_w.valid) { w_state := s_writeResp }
      }
    }
    is(s_writeResp) {
      when(io.out.b.fire()) { w_state := s_idle }
    }
  }

  // printf(p"[${GTimer()}]: XbarNto1 Debug Start-----------\n")
  // printf(
  //   p"r_state=${r_state},inflightSrc_r=${inflightSrc_r},w_state=${w_state},inflightSrc_w=${inflightSrc_w}\n"
  // )
  // for (i <- 0 until n) {
  //   printf(p"io.in(${i}): \n${io.in(i)}\n")
  // }
  // printf(p"io.out: \n${io.out}\n")
  // printf("--------------------------------\n")
}

class AXI4Xbar(n: Int, addressSpace: List[(Long, Long)]) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new AXI4Bundle))
    val out = Vec(addressSpace.length, new AXI4Bundle)
  })

  val inXbar = Module(new CrossbarNto1(n))
  val outXbar = Module(new Crossbar1toN(addressSpace))
  inXbar.io.in <> io.in
  outXbar.io.in <> inXbar.io.out
  io.out <> outXbar.io.out
}
