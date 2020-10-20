package rv64_nstage.fu

import chisel3._
import rv64_nstage.core._

class PHTIO extends Bundle with phvntomParams {
  // Combinational History Query
  val index_in = Input(UInt(bpuEntryBits.W))
  val history_out = Output(UInt(historyBits.W))
  // Update History
  val update_valid = Input(Bool())
  val update_index_in = Input(UInt(bpuEntryBits.W))
  val update_taken_in = Input(Bool())
}

class PHT extends Module with phvntomParams {
  val io = IO(new PHTIO)

  // The latest bit is the right-most one.
  val pht_entries = RegInit(VecInit(Seq.fill(1 << bpuEntryBits)(0.U(historyBits.W))))

  when(io.update_valid) {
    pht_entries(io.update_index_in) := Cat(pht_entries(io.update_index_in)(historyBits - 2, 0), io.update_taken_in)
  }

  io.history_out := pht_entries(io.index_in)
}

class BHTIO extends Bundle with phvntomParams {
  // Combinational History Query
  val xored_index_in = Input(UInt(bpuEntryBits.W))
  val predit_taken = Output(Bool())
  // Update History
  val update_valid = Input(Bool())
  val update_xored_index_in = Input(UInt(bpuEntryBits.W))
  val update_taken_in = Input(Bool())
}

class BHT extends Module with phvntomParams {
  val io = IO(new BHTIO)

  val s_nt = 0.U(predictorBits.W)
  val w_nt = 1.U(predictorBits.W)
  val w_tk = 2.U(predictorBits.W)
  val s_tk = 3.U(predictorBits.W)

  val bht_entries = RegInit(VecInit(Seq.fill(1 << bpuEntryBits)(s_nt)))
  val bht_chosen = bht_entries(io.update_xored_index_in)

  when(io.update_valid) {
    when(io.update_taken_in) {
      when(bht_chosen === s_nt) {
        bht_entries(io.update_xored_index_in) := w_nt
      }.elsewhen(bht_chosen === w_nt) {
        bht_entries(io.update_xored_index_in) := w_tk
      }.elsewhen(bht_chosen === w_tk) {
        bht_entries(io.update_xored_index_in) := s_tk
      }
    }.otherwise {
      when(bht_chosen === s_tk) {
        bht_entries(io.update_xored_index_in) := w_tk
      }.elsewhen(bht_chosen === w_tk) {
        bht_entries(io.update_xored_index_in) := w_nt
      }.elsewhen(bht_chosen === w_nt) {
        bht_entries(io.update_xored_index_in) := s_nt
      }
    }
  }

  // Taken when the most significant bit is 1
  io.predit_taken := bht_entries(io.xored_index_in)(predictorBits - 1)
}

class BTBIO extends Bundle with phvntomParams {
  // Combinational History Query
  val index_in = Input(UInt(bpuEntryBits.W))
  val target_out = Output(UInt(xlen.W))
  // Prediction is OK, BUT Target is Wrong
  val update_valid = Input(Bool())
  val update_index = Input(UInt(bpuEntryBits.W))
  val update_target = Input(UInt(xlen.W))
}

class BTB extends Module with phvntomParams {
  val io = IO(new BTBIO)

  val btb_entries = RegInit(VecInit(Seq.fill(1 << bpuEntryBits)(0.U)))

  when(io.update_valid) {
    btb_entries(io.update_index) := io.update_target
  }

  io.target_out := btb_entries(io.index_in)
}

class BPUIO extends Bundle with phvntomParams {
  // Normal Prediction
  val pc_to_predict = Input(UInt(xlen.W))
  val branch_taken = Output(Bool())
  val jump_taken = Output(Bool()) // TODO
  val pc_in_btb = Output(UInt(xlen.W))
  val pc_in_ras = Output(UInt(xlen.W))  // TODO
  // Modify Data from BRANCH
  val pc_feedback = Input(UInt(xlen.W))
  val pc_feedback_br = Input(Bool())
  val pc_feedback_target_addr = Input(UInt(xlen.W))
  val pc_feedback_taken = Input(Bool())
  // TODO Modify Data from CALL-RET
}

// TODO The first step will determine if the PC should change.
// TODO The second step will determine where to go.
// TODO The first and the second step will be done in parallel.
// TODO First, I will implement a simple, configurable P-Share Predictor.
// TODO Then, I will implement a simple RAS for CALL-RET.
class BPU extends Module with phvntomParams {
  val io = IO(new BPUIO)

  val pht = Module(new PHT)
  val bht = Module(new BHT)
  val btb = Module(new BTB)

  // TODO Here, we do not care C Extension for now
  pht.io.index_in := io.pc_to_predict(bpuEntryBits + 1, 2)
  pht.io.update_valid := io.pc_feedback_br

  // Combinational History Query
  val index_in = Input(UInt(bpuEntryBits.W))
  val history_out = Output(UInt(historyBits.W))
  // Update History
  val update_valid = Input(Bool())
  val update_index_in = Input(UInt(bpuEntryBits.W))
  val update_taken_in = Input(Bool())
}
