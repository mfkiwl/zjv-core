package tile.common.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import config.projectConfig
import tile._

/* ------ Here is the WRAPPER of BTB ------ */

class BTBSRAMWrapperIO extends Bundle with phvntomParams {
  val CLK = Input(Clock())
  val CEN = Input(Bool())
  val WEN = Input(Bool())
  val	A = Input(UInt(8.W))
  val D = Input(UInt(39.W))
  val Q = Output(UInt(39.W))
}

class S011HD1P_X128Y2D39 extends BlackBox {
  val io = IO(new BTBSRAMWrapperIO)
}

class BTBBRAMWrapperIO extends Bundle with phvntomParams {
  val clk = Input(Clock())
  val rst = Input(Reset())
  val we = Input(Bool())
  val addr = Input(UInt(bpuEntryBits.W))
  val din = Input(UInt(39.W))
  val dout = Input(UInt(39.W))
}

class single_port_ram_bpu extends BlackBox {
  val io = IO(new BTBBRAMWrapperIO)
}

/* --------- BTB WRAPPER ends here --------- */

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
  val index_in = Input(UInt(bpuEntryBits.W))
  val predit_taken = Output(Bool())
  // Update History
  val stall_update = Input(Bool())
  val update_valid = Input(Bool())
  val update_index_in = Input(UInt(bpuEntryBits.W))
  val update_taken_in = Input(Bool())
}

class BHT extends Module with phvntomParams {
  val io = IO(new BHTIO)

  val s_nt = 0.U(predictorBits.W)
  val w_nt = 1.U(predictorBits.W)
  val w_tk = 2.U(predictorBits.W)
  val s_tk = 3.U(predictorBits.W)

  val bht_entries = RegInit(VecInit(Seq.fill(1 << bpuEntryBits)(s_nt)))
  val bht_chosen = bht_entries(io.update_index_in)

  when(!io.stall_update) {
    when(io.update_valid) {
      when(io.update_taken_in) {
        when(bht_chosen === s_nt) {
          bht_entries(io.update_index_in) := w_nt
        }.elsewhen(bht_chosen === w_nt) {
          bht_entries(io.update_index_in) := w_tk
        }.elsewhen(bht_chosen === w_tk) {
          bht_entries(io.update_index_in) := s_tk
        }
      }.otherwise {
        when(bht_chosen === s_tk) {
          bht_entries(io.update_index_in) := w_tk
        }.elsewhen(bht_chosen === w_tk) {
          bht_entries(io.update_index_in) := w_nt
        }.elsewhen(bht_chosen === w_nt) {
          bht_entries(io.update_index_in) := s_nt
        }
      }
    }.otherwise {
      bht_entries(io.update_index_in) := s_nt
    }
  }

  // Taken when the most significant bit is 1
  io.predit_taken := bht_entries(io.index_in)(predictorBits - 1)
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

class BTB extends Module with phvntomParams with projectConfig {
  val io = IO(new BTBIO)

  if (chiplink) {
    /* ------ Use Generated RAM to Replace SyncReadMem ------ */
    val btb_entries = Module(new S011HD1P_X128Y2D39)

    btb_entries.io.CLK := clock
    btb_entries.io.CEN := false.B
    btb_entries.io.WEN := !io.update_valid
    btb_entries.io.A := Mux(io.update_valid, io.update_index, io.index_in)
    btb_entries.io.D := io.update_target(38, 0)

    io.target_out := Cat(Fill(xlen - 39, btb_entries.io.Q(38)), btb_entries.io.Q)
  } else if (fpga) {
    val btb_entries = Module(new single_port_ram_bpu)

    btb_entries.io.clk := clock
    btb_entries.io.rst := reset
    btb_entries.io.we := io.update_valid
    btb_entries.io.addr := Mux(io.update_valid, io.update_index, io.index_in)
    btb_entries.io.din := io.update_target(38, 0)

    io.target_out := Cat(Fill(xlen - 39, btb_entries.io.dout(38)), btb_entries.io.dout)
  } else {
    val btb_entries = SyncReadMem(1 << bpuEntryBits, UInt(39.W))
    val read_data = btb_entries.read(io.index_in, !io.update_valid)

    io.target_out := Cat(Fill(xlen - 39, read_data(38)), read_data)

    when(io.update_valid) {
      btb_entries.write(io.update_index, io.update_target(38, 0))
    }
 }

//  when(io.update_valid) {
//    printf("~wen %x, addr %x, data %x, out %x\n", btb_entries.io.WEN, btb_entries.io.A, btb_entries.io.D, io.target_out)
//  }
}

class BPUIO extends Bundle with phvntomParams {
  // Normal Prediction
  val pc_to_predict = Input(UInt(xlen.W))
  val branch_taken = Output(Bool())
  val pc_in_btb = Output(UInt(xlen.W))
  // Modify Data from BRANCH
  val stall_update = Input(Bool())
  val feedback_pc = Input(UInt(xlen.W))
  val feedback_is_br = Input(Bool())
  val feedback_target_pc = Input(UInt(xlen.W))
  val feedback_br_taken = Input(Bool())
  // Stall Req For Sync Mem
  val stall_req = Output(Bool())
  // Modify BTB
  val update_btb = Input(Bool())
}

// TODO The first step will determine if the PC should change.
// TODO The second step will determine where to go.
// TODO The first and the second step will be done in parallel.
// TODO First, I will implement a simple, configurable P-Share Predictor.
// TODO Then, I will implement a simple RAS for CALL-RET.
class BPU extends Module with phvntomParams {
  val io = IO(new BPUIO)

  def get_index(addr: UInt): UInt = {
    addr(bpuEntryBits + 1, 2)
  }

//  val pht = Module(new PHT)
  val bht = Module(new BHT)
  val btb = Module(new BTB)

//  printf("pc %x, predict_tkn %x, tar_pc %x\n", io.pc_to_predict,
//  io.branch_taken, io.pc_in_btb)

//  val history_from_pht = pht.io.history_out
 val predict_taken_from_bht = bht.io.predit_taken

//   TODO Here, we do not care C Extension for now
//  pht.io.index_in := io.pc_to_predict(bpuEntryBits + 1, 2)
//  pht.io.update_valid := io.feedback_is_br
//  pht.io.update_index_in := io.feedback_pc(bpuEntryBits + 1, 2)
//  pht.io.update_taken_in := io.feedback_br_taken

// when(!io.stall_update && io.feedback_is_br) {
//   printf("index %x, is_br %x, tar_pc %x\n", io.feedback_pc, io.feedback_is_br, io.feedback_target_pc)
// }

  bht.io.index_in := get_index(io.pc_to_predict)
  bht.io.update_valid := io.feedback_is_br
  bht.io.update_index_in := get_index(io.feedback_pc)
  bht.io.update_taken_in := io.feedback_br_taken
  bht.io.stall_update := io.stall_update

  btb.io.index_in := get_index(io.pc_to_predict)
  btb.io.update_valid := io.update_btb && !io.stall_update
//  btb.io.update_valid := io.feedback_is_br && io.feedback_br_taken
  btb.io.update_index := get_index(io.feedback_pc)
  btb.io.update_target := io.feedback_target_pc

  io.branch_taken := predict_taken_from_bht
  io.pc_in_btb := btb.io.target_out

  val last_stall_req = RegInit(Bool(), false.B)
  val last_pc_in = RegInit(UInt(xlen.W), 0.U)
  last_stall_req := io.stall_req
  last_pc_in := io.pc_to_predict

  when(last_pc_in =/= io.pc_to_predict && io.branch_taken) {
    io.stall_req := true.B
  }.otherwise {
    io.stall_req := false.B
  }
}