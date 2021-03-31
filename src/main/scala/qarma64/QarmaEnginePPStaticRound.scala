package qarma64

import chisel3._
import chisel3.util._

class QarmaEnginePPStaticRound(fixed_round: Int = 7) extends QarmaParamsIO {

  // Step 1 ---- Generate Key
  val mix_column = Module(new MixColumnOperator)
  mix_column.io.in := input.bits.keyl
  val w0 = Mux(input.bits.encrypt, input.bits.keyh, o_operation(input.bits.keyh))
  val k0 = Mux(input.bits.encrypt, input.bits.keyl, input.bits.keyl ^ alpha)
  val w1 = Mux(input.bits.encrypt, o_operation(input.bits.keyh), input.bits.keyh)
  val k1 = Mux(input.bits.encrypt, input.bits.keyl, mix_column.io.out)

  // Step 2 ---- Define Hardware
  val is_vec = Wire(Vec(fixed_round * 2 + 4, UInt(64.W)))
  val tk_vec = Wire(Vec(fixed_round * 2 + 4, UInt(64.W)))
  val forward_operator_vec = Array.fill(fixed_round + 1)(Module(new ForwardOperator).io)
  val forward_tweak_update_operator_vec = Array.fill(fixed_round)(Module(new ForwardTweakUpdateOperator).io)
  val reflector = Module(new PseudoReflectOperator)
  val backward_operator_vec = Array.fill(fixed_round + 1)(Module(new BackwardOperator).io)
  val backward_tweak_update_operator_vec = Array.fill(fixed_round)(Module(new BackwardTweakUpdateOperator).io)
  var wire_index = 0
  var module_index = 0
  val temp_index = new Array[Int](3)
  val internal_regs = RegInit(VecInit(Seq.fill(4)(0.U((64 * 6).W))))
  val s_idle :: s_busy :: s_wait :: Nil = Enum(3)
  val state = RegInit(s_idle)
  val next_state = WireDefault(s_idle)
  val counter = RegInit(UInt(2.W), 0.U)

  // Step 3 ---- Forward Internal-Regs is/tk/w0/k0/w1/k1
  is_vec(wire_index) := internal_regs(0)(64 * 6 - 1, 64 * 5)
  log(1, is_vec(wire_index), tk_vec(wire_index))
  tk_vec(wire_index) := internal_regs(0)(64 * 5 - 1, 64 * 4)
  for (i <- 0 until fixed_round) {
    forward_operator_vec(module_index).is := is_vec(wire_index)
    forward_operator_vec(module_index).tk := tk_vec(wire_index) ^ internal_regs(0)(64 * 3 - 1, 64 * 2) ^ c(i.asUInt)
    forward_operator_vec(module_index).round_zero := i.asUInt === 0.U
    forward_tweak_update_operator_vec(module_index).old_tk := tk_vec(wire_index)
    wire_index = wire_index + 1
    is_vec(wire_index) := Mux(i.asUInt < fixed_round.U,
      forward_operator_vec(module_index).out, is_vec(wire_index - 1))
    tk_vec(wire_index) := Mux(i.asUInt < fixed_round.U,
      forward_tweak_update_operator_vec(module_index).new_tk, tk_vec(wire_index - 1))
    module_index = module_index + 1
    log(2 + i, is_vec(wire_index), tk_vec(wire_index))
  }

  // Step 4 ---- Reflect
  temp_index(0) = wire_index
  forward_operator_vec(module_index).is := internal_regs(1)(64 * 6 - 1, 64 * 5)
  forward_operator_vec(module_index).tk := internal_regs(1)(64 * 5 - 1, 64 * 4) ^ internal_regs(1)(64 * 2 - 1, 64 * 1)
  forward_operator_vec(module_index).round_zero := false.B
  wire_index = wire_index + 1
  is_vec(wire_index) := forward_operator_vec(module_index).out
  tk_vec(wire_index) := internal_regs(1)(64 * 5 - 1, 64 * 4)
  log(fixed_round + 2, is_vec(wire_index), tk_vec(wire_index))
  module_index = fixed_round
  reflector.io.is := is_vec(wire_index)
  reflector.io.key := internal_regs(1)(64 * 1 - 1, 64 * 0)
  wire_index = wire_index + 1
  is_vec(wire_index) := reflector.io.out
  tk_vec(wire_index) := tk_vec(wire_index - 1)
  log(fixed_round + 3, is_vec(wire_index), tk_vec(wire_index))
  backward_operator_vec(module_index).is := is_vec(wire_index)
  backward_operator_vec(module_index).tk := tk_vec(wire_index) ^ internal_regs(1)(64 * 4 - 1, 64 * 3)
  backward_operator_vec(module_index).round_zero := false.B
  wire_index = wire_index + 1
  is_vec(wire_index) := backward_operator_vec(module_index).out
  tk_vec(wire_index) := tk_vec(wire_index - 1)
  log(fixed_round + 4, is_vec(wire_index), tk_vec(wire_index))
  module_index = 0

  // Step 5 ---- Backward
  temp_index(1) = wire_index
  for (i <- 0 until fixed_round) {
    val j = fixed_round - 1 - i
    backward_tweak_update_operator_vec(module_index).old_tk := Mux(j.asUInt + 1.U === fixed_round.U,
      internal_regs(2)(64 * 5 - 1, 64 * 4), tk_vec(wire_index))
    backward_operator_vec(module_index).is := Mux(j.asUInt + 1.U === fixed_round.U,
      internal_regs(2)(64 * 6 - 1, 64 * 5), is_vec(wire_index))
    wire_index = wire_index + 1
    backward_operator_vec(module_index).tk := internal_regs(2)(64 * 3 - 1, 64 * 2) ^ tk_vec(wire_index) ^ c(j.asUInt) ^ alpha.asUInt
    backward_operator_vec(module_index).round_zero := i.asUInt + 1.U === fixed_round.asUInt
    tk_vec(wire_index) := Mux(j.asUInt < fixed_round.U,
      backward_tweak_update_operator_vec(module_index).new_tk, tk_vec(wire_index - 1))
    is_vec(wire_index) := Mux(j.asUInt < fixed_round.U,
      backward_operator_vec(module_index).out, is_vec(wire_index - 1))
    module_index = module_index + 1
    log(fixed_round + 5 + i, is_vec(wire_index), tk_vec(wire_index))
  }
  temp_index(2) = wire_index

  // Step 6 ---- Registers
  for (j <- 0 until 4) {
    val i = 3 - j
    if (i == 0) {
      internal_regs(0) := Cat(input.bits.text ^ w0, input.bits.tweak,
        w0, k0, w1, k1)
    } else if (i == 3) {
      when(state =/= s_wait) {
        internal_regs(3) := Cat(is_vec(temp_index(2)), tk_vec(temp_index(2)),
          internal_regs(2)(64 * 4 - 1, 0))
      }
    } else {
      internal_regs(i) := Cat(is_vec(temp_index(i - 1)), tk_vec(temp_index(i - 1)),
        internal_regs(i - 1)(64 * 4 - 1, 0))
    }
  }

  // Step 7 ---- FSM
  // comb0 | comb1 | comb2 | comb3
  //       | cnt0  | cnt1  | cnt2
  state := next_state
  when(state === s_idle) {
    next_state := Mux(input.valid, s_busy, s_idle)
    input.ready := Mux(input.valid, false.B, true.B)
    output.valid := false.B
  }.elsewhen(state === s_busy) {
    input.ready := false.B
    output.valid := false.B
    when(counter(1)) {
      counter := 0.U
      next_state := s_wait
    }.otherwise {
      counter := counter + 1.U
      next_state := s_busy
    }
  }.otherwise {
    next_state := Mux(output.ready, s_idle, s_wait)
    input.ready := Mux(output.ready, true.B, false.B)
    output.valid := true.B
  }

  if (ppldbg) {
    printf("%x\t%x\t%x\t%x\n", internal_regs(0)(64 * 6 - 1, 64 * 5),
      internal_regs(1)(64 * 6 - 1, 64 * 5), internal_regs(2)(64 * 6 - 1, 64 * 5), internal_regs(3)(64 * 6 - 1, 64 * 5))
  }

  output.bits.result := internal_regs(3)(64 * 6 - 1, 64 * 5) ^ internal_regs(3)(64 * 2 - 1, 64 * 1)
}
