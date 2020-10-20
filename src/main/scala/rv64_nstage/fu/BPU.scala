package rv64_nstage.fu

import chisel3._
import rv64_nstage.core._
import chisel3.util._

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

  val btb_entries = RegInit(VecInit(Seq.fill(1 << bpuEntryBits)("h80000000".U)))

  when(io.update_valid) {
    btb_entries(io.update_index) := io.update_target
  }

  io.target_out := btb_entries(io.index_in)
}

class BPUIO extends Bundle with phvntomParams {
  // Normal Prediction
  val pc_to_predict = Input(UInt(xlen.W))
  val branch_taken = Output(Bool())
  val pc_in_btb = Output(UInt(xlen.W))
  val xored_index_out = Output(UInt(bpuEntryBits.W))
  // Modify Data from BRANCH
  val feedback_pc = Input(UInt(xlen.W))
  val feedback_xored_index = Input(UInt(bpuEntryBits.W))
  val feedback_is_br = Input(Bool())
  val feedback_target_pc = Input(UInt(xlen.W))
  val feedback_br_taken = Input(Bool())
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

//  printf("fb_pc %x, fb_xored_in %x, fb_is_br_inst %x, fb_tar_pc %x, fb_br_tk %x\n", io.feedback_pc,
//  io.feedback_xored_index, io.feedback_is_br, io.feedback_target_pc, io.feedback_br_taken)

  val history_from_pht = pht.io.history_out
  val predict_taken_from_bht = bht.io.predit_taken
  val xored_index = history_from_pht ^ io.pc_to_predict(bpuEntryBits + 1, 2)

  // TODO Here, we do not care C Extension for now
  pht.io.index_in := io.pc_to_predict(bpuEntryBits + 1, 2)
  pht.io.update_valid := io.feedback_is_br
  pht.io.update_index_in := io.feedback_pc(bpuEntryBits + 1, 2)
  pht.io.update_taken_in := io.feedback_br_taken

  bht.io.xored_index_in := xored_index
  bht.io.update_valid := io.feedback_is_br
  bht.io.update_xored_index_in := io.feedback_xored_index
  bht.io.update_taken_in := io.feedback_br_taken

  btb.io.index_in := io.pc_to_predict(bpuEntryBits + 1, 2)
  btb.io.update_valid := io.feedback_is_br && io.feedback_br_taken
  btb.io.update_index := io.feedback_pc(bpuEntryBits + 1, 2)
  btb.io.update_target := io.feedback_target_pc

  io.branch_taken := false.B // predict_taken_from_bht
  io.pc_in_btb := btb.io.target_out
  io.xored_index_out := xored_index
}
