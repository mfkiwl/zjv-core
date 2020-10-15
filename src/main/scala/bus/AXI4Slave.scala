package bus

import chisel3._
import chisel3.util._
import rv64_3stage._
import utils._

abstract class AXI4Slave[B <: Data](
    _extra: B = null,
    name: String = "AXI4Slave"
) extends Module
    with phvntomParams {
  val io = IO(new Bundle {
    val in = Flipped(new AXI4Bundle)
    val extra = if (_extra != null) Some(Flipped(Flipped(_extra))) else None
  })

  def MaskExpand(m: UInt) =
    Cat(m.asBools.map(Fill(8, _)).reverse) // expand each bit to 8 bits mask
  def HoldUnless[T <: Data](x: T, en: Bool): T =
    Mux(en, x, RegEnable(x, 0.U.asTypeOf(x), en))
  def BoolStopWatch(
      start: Bool,
      stop: Bool,
      startHighPriority: Boolean = false
  ) = {
    val r = RegInit(false.B)
    if (startHighPriority) {
      when(stop) { r := false.B }
      when(start) { r := true.B }
    } else {
      when(start) { r := true.B }
      when(stop) { r := false.B }
    }
    r
  }
  val fullMask = MaskExpand(io.in.w.bits.strb) // mask invalid data
  def genWdata(originData: UInt) =
    (originData & ~fullMask) | (io.in.w.bits.data & fullMask)

  // TODO 4K boundary check?

  // read/read address channel
  val raddr = Wire(UInt(xlen.W))
  val ren = Wire(Bool())
  val c_r = Counter(256)
  val beatCnt = Counter(256)
  val len = HoldUnless(io.in.ar.bits.len, io.in.ar.fire())
  val burst = HoldUnless(io.in.ar.bits.burst, io.in.ar.fire())
  val wrapAddr = io.in.ar.bits.addr & ~(io.in.ar.bits.len
    .asTypeOf(UInt(io.in.addrBits.W)) << io.in.ar.bits.size)
  raddr := HoldUnless(wrapAddr, io.in.ar.fire())
  io.in.r.bits.last := (c_r.value === len)
  when(ren) {
    beatCnt.inc()
    when(burst === io.in.BURST_WRAP && beatCnt.value === len) {
      beatCnt.value := 0.U // wrap the value
    }
  }
  when(io.in.r.fire()) {
    c_r.inc()
    when(io.in.r.bits.last) { c_r.value := 0.U }
  }
  when(io.in.ar.fire()) {
    beatCnt.value := (io.in.ar.bits.addr >> io.in.ar.bits.size) & io.in.ar.bits.len
    when(
      io.in.ar.bits.len =/= 0.U && io.in.ar.bits.burst === io.in.BURST_WRAP
    ) {
      assert(
        io.in.ar.bits.len === 1.U || io.in.ar.bits.len === 3.U ||
          io.in.ar.bits.len === 7.U || io.in.ar.bits.len === 15.U
      ) // restriction for wrapping burst
    }
  }
  val (readBeatCnt, rLast) = (beatCnt.value, io.in.r.bits.last)

  val r_busy = BoolStopWatch(
    io.in.ar.fire(),
    io.in.r.fire() && rLast, // stop when read the last one
    startHighPriority = true
  )
  io.in.ar.ready := io.in.r.ready || !r_busy
  io.in.r.bits.resp := io.in.RESP_OKAY
  ren := RegNext(io.in.ar.fire(), init = false.B) || (io.in.r.fire() && !rLast)
  io.in.r.valid := BoolStopWatch(
    ren && (io.in.ar.fire() || r_busy),
    io.in.r.fire(),
    startHighPriority = true
  )

  // write channel
  val waddr = Wire(UInt(xlen.W))
  val c_w = Counter(256)
  waddr := HoldUnless(io.in.aw.bits.addr, io.in.aw.fire())
  when(io.in.w.fire()) {
    c_w.inc()
    when(io.in.w.bits.last) { c_w.value := 0.U }
  }
  val (writeBeatCnt, wLast) = (c_w.value, io.in.w.bits.last)

  val w_busy =
    BoolStopWatch(io.in.aw.fire(), io.in.b.fire(), startHighPriority = true)
  io.in.aw.ready := !w_busy
  io.in.w.ready := io.in.aw.valid || (w_busy)
  io.in.b.bits.resp := io.in.RESP_OKAY
  io.in.b.valid := BoolStopWatch(
    io.in.w.fire() && wLast,
    io.in.b.fire(),
    startHighPriority = true
  )

  io.in.b.bits.id := RegEnable(io.in.aw.bits.id, io.in.aw.fire())
  io.in.b.bits.user := RegEnable(io.in.aw.bits.user, io.in.aw.fire())
  io.in.r.bits.id := RegEnable(io.in.ar.bits.id, io.in.ar.fire())
  io.in.r.bits.user := RegEnable(io.in.ar.bits.user, io.in.ar.fire())

//   printf(p"[${GTimer()}] ${name} Debug Start-----------\n")
//   printf(
//     p"ren=${ren}, wrapAddr=${Hexadecimal(wrapAddr)}, r_busy=${r_busy}, w_busy=${w_busy}\n"
//   )
//   printf(p"c_r=${c_r.value}, beatCnt=${beatCnt.value}, c_w=${c_w.value}\n")
//   printf(
//     p"readBeatCnt=${readBeatCnt}, writeBeatCnt=${writeBeatCnt}, rLast=${rLast}, wLast=${wLast}\n"
//   )
//   printf(p"io.in: \n${io.in}\n")
//   printf("-----------AXI4Slave Debug Done-----------\n")
}
