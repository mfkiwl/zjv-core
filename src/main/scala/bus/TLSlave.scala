package bus

import chisel3._
import chisel3.util._
import tile._
import utils._

abstract class TLSlave[B <: Data](
    _extra: B = null,
    name: String = "AXI4Slave"
) extends Module
    with phvntomParams {

  val io = IO(new Bundle {
    val in = Flipped(new TLBundle)
    val extra = if (_extra != null) Some(Flipped(Flipped(_extra))) else None
  })
  val fullMask = MaskExpand(io.in.a.mask) // mask invalid data

  val fireA = io.in.a.valid & io.in.a.ready
  val fireD = io.in.d.valid & io.in.d.ready

  // read/read address channel
  val addr = HoldUnless(io.in.a.address, fireA)
  val ren = Wire(Bool())
  val wen = Wire(Bool())
  val valid = Wire(Bool())

  val len = HoldUnless(Mux(io.in.a.size <= 3.U, 0.U, (1.U << (io.in.a.size - 3.U)) - 1.U), fireA)
  val isGet = HoldUnless(io.in.a.opcode === 4.U, fireA)

  val readBeatCnt = Counter(256)
  val writeBeatCnt = Counter(256)
  val readBeatTot = Mux(isGet, len, 0.U)
  val writeBeatTot = Mux(isGet, 0.U, len)
  val readLast = readBeatTot === readBeatCnt.value
  val writeLast = writeBeatTot === writeBeatCnt.value

  val reading = BoolStopWatch(
    fireA,
    fireD & readLast,
    startHighPriority = true
  )
  val writing = BoolStopWatch(
    fireA,
    writeLast,
    startHighPriority = true
  )

  ren :=  isGet & (fireA | fireD & reading)
  wen := ~isGet &  fireA

  val opcode = HoldUnless(Mux(isGet, 1.U(3.W), 0.U(3.W)), fireA)
  val size = HoldUnless(io.in.a.size, fireA)
  val source = HoldUnless(io.in.a.source, fireA)

  val busy = reading | writing

  io.in.a.ready := true.B

  io.in.b.valid := false.B
  io.in.b.opcode := 0.U
  io.in.b.param := 0.U
  io.in.b.size := 0.U
  io.in.b.source := 0.U
  io.in.b.address := 0.U
  io.in.b.mask := 0.U
  io.in.b.data := 0.U
  io.in.b.corrupt := false.B

  io.in.c.ready := false.B

  io.in.d.valid := false.B
  io.in.d.opcode := opcode
  io.in.d.param := 0.U(3.W)
  io.in.d.size := size
  io.in.d.source := source
  io.in.d.sink := 0.U
  io.in.d.denied := false.B
  io.in.d.data := 0.U
  io.in.d.corrupt := false.B

  io.in.e.ready := false.B

  when (fireA) {
    writeBeatCnt.inc()
    when (writeLast) {
      writeBeatCnt.value := 0.U
    }
  }

  when (ren) {
    readBeatCnt.inc()
    when (readLast) {
      readBeatCnt.value := 0.U
    }
  }

  when (isGet) {
    // Read: send each packet
    io.in.d.valid := valid
  }.otherwise {
    // Write: reply when receiving last packet
    io.in.d.valid := writeLast
  }
}