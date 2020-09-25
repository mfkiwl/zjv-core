package rv64_3stage

import chisel3._
import chisel3.util._
import bus._
import ControlConst._

class CacheDummyIO extends Bundle with phvntomParams {
  val in = new MemIO
  val out = new AXI4Bundle
}

// serve as a simple convertor from MemIO to AXI4 interface
class CacheDummy extends Module with phvntomParams with AXI4Parameters {
  val io = IO(new CacheDummyIO)
  val blen = log2Ceil(xlen / 8)
  // cache states
  val (s_IDLE :: s_READ_CACHE :: s_WRITE_CACHE :: s_WRITE_BACK :: s_WRITE_ACK :: s_REFILL_READY :: s_REFILL :: Nil) =
    Enum(7)
  val state = RegInit(s_IDLE)

  when(io.in.req.bits.wen) {
    // write address
    io.out.aw.bits.id := 0.U
    io.out.aw.bits.addr := io.in.req.bits.addr(xlen - 1, blen) << blen.U
    io.out.aw.bits.len := 1.U
    io.out.aw.bits.size := "b011".U // 8 bytes
    io.out.aw.bits.burst := BURST_INCR
    io.out.aw.bits.lock := "b00".U // normal access
    io.out.aw.bits.cache := CACHE_RALLOCATE
    io.out.aw.bits.prot := PROT_INSECURE
    io.out.aw.bits.qos := 0.U
    // io.out.aw.bits := outWriteAddressChannel(
    //   0.U, Cat(rmeta.tag, idx_reg) << blen.U, log2Up(outXDataBits/8).U, (dataBeats-1).U)
    io.out.aw.valid := true.B

    // write data
    io.out.w.bits.data := io.in.req.bits.data
    val offset = io.in.req.bits.addr(blen - 1, 0) << 8
    switch(io.in.req.bits.memtype) {
      is(memXXX) { io.out.w.bits.strb := Fill(xlen / 8, 1.U(1.W)) }
      is(memByte) { io.out.w.bits.strb := Fill(1, 1.U(1.W)) << offset }
      is(memHalf) { io.out.w.bits.strb := Fill(2, 1.U(1.W)) << offset }
      is(memWord) { io.out.w.bits.strb := Fill(4, 1.U(1.W)) << offset }
      is(memDouble) { io.out.w.bits.strb := Fill(xlen / 8, 1.U(1.W)) }
      is(memByteU) { io.out.w.bits.strb := Fill(1, 1.U(1.W)) << offset }
      is(memHalfU) { io.out.w.bits.strb := Fill(2, 1.U(1.W)) << offset }
      is(memWordU) { io.out.w.bits.strb := Fill(4, 1.U(1.W)) << offset }
    }
    // io.out.w.bits.strb := io.in.req.bits.addr(blen - 1, 0)
    io.out.w.bits.last := true.B
    io.out.w.bits.user := 0.U
    // io.out.w.bits := outWriteDataChannel(
    //   Vec.tabulate(dataBeats)(i => read((i+1)*outXDataBits-1, i*outXDataBits))(write_count),
    //   None, write_wrap_out)
    io.out.w.valid := true.B

    // write response
    io.out.b.ready := true.B
    when(io.out.b.fire()) {
      io.in.resp.bits.data := io.out.r.bits.data
      io.in.resp.valid := true.B
    }

    // read address
    io.out.ar.valid := false.B

    // read data
    io.out.r.ready := false.B
  } otherwise {
    // read address
    io.out.ar.bits.id := 0.U
    io.out.ar.bits.addr := io.in.req.bits
      .addr(xlen - 1, blen) << blen.U // Cat(tag_reg, idx_reg) << blen.U
    io.out.ar.bits.len := 1.U
    io.out.ar.bits.size := "0b011".U // 8 bytes
    io.out.ar.bits.burst := BURST_INCR
    io.out.ar.bits.lock := "b00".U // normal access
    io.out.ar.bits.cache := CACHE_RALLOCATE
    io.out.ar.bits.prot := PROT_INSECURE
    io.out.ar.bits.qos := 0.U
    // log2Up(outXDataBits/8).U, (dataBeats-1).U
    io.out.ar.valid := false.B

    // read data
    io.out.r.ready := state === s_REFILL
    when(io.out.r.fire()) {
      io.in.resp.bits.data := io.out.r.bits.data
      io.in.resp.valid := true.B
    }

    // write address
    io.out.aw.valid := false.B

    // write data
    io.out.w.valid := false.B

    // write response
    io.out.b.ready := false.B
  }

  switch(state) {
    is(s_IDLE) {
      when(io.in.req.valid) {
        state := Mux(io.in.req.bits.wen, s_WRITE_CACHE, s_READ_CACHE)
      }
    }
    is(s_READ_CACHE) {
      io.out.ar.valid := true.B
      when(io.out.ar.fire()) {
        state := s_REFILL
      }
    }
    is(s_WRITE_CACHE) {
      io.out.aw.valid := true.B
      when(io.out.aw.fire()) {
        state := s_WRITE_BACK
      }.elsewhen(io.out.ar.fire()) {
        state := s_REFILL
      }
    }
    is(s_WRITE_BACK) {
      io.out.w.valid := true.B
      // when(write_wrap_out) {
      state := s_WRITE_ACK
      // }
    }
    is(s_WRITE_ACK) {
      io.out.b.ready := true.B
      // when(io.out.b.fire()) {
      state := s_REFILL_READY
      // }
    }
    is(s_REFILL_READY) {
      io.out.ar.valid := true.B
      when(io.out.ar.fire()) {
        state := s_REFILL
      }
    }
    is(s_REFILL) {
      // when(read_wrap_out) {
        // state := Mux(cpu_mask.orR, s_WRITE_CACHE, s_IDLE)
      // }
      state := s_IDLE
    }
  }
}
