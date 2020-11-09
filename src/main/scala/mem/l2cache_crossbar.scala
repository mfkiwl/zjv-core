package mem

import chisel3._
import chisel3.util._
import rv64_nstage.core._
import device._

class L2CacheXbar(val n_sources: Int = 1)(implicit val cacheConfig: CacheConfig)
    extends Module
    with CacheParameters {
  val io = IO(new Bundle {
    val in = Vec(n_sources, new MemIO(blockBits))
    val out = Flipped(new MemIO(blockBits))
  })

  val s_idle :: s_readResp :: s_writeResp :: Nil = Enum(3)
  val state = RegInit(s_idle)

  val inputArb = Module(new Arbiter(new MemReq(blockBits), n_sources))
  (inputArb.io.in zip io.in.map(_.req)).map { case (arb, in) => arb <> in }
  val thisReq = inputArb.io.out
  val inflightSrc = Reg(UInt(log2Up(n_sources).W))

  io.out.req.bits := thisReq.bits
  // bind correct valid and ready signals
  io.out.stall := false.B
  io.out.flush := false.B
  io.out.req.valid := thisReq.valid && (state === s_idle)
  thisReq.ready := io.out.req.ready && (state === s_idle)

  io.in.map(_.resp.bits := DontCare)
  io.in.map(_.resp.valid := false.B)
  io.in.map(_.flush_ready := true.B)
  (io.in(inflightSrc).resp, io.out.resp) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
      l.bits := r.bits
    }
  }

  switch(state) {
    is(s_idle) {
      when(thisReq.fire()) {
        inflightSrc := inputArb.io.chosen
        when(thisReq.valid) {
          when(thisReq.bits.wen) { state := s_writeResp }.otherwise {
            state := s_readResp
          }
        }
      }
    }
    is(s_readResp) { when(io.out.resp.fire()) { state := s_idle } }
    is(s_writeResp) { when(io.out.resp.fire()) { state := s_idle } }
  }

  // when(GTimer() > 480000000.U) {
  //   printf(p"[${GTimer()}]: L2CacheXbar Debug Start-----------\n")
  //   printf(p"state=${state},inflightSrc=${inflightSrc}\n")
  //   for (i <- 0 until n_sources) {
  //     printf(p"----------l2cache io.in(${i})----------\n")
  //     printf(p"${io.in(i)}\n")
  //   }
  //   printf(p"----------l2cache io.out----------\n")
  //   printf(p"${io.out}\n")
  //   printf("--------------------------------\n")
  // }
}