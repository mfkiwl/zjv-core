package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import device._
import utils._

case class WBConfig(
    wb_name: String = "write buffer", // used for debug info
    dataWidth: Int = 64,
    entries: Int = 4
)

trait WBParameters extends phvntomParams {
  implicit val wbConfig: WBConfig

  val wb_name = wbConfig.wb_name
  val dataWidth = wbConfig.dataWidth
  val nEntry = wbConfig.entries
  val entryBits = log2Ceil(nEntry)
}

class WBEntry(implicit val wbConfig: WBConfig)
    extends Bundle
    with WBParameters {
  val valid = Bool()
  val need_write = Bool()
  val meta = UInt(entryBits.W) // TODO use meta
  val addr = UInt(xlen.W)
  val data = UInt(dataWidth.W)
  override def toPrintable: Printable =
    p"WBEntry(valid=${valid}, need_write = ${need_write}, addr = 0x${Hexadecimal(addr)}, data = 0x${Hexadecimal(data)}"
}

class WriteBuffer(implicit val wbConfig: WBConfig)
    extends Module
    with WBParameters {
  val io = IO(new Bundle {
    val in = new MemIO(dataWidth)
    val readChannel = Flipped(new MemIO(dataWidth))
    val writeChannel = Flipped(new MemIO(dataWidth))
  })

  val entryArray =
    RegInit(Vec(nEntry, new WBEntry), 0.U.asTypeOf(Vec(nEntry, new WBEntry)))

  val r_idle :: r_readReq :: r_readResp :: r_write :: r_flush :: Nil = Enum(5)
  val req_state = RegInit(r_idle)
  val w_idle :: w_req :: w_resp :: Nil = Enum(3)
  val write_state = RegInit(w_idle)

  // signals for request handling
  val hit_vec = VecInit(
    entryArray.map(m => m.valid && m.addr === io.in.req.bits.addr)
  ).asUInt
  val hit_index = PriorityEncoder(hit_vec)
  val hit = hit_vec.orR

  val available_vec = VecInit(entryArray.map(m => !m.need_write)).asUInt
  val available_index = PriorityEncoder(available_vec)
  val available = available_vec.orR

  // signals for writing back
  val need_write_vec = VecInit(
    entryArray.map(m => m.valid && m.need_write)
  ).asUInt
  val write_index = PriorityEncoder(need_write_vec)
  val need_write = need_write_vec.orR
  val need_write_next = PopCount(need_write_vec) > 1.U

  io.in.req.ready := req_state === r_idle
  io.in.resp.valid := (req_state === r_readReq && hit) || (req_state === r_readResp && io.readChannel.resp
    .fire()) || (req_state === r_write && ((hit && !entryArray(hit_index).need_write) || (!hit && available)))
  io.in.resp.bits.data := Mux(
    hit,
    entryArray(hit_index).data,
    io.readChannel.resp.bits.data
  )
  io.in.flush_ready := (req_state =/= r_idle && req_state =/= r_flush) || (req_state === r_idle && !io.in.flush) || (req_state === r_flush && write_state === w_idle)

  io.readChannel.stall := false.B
  io.readChannel.flush := false.B
  io.readChannel.req.valid := req_state === r_readReq
  io.readChannel.req.bits.addr := io.in.req.bits.addr
  io.readChannel.req.bits.data := DontCare
  io.readChannel.req.bits.memtype := DontCare
  io.readChannel.req.bits.wen := false.B
  io.readChannel.resp.ready := req_state === r_readResp

  // request state machine
  switch(req_state) {
    is(r_idle) {
      when(io.in.flush) {
        req_state := r_flush
      }.elsewhen(io.in.req.fire()) {
        when(io.in.req.bits.wen) {
          req_state := r_write
        }.otherwise {
          req_state := r_readReq
        }
      }
    }
    is(r_readReq) {
      when(hit) {
        req_state := r_idle
      }.otherwise {
        when(io.readChannel.req.fire()) {
          req_state := r_readResp
        }
      }
    }
    is(r_readResp) {
      when(io.readChannel.resp.fire()) {
        req_state := r_idle
      }
    }
    is(r_write) {
      when(hit) {
        when(!entryArray(hit_index).need_write) {
          entryArray(hit_index).valid := true.B
          entryArray(hit_index).need_write := true.B
          entryArray(hit_index).addr := io.in.req.bits.addr
          entryArray(hit_index).data := io.in.req.bits.data
          req_state := r_idle
        }
      }.elsewhen(available) {
        entryArray(available_index).valid := true.B
        entryArray(available_index).need_write := true.B
        entryArray(available_index).addr := io.in.req.bits.addr
        entryArray(available_index).data := io.in.req.bits.data
        req_state := r_idle
      }
    }
    is(r_flush) { // TODO need flush signal
      when(write_state === w_idle) {
        req_state := r_idle
      }
    }
  }

  io.writeChannel.stall := false.B
  io.writeChannel.flush := false.B
  io.writeChannel.req.valid := write_state === w_req
  io.writeChannel.req.bits.addr := entryArray(write_index).addr
  io.writeChannel.req.bits.data := entryArray(write_index).data
  io.writeChannel.req.bits.memtype := DontCare
  io.writeChannel.req.bits.wen := write_state === w_req
  io.writeChannel.resp.ready := write_state === w_resp

  // write state machine
  switch(write_state) {
    is(w_idle) {
      when(need_write) {
        write_state := w_req
      }
    }
    is(w_req) {
      when(io.writeChannel.req.fire()) {
        write_state := w_resp
      }
    }
    is(w_resp) {
      when(io.writeChannel.resp.fire()) {
        entryArray(write_index).need_write := false.B
        write_state := Mux(need_write_next, w_req, w_idle)
      }
    }
  }

  // printf(p"[${GTimer()}] ${wb_name} Debug Info----------\n")
  // printf(p"req_state=${req_state}, write_state=${write_state}\n")
  // printf(p"hit_vec=${hit_vec}, hit_index=${hit_index}, hit=${hit}\n")
  // printf(
  //   p"available_vec=${available_vec}, available_index=${available_index}, available=${available}\n"
  // )
  // printf(
  //   p"need_write_vec=${need_write_vec}, write_index=${write_index}, need_write=${need_write}, need_write_next=${need_write_next}\n"
  // )
  // printf(p"entryArray=${entryArray}\n")
  // printf(p"----------io.in----------\n")
  // printf(p"${io.in}\n")
  // printf(p"----------io.readChannel----------\n")
  // printf(p"${io.readChannel}\n")
  // printf(p"----------io.writeChannel----------\n")
  // printf(p"${io.writeChannel}\n")
  // printf("-----------------------------------------------\n")
}
