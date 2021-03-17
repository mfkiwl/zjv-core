package qarma64

import chisel3._
import chisel3.util._

class MixColumnOperatorIO extends Bundle {
  val in = Input(UInt(64.W))
  val out = Output(UInt(64.W))
}

class OperatorIO extends Bundle {
  val is = Input(UInt(64.W))
  val tk = Input(UInt(64.W))
  val round_zero = Input(Bool())
  val out = Output(UInt(64.W))
}

class TweakIO extends Bundle {
  val old_tk = Input(UInt(64.W))
  val new_tk = Output(UInt(64.W))
}

class DataBundle(max_round: Int, step_len: Int) extends Bundle with QarmaParams {
  val code = UInt((code_map_width * ((max_round + step_len - 1) / step_len * 2 + 2)).W)
  val step_end = UInt((log2Ceil(step_len) * ((max_round + step_len - 1) / step_len * 2 + 2)).W)
  val is = UInt(64.W)
  val tk = UInt(64.W)
  val k0 = UInt(64.W)
  val k1 = UInt(64.W)
  val w0 = UInt(64.W)
  val w1 = UInt(64.W)
}

class MetaBundle(max_round: Int) extends Bundle with QarmaParams {
  val valid = Bool()
  val done = Bool()
  val pointer = UInt(log2Ceil(code_map_width * (max_round * 2 + 2)).W)
}

class MixColumnOperator extends Module with QarmaParams {
  val io = IO(new MixColumnOperatorIO)

  val result_vec = Wire(Vec(16, UInt(4.W)))
  val temp_vec = Wire(Vec(16, Vec(4, UInt(4.W))))
  for (x <- 0 until 4; y <- 0 until 4) {
    for (j <- 0 until 4) {
      val a = io.in.asTypeOf(Vec(16, UInt(4.W)))(15 - (4 * j + y)).asUInt
      val b = M(4 * x + j)
      when(b.asUInt =/= 0.U) {
        temp_vec(15 - (4 * x + y))(j) := Cat(a(3 - b, 0), a(3, 3 - b)) >> 1.U
      }.otherwise {
        temp_vec(15 - (4 * x + y))(j) := 0.U
      }
    }
    result_vec(15 - (4 * x + y)) := temp_vec(15 - (4 * x + y)).reduce((a, b) => (a ^ b).asUInt)
  }
  io.out := result_vec.asTypeOf(UInt(64.W))
}

class ForwardOperator extends Module with QarmaParams {
  val io = IO(new OperatorIO)

  val new_is = io.is ^ io.tk
  val mix_column_is = Wire(UInt(64.W))
  val perm_vec = Wire(Vec(16, UInt(4.W)))
  val result_vec = Wire(Vec(16, UInt(4.W)))
  val sbox_prev_is = Mux(io.round_zero, new_is, mix_column_is)
  val mix_column_operator = Module(new MixColumnOperator)

  for (i <- 0 until 16) {
    perm_vec(15 - i) := new_is.asTypeOf(Vec(16, UInt(4.W)))(15 - t(i))
  }

  mix_column_operator.io.in := perm_vec.asTypeOf(UInt(64.W))
  mix_column_is := mix_column_operator.io.out

  for (i <- 0 until 16) {
    result_vec(15 - i) := sbox(sbox_number)(sbox_prev_is.asTypeOf(Vec(16, UInt(4.W)))(15 - i))
  }

  io.out := result_vec.asTypeOf(UInt(64.W))
}

class BackwardOperator extends Module with QarmaParams {
  val io = IO(new OperatorIO)

  val inv_sbox_is = Wire(Vec(16, UInt(4.W)))
  val mix_column_is = Wire(UInt(64.W))
  val result_vec = Wire(Vec(16, UInt(4.W)))
  val perm_vec = Wire(Vec(16, UInt(4.W)))
  val mix_column_operator = Module(new MixColumnOperator)

  for (i <- 0 until 16) {
    inv_sbox_is(15 - i) := sbox_inv(sbox_number)(io.is.asTypeOf(Vec(16, UInt(4.W)))(15 - i))
  }

  mix_column_operator.io.in := inv_sbox_is.asTypeOf(UInt(64.W))
  mix_column_is := mix_column_operator.io.out

  for (i <- 0 until 16) {
    perm_vec(15 - i) := mix_column_is.asTypeOf(Vec(16, UInt(4.W)))(15 - t_inv(i))
  }

  result_vec := Mux(io.round_zero, inv_sbox_is, perm_vec)
  io.out := result_vec.asTypeOf(UInt(64.W)) ^ io.tk
}

class ForwardTweakUpdateOperator extends Module with QarmaParams {
  val io = IO(new TweakIO)

  val result_vec = Wire(Vec(16, UInt(4.W)))
  val temp_vec = Wire(Vec(16, UInt(4.W)))
  for (i <- 0 until 16) {
    temp_vec(15 - i) := io.old_tk.asTypeOf(Vec(16, UInt(4.W)))(15 - h(i))
  }

  for (i <- 0 until 16) {
    if (Set(0, 1, 3, 4, 8, 11, 13).contains(i)) {
      result_vec(15 - i) := lfsr_operation(temp_vec(15 - i))
    } else {
      result_vec(15 - i) := temp_vec(15 - i)
    }
  }

  io.new_tk := result_vec.asTypeOf(UInt(64.W))
}

class BackwardTweakUpdateOperator extends Module with QarmaParams {
  val io = IO(new TweakIO)

  val mtk = Wire(Vec(16, UInt(4.W)))
  val result_vec = Wire(Vec(16, UInt(4.W)))
  for (i <- 0 until 16) {
    val tk_base_index = (15 - i) * 4
    val step_result = if (List(0, 1, 3, 4, 8, 11, 13).indexOf(i) != -1)
      lfsr_inv_operation(io.old_tk(tk_base_index + 3, tk_base_index)) else io.old_tk(tk_base_index + 3, tk_base_index)
    mtk(15 - i) := step_result
  }
  for (i <- 0 until 16) {
    result_vec(15 - i) := mtk(15 - h_inv(i))
  }
  io.new_tk := result_vec.asTypeOf(UInt(64.W))
}

class PseudoReflectOperator extends Module with QarmaParams {
  val io = IO(new Bundle {
    val is = Input(UInt(64.W))
    val key = Input(UInt(64.W))
    val out = Output(UInt(64.W))
  })

  val perm_vec = Wire(Vec(16, UInt(4.W)))
  val mix_column_is = Wire(UInt(64.W))
  val mix_column_operator = Module(new MixColumnOperator)
  val result_vec = Wire(Vec(16, UInt(4.W)))
  val tweakey_is = Wire(Vec(16, UInt(4.W)))

  for (i <- 0 until 16) {
    perm_vec(15 - i) := io.is.asTypeOf(Vec(16, UInt(4.W)))(15 - t(i))
  }

  mix_column_operator.io.in := perm_vec.asTypeOf(UInt(64.W))
  mix_column_is := mix_column_operator.io.out

  for (i <- 0 until 16) {
    val key_base = 4 * (15 - i)
    tweakey_is(15 - i) := (mix_column_is.asTypeOf(Vec(16, UInt(4.W)))(15 - i) ^
      io.key(key_base + 3, key_base)).asUInt
  }

  for (i <- 0 until 16) {
    result_vec(15 - i) := tweakey_is(15 - t_inv(i))
  }

  io.out := result_vec.asTypeOf(UInt(64.W))
}

class ExecutionContext(max_round: Int = 7, depth: Int = 0, port: Int = 0, step_len: Int)
  extends MultiIOModule with QarmaParams {

  if (port != 1 && port != 2 && port != 0) {
    println("Variable read_port in ExecutionContext should be in [1, 2].")
    sys.exit(-1)
  }

  val slot_depth = if (depth == 0) {
    if (superscalar) 2 else 1
  } else depth
  val read_write_port = if (port == 0) slot_depth else port
  // forward * mr + backward * mr + reflect + end
  val code_width = code_map_width * ((max_round + step_len - 1) / step_len * 2 + 2)
  val valid_width = 1
  val done_width = 1
  val data_width = 64 * 6
  val pointer_width = log2Ceil(code_width)
  // code width + is tk w0 w1 k0 k1
  val data_slot_width = new DataBundle(max_round, step_len).getWidth
  // valid + done + pointer
  val meta_slot_width = new MetaBundle(max_round).getWidth

  val input = IO(new Bundle {
    val new_data = Input(Vec(slot_depth, new DataBundle(max_round, step_len)))
    val new_meta = Input(Vec(slot_depth, new MetaBundle(max_round)))
    val update = Input(Vec(slot_depth, Bool()))
  })
  val output = IO(new Bundle {
    val old_data = Output(Vec(slot_depth, new DataBundle(max_round, step_len)))
    val old_meta = Output(Vec(slot_depth, new MetaBundle(max_round)))
  })

  // Here READ_PORT === WRITE_PORT to simplify and accelerate
  val data = RegInit(VecInit(Seq.fill(slot_depth)(0.U(data_slot_width.W))))
  val meta = RegInit(VecInit(Seq.fill(slot_depth)(0.U(meta_slot_width.W))))

  for (i <- 0 until slot_depth) {
    when(input.update(i)) {
      meta(i) := input.new_meta(i).asUInt
      data(i) := input.new_data(i).asUInt
    }
  }

  for (i <- 0 until slot_depth) {
    output.old_meta(i) := meta(i).asTypeOf(new MetaBundle(max_round))
    output.old_data(i) := data(i).asTypeOf(new DataBundle(max_round, step_len))
  }
}

class QarmaScheduler(size_f: Int = 1, size_r: Int = 1, size_b: Int = 1,
                     depth: Int = 0)
  extends MultiIOModule with QarmaParams {

  val slot_depth = if (depth == 0) {
    if (superscalar) 2 else 1
  } else depth

  // Arbiter with
}

class ParallelExecutionUnit(size_f: Int = 1, size_r: Int = 1, size_b: Int = 1,
                            step_len: Int = 4, max_round: Int = 7)
  extends MultiIOModule with QarmaParams {

  if (step_len > 7 || step_len < 1) {
    println("Variable step_len in ParallelExecutionUnit should be in [1, 7].")
    System.exit(-1)
  }
  val total_size: Int = size_b + size_f + size_r
  if (total_size > 8 || total_size < 3) {
    println("Variable step_len in ParallelExecutionUnit should be in [3, 8].")
    sys.exit(-1)
  }

  // When round_zero becomes 1, the last step's round_zero is 1 if backward, else the first step's
  val io = IO(new Bundle {
    val forward = Vec(size_f, new Bundle {
      val data_is = new OperatorIO
      val data_tk = Output(UInt(64.W))
      val step_after = Input(UInt(log2Ceil(step_len).W))
      val inc_step_number = Input(UInt(log2Ceil(max_round).W))
      val k0 = Input(UInt(64.W))
    })
    val reflect = Vec(size_r, new Bundle {
      val data_is = new OperatorIO
      val k1 = Input(UInt(64.W))
      val w0 = Input(UInt(64.W))
      val w1 = Input(UInt(64.W))
    })
    val backward = Vec(size_b, new Bundle {
      val data_is = new OperatorIO
      val data_tk = Output(UInt(64.W))
      val step_after = Input(UInt(log2Ceil(step_len).W))
      val dec_step_number = Input(UInt(log2Ceil(max_round).W))
      val k0 = Input(UInt(64.W))
    })
  })

  val forward_vec = Array.fill(size_f)(Array.fill(step_len)(Module(new ForwardOperator).io))
  val forward_key_vec = Array.fill(size_f)(Array.fill(step_len)(Module(new ForwardTweakUpdateOperator).io))
  val reflect_forward_vec = Array.fill(size_r)(Module(new ForwardOperator).io)
  val reflect_reflect_vec = Array.fill(size_r)(Module(new PseudoReflectOperator).io)
  val reflect_backward_vec = Array.fill(size_r)(Module(new BackwardOperator).io)
  val backward_vec = Array.fill(size_b)(Array.fill(step_len)(Module(new BackwardOperator).io))
  val backward_key_vec = Array.fill(size_b)(Array.fill(step_len)(Module(new BackwardTweakUpdateOperator).io))

  // Forward update IS and TK with step_len
  for (i <- 0 until size_f) {
    forward_vec(i)(0).is := io.forward(i).data_is.is
    forward_vec(i)(0).tk := io.forward(i).data_is.tk ^ io.forward(i).k0 ^ c(io.forward(i).inc_step_number)
    forward_vec(i)(0).round_zero := io.forward(i).data_is.round_zero
    forward_key_vec(i)(0).old_tk := io.forward(i).data_is.tk
    for (j <- 1 until step_len) {
      forward_vec(i)(j).is := forward_vec(i)(j - 1).out
      forward_vec(i)(j).tk := forward_key_vec(i)(j - 1).new_tk ^ io.forward(i).k0 ^ c(io.forward(i).inc_step_number + j.asUInt)
      forward_vec(i)(j).round_zero := false.B
      forward_key_vec(i)(j).old_tk := forward_key_vec(i)(j - 1).new_tk
    }
    io.forward(i).data_is := MuxLookup(io.forward(i).step_after, 0.U, Array(
      0.U -> forward_vec(i)(0).out,
      1.U -> forward_vec(i)(1).out,
      2.U -> forward_vec(i)(2).out,
      3.U -> forward_vec(i)(3).out
    ))
    io.forward(i).data_tk := MuxLookup(io.forward(i).step_after, 0.U, Array(
      0.U -> forward_key_vec(i)(0).new_tk,
      1.U -> forward_key_vec(i)(1).new_tk,
      2.U -> forward_key_vec(i)(2).new_tk,
      3.U -> forward_key_vec(i)(3).new_tk
    ))
  }

  for (i <- 0 until size_r) {
    reflect_forward_vec(i).is := io.reflect(i).data_is.is
    reflect_forward_vec(i).tk := io.reflect(i).data_is.tk ^ io.reflect(i).w1
    reflect_forward_vec(i).round_zero := false.B
    reflect_reflect_vec(i).is := reflect_forward_vec(i).out
    reflect_reflect_vec(i).key := io.reflect(i).k1
    reflect_backward_vec(i).is := reflect_reflect_vec(i).out
    reflect_backward_vec(i).tk := io.reflect(i).data_is.tk ^ io.reflect(i).w0
    reflect_backward_vec(i).round_zero := false.B
    io.reflect(i).data_is.out := reflect_backward_vec(i).out
  }

  for (i <- 0 until size_b) {
    backward_vec(i)(0).is := io.backward(i).data_is.is
    backward_vec(i)(0).tk := backward_key_vec(i)(0).new_tk ^ io.backward(i).k0 ^ c(io.backward(i).dec_step_number) ^ alpha.asUInt
    backward_vec(i)(0).round_zero := Mux(io.backward(i).step_after === 0.U,
      io.backward(i).data_is.round_zero, false.B)
    backward_key_vec(i)(0).old_tk := io.backward(i).data_is.tk
    for (j <- 1 until step_len) {
      backward_vec(i)(j).is := backward_vec(i)(j - 1).out
      backward_vec(i)(j).tk := backward_key_vec(i)(j).new_tk ^ io.backward(i).k0 ^ c(io.backward(i).dec_step_number - j.asUInt)
      backward_vec(i)(j).round_zero := Mux(io.backward(i).step_after === j.asUInt,
        io.backward(i).data_is.round_zero, false.B)
      backward_key_vec(i)(j).old_tk := backward_key_vec(i)(j - 1).new_tk
    }
    io.backward(i).data_is := MuxLookup(io.backward(i).step_after, 0.U, Array(
      0.U -> backward_vec(i)(0).out,
      1.U -> backward_vec(i)(1).out,
      2.U -> backward_vec(i)(2).out,
      3.U -> backward_vec(i)(3).out
    ))
    io.backward(i).data_tk := MuxLookup(io.backward(i).step_after, 0.U, Array(
      0.U -> backward_key_vec(i)(0).new_tk,
      1.U -> backward_key_vec(i)(1).new_tk,
      2.U -> backward_key_vec(i)(2).new_tk,
      3.U -> backward_key_vec(i)(3).new_tk
    ))
  }

}

