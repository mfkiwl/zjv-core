package qarma64

import chisel3._
import chisel3.util._

class QarmaEngineOneStage(max_round: Int = 7) extends QarmaParamsIO {

  // Step 1 ---- Generate Key
  val mix_column = Module(new MixColumnOperator)
  mix_column.io.in := input.bits.keyl
  val w0 = Mux(input.bits.encrypt, input.bits.keyh, o_operation(input.bits.keyh))
  val k0 = Mux(input.bits.encrypt, input.bits.keyl, input.bits.keyl ^ alpha)
  val w1 = Mux(input.bits.encrypt, o_operation(input.bits.keyh), input.bits.keyh)
  val k1 = Mux(input.bits.encrypt, input.bits.keyl, mix_column.io.out)

  // Step 2 ---- Define Hardware
  val is_vec = Wire(Vec(max_round * 2 + 4, UInt(64.W)))
  val tk_vec = Wire(Vec(max_round * 2 + 4, UInt(64.W)))
  val forward_operator_vec = Array.fill(max_round + 1)(Module(new ForwardOperator).io)
  val forward_tweak_update_operator_vec = Array.fill(max_round)(Module(new ForwardTweakUpdateOperator).io)
  val reflector = Module(new PseudoReflectOperator)
  val backward_operator_vec = Array.fill(max_round + 1)(Module(new BackwardOperator).io)
  val backward_tweak_update_operator_vec = Array.fill(max_round)(Module(new BackwardTweakUpdateOperator).io)
  var wire_index = 0
  var module_index = 0

  // Step 3 ---- Forward
  is_vec(wire_index) := input.bits.text ^ w0
  log(1, is_vec(wire_index), tk_vec(wire_index))
  tk_vec(wire_index) := input.bits.tweak
  for (i <- 0 until max_round) {
    forward_operator_vec(module_index).is := is_vec(wire_index)
    forward_operator_vec(module_index).tk := tk_vec(wire_index) ^ k0 ^ c(i.asUInt)
    forward_operator_vec(module_index).round_zero := i.asUInt === 0.U
    forward_tweak_update_operator_vec(module_index).old_tk := tk_vec(wire_index)
    wire_index = wire_index + 1
    is_vec(wire_index) := Mux(i.asUInt < input.bits.actual_round,
      forward_operator_vec(module_index).out, is_vec(wire_index - 1))
    tk_vec(wire_index) := Mux(i.asUInt < input.bits.actual_round,
      forward_tweak_update_operator_vec(module_index).new_tk, tk_vec(wire_index - 1))
    module_index = module_index + 1
    log(2 + i, is_vec(wire_index), tk_vec(wire_index))
  }

  // Step 4 ---- Reflect
  forward_operator_vec(module_index).is := is_vec(wire_index)
  forward_operator_vec(module_index).tk := tk_vec(wire_index) ^ w1
  forward_operator_vec(module_index).round_zero := false.B
  wire_index = wire_index + 1
  is_vec(wire_index) := forward_operator_vec(module_index).out
  tk_vec(wire_index) := tk_vec(wire_index - 1)
  log(max_round + 2, is_vec(wire_index), tk_vec(wire_index))
  module_index = max_round
  reflector.io.is := is_vec(wire_index)
  reflector.io.key := k1
  wire_index = wire_index + 1
  is_vec(wire_index) := reflector.io.out
  tk_vec(wire_index) := tk_vec(wire_index - 1)
  log(max_round + 3, is_vec(wire_index), tk_vec(wire_index))
  backward_operator_vec(module_index).is := is_vec(wire_index)
  backward_operator_vec(module_index).tk := tk_vec(wire_index) ^ w0
  backward_operator_vec(module_index).round_zero := false.B
  wire_index = wire_index + 1
  is_vec(wire_index) := backward_operator_vec(module_index).out
  tk_vec(wire_index) := tk_vec(wire_index - 1)
  log(max_round + 4, is_vec(wire_index), tk_vec(wire_index))
  module_index = 0

  // Step 5 ---- Backward
  for (i <- 0 until max_round) {
    val j = max_round - 1 - i
    backward_tweak_update_operator_vec(module_index).old_tk := tk_vec(wire_index)
    backward_operator_vec(module_index).is := is_vec(wire_index)
    wire_index = wire_index + 1
    backward_operator_vec(module_index).tk := k0 ^ tk_vec(wire_index) ^ c(j.asUInt) ^ alpha.asUInt
    backward_operator_vec(module_index).round_zero := i.asUInt + 1.U === max_round.asUInt
    tk_vec(wire_index) := Mux(j.asUInt < input.bits.actual_round,
      backward_tweak_update_operator_vec(module_index).new_tk, tk_vec(wire_index - 1))
    is_vec(wire_index) := Mux(j.asUInt < input.bits.actual_round,
      backward_operator_vec(module_index).out, is_vec(wire_index - 1))
    module_index = module_index + 1
    log(max_round + 5 + i, is_vec(wire_index), tk_vec(wire_index))
  }

  output.bits.result := is_vec(wire_index) ^ w1
  output.valid := true.B
  input.ready := true.B
}
