package qarma64

import chisel3._
import chisel3.util._

class QarmaCache(depth: Int = 8, policy: String = "Stack") extends Module {
  // fully associative register file
  val io = IO(new Bundle {
    val update = Input(Bool())
    // update bundle
    val cipher = Input(UInt(64.W))
    val plain  = Input(UInt(64.W))
    val tweak  = Input(UInt(64.W))
    val keyh   = Input(UInt(64.W))
    val keyl   = Input(UInt(64.W))
    // query bundle
    val encrypt = Input(Bool())
    val text    = Input(UInt(64.W))
    val hit     = Output(Bool())
    val result  = Output(UInt(64.W))
  })

  class CacheData extends Bundle {
    val valid  = Output(Bool())
    val cipher = Output(UInt(64.W))
    val plain  = Output(UInt(64.W))
    val tweak  = Output(UInt(64.W))
    val keyh   = Output(UInt(64.W))
    val keyl   = Output(UInt(64.W))
  }

  // --------------------------------------------- v - c -- p -- tk - key
  val cache = RegInit(VecInit(Seq.fill(depth)(0.U((1 + 64 + 64 + 64 + 128).W))))
  val wptr = RegInit(0.U(log2Ceil(depth).W))

  assert(depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16)

  // query
  io.hit := false.B
  io.result := Mux(io.encrypt, cache(0).asTypeOf(new CacheData).cipher, cache(0).asTypeOf(new CacheData).plain)
  for (i <- 0 until depth) {
    val data = cache(i).asTypeOf(new CacheData)
    when (data.valid && io.tweak === data.tweak && io.keyh === data.keyh && io.keyl === data.keyl) {
      when (io.encrypt && io.text === data.plain) {
        io.hit := true.B
        io.result := data.cipher
        wptr := wptr - 1.U
      }.elsewhen (!io.encrypt && io.text === data.cipher) {
        io.hit := true.B
        io.result := data.plain
        wptr := wptr - 1.U
      }
    }
  }

  // update
  when (io.update) {
    wptr := wptr + 1.U
    val new_data = WireInit(cache(0).asTypeOf(new CacheData))
    new_data.valid := true.B
    new_data.cipher := io.cipher
    new_data.plain := io.plain
    new_data.tweak := io.tweak
    new_data.keyh := io.keyh
    new_data.keyl := io.keyl
    cache(wptr) := new_data.asUInt
  }
}

class QarmaEngine(ppl: Boolean, static_ppl: Boolean, max_round: Int = 7) extends QarmaParamsIO {

  val engine = Module(new QarmaEnginePPStaticRound(fixed_round = max_round))
  val cache  = Module(new QarmaCache(8, "Stack"))

  cache.io.update := engine.output.valid
  cache.io.cipher := Mux(input.bits.encrypt, output.bits.result, input.bits.text)
  cache.io.plain  := Mux(input.bits.encrypt, input.bits.text, output.bits.result)
  cache.io.tweak  := input.bits.tweak
  cache.io.keyh   := input.bits.keyh
  cache.io.keyl   := input.bits.keyl
  cache.io.text   := input.bits.text
  cache.io.encrypt := input.bits.encrypt

  engine.kill.valid := RegNext(cache.io.hit)
  engine.input.valid := input.valid
  input.ready := engine.input.ready
  engine.input.bits := input.bits
  engine.output.ready := output.ready
  output.valid := engine.output.valid || cache.io.hit
  output.bits.result := Mux(cache.io.hit, cache.io.result, engine.output.bits.result)
}
