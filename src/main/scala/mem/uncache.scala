package mem

import chisel3._
import chisel3.util._
import tile._
import tile.common.control.ControlConst._
import bus._
import device._
import utils._
import scala.annotation.switch

class UncacheIO(val dataWidth: Int = 64) extends Bundle with phvntomParams {
  val in = new MemIO(dataWidth)
  val out = new AXI4Bundle
}

// serve as a simple convertor from MemIO to AXI4 interface
class Uncache(val dataWidth: Int = 64, val mname: String = "Uncache")
    extends Module
    with AXI4Parameters {
  val io = IO(new UncacheIO(dataWidth))
  val blen = log2Ceil(xlen / 8)
  // cache states
  val (s_IDLE :: s_WAIT_AXI_READY :: s_RECEIVING :: s_WB_WAIT_AWREADY :: s_WB_WRITE :: s_WB_WAIT_BVALID :: s_REFILL :: s_FINISH :: Nil) =
    Enum(8)
  val state = RegInit(s_IDLE)

  // disable all channels
  io.out.aw.valid := false.B
  io.out.aw.bits := DontCare
  io.out.w.valid := false.B
  io.out.w.bits := DontCare
  io.out.b.ready := false.B
  io.out.ar.valid := false.B
  io.out.ar.bits := DontCare
  io.out.r.ready := false.B
  io.in.resp.valid := false.B // stall
  io.in.flush_ready := true.B
  io.in.req.ready := (state === s_WAIT_AXI_READY && io.out.ar.ready && !io.in.req.bits.wen) || (state === s_WB_WAIT_AWREADY && io.out.aw.ready && io.in.req.bits.wen)

  io.out.aw.bits.id := 0.U
  io.out.ar.bits.id := 0.U

  val addr_aligned = Wire(UInt(xlen.W))
  addr_aligned := io.in.req.bits.addr
  switch(io.in.req.bits.memtype) {
    is(memXXX) { addr_aligned := io.in.req.bits.addr }
    is(memByte) { addr_aligned := io.in.req.bits.addr }
    is(memHalf) {
      addr_aligned := Cat(io.in.req.bits.addr(xlen - 1, 1), 0.U(1.W))
    }
    is(memWord) {
      addr_aligned := Cat(io.in.req.bits.addr(xlen - 1, 2), 0.U(2.W))
    }
    is(memDouble) {
      addr_aligned := Cat(io.in.req.bits.addr(xlen - 1, 3), 0.U(3.W))
    }
    is(memByteU) { addr_aligned := io.in.req.bits.addr }
    is(memHalfU) {
      addr_aligned := Cat(io.in.req.bits.addr(xlen - 1, 1), 0.U(1.W))
    }
    is(memWordU) {
      addr_aligned := Cat(io.in.req.bits.addr(xlen - 1, 2), 0.U(2.W))
    }
  }

  when(state === s_IDLE) {
    when(io.in.req.valid) {
      state := Mux(io.in.req.bits.wen, s_WB_WAIT_AWREADY, s_WAIT_AXI_READY)
    }
  }.elsewhen(
    state === s_WB_WAIT_AWREADY || state === s_WB_WRITE || state === s_WB_WAIT_BVALID
  ) {
    io.out.aw.valid := false.B
    io.out.w.valid := false.B
    io.out.b.ready := true.B
    io.out.aw.bits.addr := addr_aligned
    io.out.aw.bits.len := 0.U // 1 word
    io.out.aw.bits.size := MuxLookup(
      io.in.req.bits.memtype, "b011".U,
      Seq(
        memByte -> "b000".U,
        memByteU -> "b000".U,
        memHalf -> "b001".U,
        memHalfU -> "b001".U,
        memWord -> "b010".U,
        memWordU -> "b010".U
      )
    )
    //"b011".U // 8 bytes
    io.out.aw.bits.burst := BURST_FIXED
    io.out.aw.bits.lock := 0.U
    io.out.aw.bits.cache := 0.U
    io.out.aw.bits.prot := 0.U
    io.out.aw.bits.qos := 0.U

    switch(io.in.req.bits.memtype) {
      is(memXXX) { io.out.w.bits.data := io.in.req.bits.data }
      is(memByte) { io.out.w.bits.data := Fill(8, io.in.req.bits.data(7, 0)) }
      is(memHalf) {
        io.out.w.bits.data := Fill(4, io.in.req.bits.data(15, 0))
      }
      is(memWord) {
        io.out.w.bits.data := Fill(2, io.in.req.bits.data(31, 0))
      }
      is(memDouble) { io.out.w.bits.data := io.in.req.bits.data }
      is(memByteU) {
        io.out.w.bits.data := Fill(8, io.in.req.bits.data(7, 0))
      }
      is(memHalfU) {
        io.out.w.bits.data := Fill(4, io.in.req.bits.data(15, 0))
      }
      is(memWordU) {
        io.out.w.bits.data := Fill(2, io.in.req.bits.data(31, 0))
      }
    }
    val offset = io.in.req.bits.addr(blen - 1, 0)
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
    io.out.w.bits.last := true.B

    when(state === s_WB_WAIT_AWREADY) {
      io.out.aw.valid := true.B
      // io.out.w.valid := true.B
      when(io.out.aw.ready) {
        state := s_WB_WRITE
      }
    }.elsewhen(state === s_WB_WRITE) {
      io.out.w.valid := true.B
      when(io.out.w.ready && io.out.w.bits.last) {
        state := s_WB_WAIT_BVALID
      }
    }.elsewhen(state === s_WB_WAIT_BVALID) {
      io.out.aw.valid := false.B
      io.out.w.valid := false.B
      when(io.out.b.valid) {
        state := s_IDLE
        io.in.resp.valid := true.B
      }
    }
  }.otherwise {
    io.out.ar.bits.addr := addr_aligned
    io.out.ar.bits.len := 0.U // one word
    io.out.ar.bits.size := MuxLookup(
      io.in.req.bits.memtype, "b011".U,
      Seq(
        memByte -> "b000".U,
        memByteU -> "b000".U,
        memHalf -> "b001".U,
        memHalfU -> "b001".U,
        memWord -> "b010".U,
        memWordU -> "b010".U
      )
    )
    //"b011".U // 8 bytes
    io.out.ar.bits.burst := BURST_FIXED
    io.out.ar.valid := false.B
    when(state === s_WAIT_AXI_READY) {
      io.out.ar.valid := true.B
      when(io.out.ar.ready) {
        state := s_RECEIVING
      }
    }.elsewhen(state === s_RECEIVING) {
      io.out.r.ready := true.B
      when(io.out.r.valid) {
        // io.in.resp.valid := true.B
        // when(io.out.r.bits.last) {
        state := s_FINISH
        // }
      }
    }.elsewhen(state === s_REFILL) {
      state := s_FINISH
    }.elsewhen(state === s_FINISH) {
      when(io.in.req.bits.wen) {
        state := s_WB_WAIT_AWREADY
      }.otherwise {
        state := s_IDLE
        io.in.resp.valid := true.B
      }
    }
  }

  val offset = io.in.req.bits.addr(blen - 1, 0) << 3
  // io.offset := io.in.req.bits.addr(blen - 1, 0)
  val mask = Wire(UInt(xlen.W))
  val realdata = Wire(UInt(xlen.W))
  mask := 0.U
  realdata := 0.U
  // io.in.resp.bits.data := 0.U
  val resp_data = WireInit(UInt(xlen.W), 0.U)
  when(state === s_RECEIVING && io.out.r.valid) {
    // io.in.resp.valid := true.B
    switch(io.in.req.bits.memtype) {
      is(memXXX) { resp_data := io.out.r.bits.data }
      is(memByte) {
        mask := Fill(8, 1.U(1.W)) << offset
        realdata := (io.out.r.bits.data & mask) >> offset
        resp_data := Cat(Fill(56, realdata(7)), realdata(7, 0))
      }
      is(memHalf) {
        mask := Fill(16, 1.U(1.W)) << offset
        realdata := (io.out.r.bits.data & mask) >> offset
        resp_data := Cat(Fill(48, realdata(15)), realdata(15, 0))
      }
      is(memWord) {
        mask := Fill(32, 1.U(1.W)) << offset
        realdata := (io.out.r.bits.data & mask) >> offset
        resp_data := Cat(Fill(32, realdata(31)), realdata(31, 0))
      }
      is(memDouble) { resp_data := io.out.r.bits.data }
      is(memByteU) {
        mask := Fill(8, 1.U(1.W)) << offset
        realdata := (io.out.r.bits.data & mask) >> offset
        resp_data := Cat(Fill(56, 0.U), realdata(7, 0))
      }
      is(memHalfU) {
        mask := Fill(16, 1.U(1.W)) << offset
        realdata := (io.out.r.bits.data & mask) >> offset
        resp_data := Cat(Fill(48, 0.U), realdata(15, 0))
      }
      is(memWordU) {
        mask := Fill(32, 1.U(1.W)) << offset
        realdata := (io.out.r.bits.data & mask) >> offset
        resp_data := Cat(Fill(32, 0.U), realdata(31, 0))
      }
    }
  }
  io.in.resp.bits.data := RegNext(resp_data)

  // printf(p"[${GTimer()}]: ${mname} Debug Start-----------\n")
  // printf("state = %d\n", state);
  // printf("offset = %x, mask = %x, realdata = %x\n", offset, mask, realdata)
  // printf(p"io.in: \n${io.in}\n")
  // printf(p"io.out: \n${io.out}\n")
  // printf("--------------------------------\n")
}
