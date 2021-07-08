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
  val state = RegInit(3.U(3.W))
  
  val readBeatCnt  = Counter(256)
  val writeBeatCnt = Counter(256)
  
  val isWrite = io.in.req.bits.wen
  val beatTot      = Wire(UInt(4.W))
  val readBeatTot  = Mux(isWrite, 0.U, beatTot)
  val writeBeatTot = Mux(isWrite, beatTot, 0.U)
  val readLast     = Wire(Bool())
  val writeLast    = Wire(Bool())

  readLast  := readBeatCnt.value  === readBeatTot
  writeLast := writeBeatCnt.value === writeBeatTot

  val offset = writeBeatCnt.value << 6.U
  val byteid = io.in.req.bits.addr(blen - 1, 0)

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
    beatTot := 0.U
  }
  else{
    size := log2Ceil(dataWidth / 8).U
    beatTot := (dataWidth / xlen - 1).U
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
  val inData = Wire(UInt(dataWidth.W))
  if (uncache)
  {
    val realData = Wire(UInt(xlen.W))
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
  io.out.a.corrupt  <> false.B

  io.out.b.ready    <> false.B

  io.out.c.valid    <> false.B
  io.out.c.opcode   <> 0.U
  io.out.c.param    <> 0.U
  io.out.c.size     <> 0.U
  io.out.c.source   <> 0.U
  io.out.c.address  <> 0.U
  io.out.c.data     <> 0.U
  io.out.c.corrupt  <> false.B
  
  io.out.d.ready    <> false.B

  io.out.e.valid    <> false.B
  io.out.e.sink     <> false.B

  val fireA = Wire(Bool())
  val fireD = Wire(Bool())
  fireA := false.B
  fireD := false.B

  io.in.resp.valid := false.B

  when (state === 3.U) {
    state := Mux(io.in.req.valid, 0.U, 3.U)
    io.in.resp.valid := true.B
  }
  .otherwise
  {
    when (!state(1)) {  // Send not finished
      io.out.a.valid := true.B
      when (io.out.a.ready) {
        writeBeatCnt.inc()
        when(writeLast) {
          writeBeatCnt.value := 0.U
        }
        fireA := true.B
      }
    }
    when (!state(0)) {  // Receive not finished
      io.out.d.ready := true.B
      when (io.out.d.valid) {
        readBeatCnt.inc()
        when(readLast) {
          readBeatCnt.value := 0.U
        }
        fireD := true.B
      }
    }
    state := state | Cat(writeLast & fireA, readLast & fireD)
  }

  val data_vec = Reg(Vec(burst_length, UInt(xlen.W)))
  if (uncache)
  {
    val data = Reg(UInt(xlen.W))
    when (fireD) {
      data := inData
    }

    io.in.resp.bits.data := data
  } else {
    when (fireD) {
      data_vec(readBeatCnt.value) := inData
    }
    
    io.in.resp.bits.data := data_vec.asUInt
  }

  io.in.flush_ready := true.B
  io.in.req.ready := io.out.a.ready


  printf(p"[${GTimer()}]: TLUncache Debug Start-----------\n")
  printf("state = %d\n", state);
  printf(
    p"write: ${writeBeatCnt.value}/${writeBeatTot}\nread: ${readBeatCnt.value}/${readBeatTot}\n"
  )
  printf("in_data_raw = %x\n", inDataRaw)
  printf("data_vec = (")
  for (i <- 0 until 8) {
    printf("%x, ", data_vec(i))
  }
  printf(")\n")
  // printf(p"io.in: \n${io.in}\n")
  // printf(p"io.out: \n${io.out}\n")
  // printf("size: %d\n", io.out.a.size)
  printf("-----------TLUncache Debug Done-----------\n")
}
