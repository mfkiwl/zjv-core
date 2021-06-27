package mem

import chisel3._
import chisel3.util._
import tile._
import tile.common.control.ControlConst._
import bus._
import device._
import utils._
import scala.annotation.switch

class TLIO (val dataWidth: Int = 64) extends Bundle with phvntomParams {
  val in = new MemIO(dataWidth)
  val out = new TLBundle
}

// serve as a simple convertor from MemIO to AXI4 interface
class TLUncache(val uncache: Boolean = true, val dataWidth: Int = 64, val mname: String = "Uncache")
    extends Module
    with AXI4Parameters 
    with TLParameters
    with TLOps {
  val io = IO(new TLIO(dataWidth))
  val blen = log2Ceil(xlen / 8)
  val burst_length = dataWidth / xlen

  // cache states
  val state = RegInit(0.U(3.W))
  
  val readBeatCnt  = RegInit(0.U(4.W))
  val writeBeatCnt = RegInit(0.U(4.W))
  
  val readBeatTot  = RegInit(0.U(4.W))
  val writeBeatTot = RegInit(0.U(4.W))
  val readLast     = Wire(Bool())
  val writeLast    = Wire(Bool())

  readLast  := readBeatCnt  === readBeatTot
  writeLast := writeBeatCnt === writeBeatTot

  val offset = writeBeatCnt << 6
  val byteid = io.in.req.bits.addr(blen - 1, 0)

  val isWrite = io.in.req.bits.wen

  val opcode = Wire(UInt(3.W))
  if (uncache) {
    opcode := Mux(isWrite, Mux(
      io.in.req.bits.memtype === memXXX |
      io.in.req.bits.memtype === memDouble,
      TL_PUT_FULL_DATA,
      TL_PUT_PARTIAL_DATA
    ), TL_GET)
  }
  else {
    opcode := Mux(isWrite, TL_PUT_FULL_DATA, TL_GET)
  }

  val size = Wire(UInt(TL_SZW.W))
  if (uncache) {
    size := MuxLookup(
      io.in.req.bits.memtype, 
      3.U,
      Seq(
        memByte -> 0.U,
        memByteU -> 0.U,
        memHalf -> 1.U,
        memHalfU -> 1.U,
        memWord -> 2.U,
        memWordU -> 2.U
      )
    )
  }
  else{
    size := 3.U
  }

  val addr_aligned = Wire(UInt(xlen.W))
  if (uncache) {
    addr_aligned := Cat(io.in.req.bits.addr(xlen - 1, 3), 0.U(3.W))
  }
  else {
    addr_aligned := io.in.req.bits.addr
  }
  
  val mask = Wire(UInt(TL_DBW.W))
  if (uncache) {
    switch(io.in.req.bits.memtype) {
      is(memXXX) { mask := Fill(xlen / 8, 1.U(1.W)) }
      is(memByte) { mask := Fill(1, 1.U(1.W)) << byteid }
      is(memHalf) { mask := Fill(2, 1.U(1.W)) << byteid }
      is(memWord) { mask := Fill(4, 1.U(1.W)) << byteid }
      is(memDouble) { mask := Fill(xlen / 8, 1.U(1.W)) }
      is(memByteU) { mask := Fill(1, 1.U(1.W)) << byteid }
      is(memHalfU) { mask := Fill(2, 1.U(1.W)) << byteid }
      is(memWordU) { mask := Fill(4, 1.U(1.W)) << byteid }
    }
  }
  else {
    mask := Fill(xlen / 8, 1.U(1.W))
  }

  val outData = Wire(UInt(dataWidth.W))
  if (uncache) {
    switch(io.in.req.bits.memtype) {
      is(memXXX) { outData := io.in.req.bits.data }
      is(memByte) { outData := Fill(8, io.in.req.bits.data(7, 0)) }
      is(memHalf) { outData := Fill(4, io.in.req.bits.data(15, 0)) }
      is(memWord) { outData := Fill(2, io.in.req.bits.data(31, 0)) }
      is(memDouble) { outData := io.in.req.bits.data }
      is(memByteU) { outData := Fill(8, io.in.req.bits.data(7, 0)) }
      is(memHalfU) { outData := Fill(4, io.in.req.bits.data(15, 0)) }
      is(memWordU) { outData := Fill(2, io.in.req.bits.data(31, 0)) }
    }
  }
  else {
    outData := (io.in.req.bits.data >> offset)(xlen - 1, 0)
  }

  val inDataRaw = io.out.d.data
  val realData = Wire(UInt(xlen.W))
  val inData = Wire(UInt(dataWidth.W))
  if (uncache)
  {
    switch(io.in.req.bits.memtype) {
      is(memXXX) { inData := inDataRaw }
      is(memByte) {
        mask := Fill(8, 1.U(1.W)) << offset
        realData := (inDataRaw & mask) >> offset
        inData := Cat(Fill(56, realData(7)), realData(7, 0))
      }
      is(memHalf) {
        mask := Fill(16, 1.U(1.W)) << offset
        realData := (inDataRaw & mask) >> offset
        inData := Cat(Fill(48, realData(15)), realData(15, 0))
      }
      is(memWord) {
        mask := Fill(32, 1.U(1.W)) << offset
        realData := (inDataRaw & mask) >> offset
        inData := Cat(Fill(32, realData(31)), realData(31, 0))
      }
      is(memDouble) { inData := inDataRaw }
      is(memByteU) {
        mask := Fill(8, 1.U(1.W)) << offset
        realData := (inDataRaw & mask) >> offset
        inData := Cat(Fill(56, 0.U), realData(7, 0))
      }
      is(memHalfU) {
        mask := Fill(16, 1.U(1.W)) << offset
        realData := (inDataRaw & mask) >> offset
        inData := Cat(Fill(48, 0.U), realData(15, 0))
      }
      is(memWordU) {
        mask := Fill(32, 1.U(1.W)) << offset
        realData := (inDataRaw & mask) >> offset
        inData := Cat(Fill(32, 0.U), realData(31, 0))
      }
    }
  } else {
    inData := inDataRaw
  }

  // Set default
  io.out.a.valid    <> false.B
  io.out.a.opcode   <> opcode
  io.out.a.param    <> 0.U
  io.out.a.size     <> size
  io.out.a.source   <> 0.U
  io.out.a.address  <> addr_aligned
  io.out.a.mask     <> mask
  io.out.a.data     <> outData

  io.out.b.ready    <> false.B
  io.out.c.valid    <> false.B
  io.out.d.ready    <> false.B

  val fireA = Wire(Bool())
  val fireD = Wire(Bool())
  fireA := false.B
  fireD := false.B

  when (state === 4.U)  // Begin
  {
    when(io.in.req.valid) {
      state := 0.U
      writeBeatCnt := 0.U
      readBeatCnt := 0.U
      writeBeatTot := 0.U
      readBeatTot := 0.U
    }
  }.elsewhen(state === 3.U) {  // End
    state := 4.U
    io.in.resp.valid := true.B
  }
  .otherwise
  {
    when (!state(0)) {  // Send not finished
      io.out.a.valid := true.B
      when (io.out.a.ready) {
        writeBeatCnt := writeBeatCnt + 1.U
        fireA := true.B
      }
    }
    when (!state(1)) {  // Receive not finished
      io.out.d.ready := true.B
      when (io.out.d.valid) {
        readBeatCnt := readBeatCnt + 1.U
        fireD := true.B
      }
    }
    state := state | Cat(readLast & fireA, writeLast & fireD)
  }

  if (uncache)
  {
    val data = Reg(UInt(xlen.W))
    when (fireD) {
      data := inData
    }

    io.in.resp.bits.data := data
  } else {
    val data_vec = Reg(Vec(burst_length, UInt(xlen.W)))
    when (fireD) {
      data_vec(readBeatCnt) := inData
    }
    
    io.in.resp.bits.data := data_vec.asUInt
  }
}
