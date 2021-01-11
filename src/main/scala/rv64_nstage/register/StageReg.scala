package rv64_nstage.register

import chisel3._
import chisel3.util._
import rv64_nstage.control.ControlConst._
import rv64_nstage.control._
import rv64_nstage.core.phvntomParams

class BasicStageRegIO extends Bundle with phvntomParams {
  // Stall Signal
  val stall = Input(Bool())
  // Interrupt or Misprediction Flush
  val flush_one = Input(Bool())
  // Info After Last Stage
  val last_stage_atomic_stall_req = Input(Bool())
  val next_stage_atomic_stall_req = Input(Bool())
  val next_stage_flush_req = Input(Bool())
  val bubble_in = Input(Bool())
  val pc_in = Input(UInt(xlen.W))
  // Output
  val bubble_out = Output(Bool())
  val pc_out = Output(UInt(xlen.W))
}

class InstFaultIO extends Bundle with phvntomParams {
  val inst_af_in = Input(Bool())
  val inst_pf_in = Input(Bool())
  val inst_af_out = Output(Bool())
  val inst_pf_out = Output(Bool())
}

class BPUPredictIO extends Bundle with phvntomParams {
  val predict_taken_in = Input(Bool())
  val target_in = Input(UInt(xlen.W))
  val xored_index_in = Input(UInt(bpuEntryBits.W))
  val predict_taken_out = Output(Bool())
  val target_out = Output(UInt(xlen.W))
  val xored_index_out = Output(UInt(bpuEntryBits.W))
}

class InstIO extends Bundle with phvntomParams {
  val inst_in = Input(UInt(32.W)) 
  val inst_out = Output(UInt(32.W)) 
}

class InstInfoIO extends Bundle with phvntomParams {
  val inst_info_in = Flipped(new InstInfo)
  val inst_info_out = Flipped(Flipped(new InstInfo))
}

class ExeInfoIO extends Bundle with phvntomParams {
  val alu_val_in = Input(UInt(xlen.W))
  val inst_addr_misaligned_in = Input(Bool())
  val mem_wdata_in = Input(UInt(xlen.W))
  val alu_val_out = Output(UInt(xlen.W))
  val inst_addr_misaligned_out = Output(Bool())
  val mem_wdata_out = Output(UInt(xlen.W))
}

class BrJumpDelayIO extends Bundle with phvntomParams {
  val misprediction_in = Input(Bool())
  val wrong_target_in = Input(Bool())
  val predict_taken_but_not_br_in = Input(Bool())
  val bjpc_in = Input(UInt(xlen.W))
  val feedback_pc_in = Input(UInt(xlen.W))
  val feedback_xored_index_in = Input(UInt(bpuEntryBits.W))
  val feedback_is_br_in = Input(Bool())
  val feedback_target_pc_in = Input(UInt(xlen.W))
  val feedback_br_taken_in = Input(Bool())
  val misprediction_out = Output(Bool())
  val wrong_target_out = Output(Bool())
  val predict_taken_but_not_br_out = Output(Bool())
  val bjpc_out = Output(UInt(xlen.W))
  val feedback_pc_out = Output(UInt(xlen.W))
  val feedback_xored_index_out = Output(UInt(bpuEntryBits.W))
  val feedback_is_br_out = Output(Bool())
  val feedback_target_pc_out = Output(UInt(xlen.W))
  val feedback_br_taken_out = Output(Bool())
}

class MultDivIO extends Bundle with phvntomParams {
  val rs1_after_fwd_in = Input(UInt(xlen.W))
  val rs2_after_fwd_in = Input(UInt(xlen.W))
  val rs1_after_fwd_out = Output(UInt(xlen.W))
  val rs2_after_fwd_out = Output(UInt(xlen.W))
}

class IntMemPfIO extends Bundle with phvntomParams {
  val s_external_int_in = Input(Bool())
  val external_int_in = Input(Bool())
  val software_int_in = Input(Bool())
  val timer_int_in = Input(Bool())
  val mem_pf_in = Input(Bool())
  val mem_af_in = Input(Bool())
  val s_external_int_out = Output(Bool())
  val external_int_out = Output(Bool())
  val software_int_out = Output(Bool())
  val timer_int_out = Output(Bool())
  val mem_pf_out = Output(Bool())
  val mem_af_out = Output(Bool())
}

// TODO Access Fault is PURELY For Difftest
class CSRInfoIO extends Bundle with phvntomParams {
  val csr_val_in = Input(UInt(xlen.W))
  val expt_in = Input(Bool())
  val int_resp_in = Input(Bool())
  val compare_in = Input(Bool())
  val comp_res_in = Input(Bool())
  val af_in = Input(Bool())
  val csr_val_out = Output(UInt(xlen.W))
  val expt_out = Output(Bool())
  val int_resp_out = Output(Bool())
  val compare_out = Output(Bool())
  val comp_res_out = Output(Bool())
  val af_out = Output(Bool())
}

class MemDataIO extends Bundle with phvntomParams {
  val mem_val_in = Input(UInt(xlen.W))
  val mem_val_out = Output(UInt(xlen.W))
}

class RegIf1If2IO extends Bundle with phvntomParams {
  val bsrio = Flipped(Flipped(new BasicStageRegIO))
  val ifio = Flipped(Flipped(new InstFaultIO))
  val bpio = Flipped(Flipped(new BPUPredictIO))
}

class RegIf1If2 extends Module with phvntomParams {
  val io = IO(new RegIf1If2IO)

  val bubble = RegInit(Bool(), true.B)
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val inst_pf = RegInit(Bool(), false.B)
  val predict_tk = RegInit(Bool(), false.B)
  val ptar = RegInit(UInt(xlen.W), 0.U)
  val xored_index = RegInit(UInt(bpuEntryBits.W), 0.U)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.bsrio.stall || io.bsrio.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.bsrio.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.bsrio.stall) {
    when((last_delay && delay_flush) || io.bsrio.bubble_in || io.bsrio.flush_one) {
      pc := 0.U
      bubble := true.B
      inst_af := false.B
      inst_pf := false.B
      predict_tk := false.B
      ptar := 0.U
      xored_index := 0.U
    }.otherwise {
      pc := io.bsrio.pc_in
      bubble := false.B
      inst_af := io.ifio.inst_af_in
      inst_pf := io.ifio.inst_pf_in
      predict_tk := io.bpio.predict_taken_in
      ptar := io.bpio.target_in
      xored_index := io.bpio.xored_index_in
    }
  }.elsewhen(!io.bsrio.next_stage_atomic_stall_req && io.bsrio.flush_one && !io.bsrio.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst_af := false.B
    inst_pf := false.B
    predict_tk := false.B
    ptar := 0.U
    xored_index := 0.U
  }

  io.bsrio.bubble_out := bubble
  io.bsrio.pc_out := pc
  io.ifio.inst_af_out := inst_af
  io.ifio.inst_pf_out := inst_pf
  io.bpio.predict_taken_out := predict_tk
  io.bpio.target_out := ptar
  io.bpio.xored_index_out := xored_index
}

class RegIf3IdIO extends Bundle with phvntomParams {
  val bsrio = Flipped(Flipped(new BasicStageRegIO))
  val ifio = Flipped(Flipped(new InstFaultIO))
  val instio = Flipped(Flipped(new InstIO))
  val bpio = Flipped(Flipped(new BPUPredictIO))
}

class RegIf3Id extends Module with phvntomParams {
  val io = IO(new RegIf3IdIO)
  
  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), BUBBLE)
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val inst_pf = RegInit(Bool(), false.B)
  val predict_tk = RegInit(Bool(), false.B)
  val ptar = RegInit(UInt(xlen.W), 0.U)
  val xored_index = RegInit(UInt(bpuEntryBits.W), 0.U)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.bsrio.stall || io.bsrio.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.bsrio.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.bsrio.stall) {
    when((last_delay && delay_flush) || io.bsrio.bubble_in || io.bsrio.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
      inst_pf := false.B
      predict_tk := false.B
      ptar := 0.U
      xored_index := 0.U
    }.otherwise {
      pc := io.bsrio.pc_in
      bubble := false.B
      inst := io.instio.inst_in
      inst_af := io.ifio.inst_af_in
      inst_pf := io.ifio.inst_pf_in
      predict_tk := io.bpio.predict_taken_in
      ptar := io.bpio.target_in
      xored_index := io.bpio.xored_index_in
    }
  }.elsewhen(!io.bsrio.next_stage_atomic_stall_req && io.bsrio.flush_one && !io.bsrio.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
    inst_pf := false.B
    predict_tk := false.B
    ptar := 0.U
    xored_index := 0.U
  }

  io.bsrio.bubble_out := bubble
  io.bsrio.pc_out := pc
  io.instio.inst_out := inst
  io.ifio.inst_af_out := inst_af
  io.ifio.inst_pf_out := inst_pf
  io.bpio.predict_taken_out := predict_tk
  io.bpio.target_out := ptar
  io.bpio.xored_index_out := xored_index
}

class RegIdExeIO extends Bundle with phvntomParams {
  val bsrio = Flipped(Flipped(new BasicStageRegIO))
  val ifio = Flipped(Flipped(new InstFaultIO))
  val instio = Flipped(Flipped(new InstIO))
  val iiio = Flipped(Flipped(new InstInfoIO))
  val bpio = Flipped(Flipped(new BPUPredictIO))
}

class RegIdExe extends Module with phvntomParams {
  val io = IO(new RegIdExeIO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), BUBBLE) 
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val inst_pf = RegInit(Bool(), false.B)
  val predict_tk = RegInit(Bool(), false.B)
  val ptar = RegInit(UInt(xlen.W), 0.U)
  val xored_index = RegInit(UInt(bpuEntryBits.W), 0.U)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX, amoXXX, fwdXXX, flushXXX, false.B, 0.U(5.W), 0.U(5.W), 0.U(5.W))
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits + fwdBits + flushBits + 1 + 5 + 5 + 5).W),
    default_inst_info)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.bsrio.stall || io.bsrio.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.bsrio.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.bsrio.stall) {
    when((last_delay && delay_flush) || io.bsrio.bubble_in || io.bsrio.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
      inst_pf := false.B
      predict_tk := false.B
      ptar := 0.U
      xored_index := 0.U
      inst_info := default_inst_info
    }.otherwise {
      pc := io.bsrio.pc_in
      bubble := false.B
      inst := io.instio.inst_in
      inst_af := io.ifio.inst_af_in
      inst_pf := io.ifio.inst_pf_in
      predict_tk := io.bpio.predict_taken_in
      ptar := io.bpio.target_in
      xored_index := io.bpio.xored_index_in
      inst_info := io.iiio.inst_info_in.asUInt
    }
  }.elsewhen(!io.bsrio.next_stage_atomic_stall_req && io.bsrio.flush_one && !io.bsrio.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
    inst_pf := false.B
    predict_tk := false.B
    ptar := 0.U
    xored_index := 0.U
    inst_info := default_inst_info
  }

  io.bsrio.bubble_out := bubble
  io.bsrio.pc_out := pc
  io.instio.inst_out := inst
  io.ifio.inst_af_out := inst_af
  io.iiio.inst_info_out := inst_info.asTypeOf(new InstInfo)
  io.ifio.inst_pf_out := inst_pf
  io.bpio.predict_taken_out := predict_tk
  io.bpio.target_out := ptar
  io.bpio.xored_index_out := xored_index
}

class RegExeDTLBIO extends Bundle with phvntomParams {
  val bsrio = Flipped(Flipped(new BasicStageRegIO))
  val ifio = Flipped(Flipped(new InstFaultIO))
  val instio = Flipped(Flipped(new InstIO))
  val iiio = Flipped(Flipped(new InstInfoIO))
  val aluio = Flipped(Flipped(new ExeInfoIO))
  val bjio = Flipped(Flipped(new BrJumpDelayIO))
  val bpio = Flipped(Flipped(new BPUPredictIO))
  val mdio = Flipped(Flipped(new MultDivIO))
  val bpufb_stall_update = Output(Bool())
}

class RegExeDTLB extends Module with phvntomParams {
  val io = IO(new RegExeDTLBIO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), BUBBLE) 
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val inst_pf = RegInit(Bool(), false.B)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX, amoXXX, fwdXXX, flushXXX, false.B, 0.U(5.W), 0.U(5.W), 0.U(5.W))
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits + fwdBits + flushBits + 1 + 5 + 5 + 5).W),
    default_inst_info)
  val alu_val = RegInit(UInt(xlen.W), 0.U)
  val inst_addr_misaligned = RegInit(Bool(), false.B)
  val mem_wdata = RegInit(UInt(xlen.W), 0.U)
  val misprediction = RegInit(Bool(), false.B)
  val wrong_target = RegInit(Bool(), false.B)
  val predict_taken_but_not_br = RegInit(Bool(), false.B)
  val bjpc = RegInit(UInt(xlen.W), startAddr.asUInt)
  val feedback_pc = RegInit(UInt(xlen.W), startAddr.asUInt)
  val feedback_xored_index = RegInit(UInt(bpuEntryBits.W), 0.U)
  val feedback_is_br = RegInit(Bool(), false.B)
  val feedback_target_pc = RegInit(UInt(xlen.W), startAddr.asUInt)
  val feedback_br_taken = RegInit(Bool(), false.B)
  val rs1_after_fwd = RegInit(UInt(xlen.W), 0.U)
  val rs2_after_fwd = RegInit(UInt(xlen.W), 0.U)
  val bpufb_stall_update = RegInit(Bool(), false.B)
  val predict_tk = RegInit(Bool(), false.B)
  val ptar = RegInit(UInt(xlen.W), 0.U)
  val xored_index = RegInit(UInt(bpuEntryBits.W), 0.U)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.bsrio.stall || io.bsrio.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.bsrio.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.bsrio.stall) {
    when((last_delay && delay_flush) || io.bsrio.bubble_in || io.bsrio.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
      inst_pf := false.B
      inst_info := default_inst_info
      alu_val := 0.U
      inst_addr_misaligned := false.B
      mem_wdata := 0.U
      misprediction := false.B
      wrong_target := false.B
      predict_taken_but_not_br := false.B
      bjpc := startAddr.asUInt
      feedback_pc := startAddr.asUInt
      feedback_xored_index := 0.U
      feedback_is_br := false.B
      feedback_target_pc := startAddr.asUInt
      feedback_br_taken := false.B
      rs1_after_fwd := 0.U
      rs2_after_fwd := 0.U
      bpufb_stall_update := true.B
      predict_tk := false.B
      ptar := 0.U
      xored_index := 0.U
    }.otherwise {
      pc := io.bsrio.pc_in
      bubble := false.B
      inst := io.instio.inst_in
      inst_af := io.ifio.inst_af_in
      inst_pf := io.ifio.inst_pf_in
      inst_info := io.iiio.inst_info_in.asUInt
      alu_val := io.aluio.alu_val_in
      inst_addr_misaligned := io.aluio.inst_addr_misaligned_in
      mem_wdata := io.aluio.mem_wdata_in
      misprediction := io.bjio.misprediction_in
      wrong_target := io.bjio.wrong_target_in
      predict_taken_but_not_br := io.bjio.predict_taken_but_not_br_in
      bjpc := io.bjio.bjpc_in
      feedback_pc := io.bjio.feedback_pc_in
      feedback_xored_index := io.bjio.feedback_xored_index_in
      feedback_is_br := io.bjio.feedback_is_br_in
      feedback_target_pc := io.bjio.feedback_target_pc_in
      feedback_br_taken := io.bjio.feedback_br_taken_in
      rs1_after_fwd := io.mdio.rs1_after_fwd_in
      rs2_after_fwd := io.mdio.rs2_after_fwd_in
      bpufb_stall_update := false.B
      predict_tk := io.bpio.predict_taken_in
      ptar := io.bpio.target_in
      xored_index := io.bpio.xored_index_in
    }
  }.elsewhen(!io.bsrio.next_stage_atomic_stall_req && io.bsrio.flush_one && !io.bsrio.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
    inst_pf := false.B
    inst_info := default_inst_info
    alu_val := 0.U
    inst_addr_misaligned := false.B
    mem_wdata := 0.U
    misprediction := false.B
    wrong_target := false.B
    predict_taken_but_not_br := false.B
    bjpc := startAddr.asUInt
    feedback_pc := startAddr.asUInt
    feedback_xored_index := 0.U
    feedback_is_br := false.B
    feedback_target_pc := startAddr.asUInt
    feedback_br_taken := false.B
    rs1_after_fwd := 0.U
    rs2_after_fwd := 0.U
    bpufb_stall_update := true.B
    predict_tk := false.B
    ptar := 0.U
    xored_index := 0.U
  }.elsewhen(io.bsrio.flush_one) {
    bpufb_stall_update := true.B
    misprediction := false.B
    wrong_target := false.B
    predict_taken_but_not_br := false.B
    inst_info.asTypeOf(new InstInfo).pcSelect := pcPlus4
  }

  io.bsrio.bubble_out := bubble
  io.bsrio.pc_out := pc
  io.instio.inst_out := inst
  io.ifio.inst_af_out := inst_af
  io.iiio.inst_info_out := inst_info.asTypeOf(new InstInfo)
  io.aluio.alu_val_out := alu_val
  io.aluio.inst_addr_misaligned_out := inst_addr_misaligned
  io.aluio.mem_wdata_out := mem_wdata
  io.ifio.inst_pf_out := inst_pf
  io.bjio.misprediction_out := misprediction
  io.bjio.wrong_target_out := wrong_target
  io.bjio.predict_taken_but_not_br_out := predict_taken_but_not_br
  io.bjio.bjpc_out := bjpc
  io.bjio.feedback_pc_out := feedback_pc
  io.bjio.feedback_xored_index_out := feedback_xored_index
  io.bjio.feedback_is_br_out := feedback_is_br
  io.bjio.feedback_target_pc_out := feedback_target_pc
  io.bjio.feedback_br_taken_out := feedback_br_taken
  io.mdio.rs1_after_fwd_out := rs1_after_fwd
  io.mdio.rs2_after_fwd_out := rs2_after_fwd
  io.bpufb_stall_update := bpufb_stall_update
  io.bpio.predict_taken_out := predict_tk
  io.bpio.target_out := ptar
  io.bpio.xored_index_out := xored_index
}

class RegDTLBMem1IO extends Bundle with phvntomParams {
  val bsrio = Flipped(Flipped(new BasicStageRegIO))
  val ifio = Flipped(Flipped(new InstFaultIO))
  val instio = Flipped(Flipped(new InstIO))
  val iiio = Flipped(Flipped(new InstInfoIO))
  val aluio = Flipped(Flipped(new ExeInfoIO))
  val intio = Flipped(Flipped(new IntMemPfIO))
}

class RegDTLBMem1 extends Module with phvntomParams {
  val io = IO(new RegDTLBMem1IO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), BUBBLE) 
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val inst_pf = RegInit(Bool(), false.B)
  val mem_af = RegInit(Bool(), false.B)
  val mem_pf = RegInit(Bool(), false.B)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX, amoXXX, fwdXXX, flushXXX, false.B, 0.U(5.W), 0.U(5.W), 0.U(5.W))
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits + fwdBits + flushBits + 1 + 5 + 5 + 5).W),
    default_inst_info)
  val alu_val = RegInit(UInt(xlen.W), 0.U)
  val inst_addr_misaligned = RegInit(Bool(), false.B)
  val mem_wdata = RegInit(UInt(xlen.W), 0.U)
  val soft_int = RegInit(Bool(), false.B)
  val extern_int = RegInit(Bool(), false.B)
  val s_extern_int = RegInit(Bool(), false.B)
  val timer_int = RegInit(Bool(), false.B)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.bsrio.stall || io.bsrio.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.bsrio.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.bsrio.stall) {
    when((last_delay && delay_flush) || io.bsrio.bubble_in || io.bsrio.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
      inst_pf := false.B
      mem_af := false.B
      mem_pf := false.B
      inst_info := default_inst_info
      alu_val := 0.U
      inst_addr_misaligned := false.B
      mem_wdata := 0.U
      soft_int := false.B
      extern_int := false.B
      timer_int := false.B
      s_extern_int := false.B
    }.otherwise {
      pc := io.bsrio.pc_in
      bubble := false.B
      inst := io.instio.inst_in
      inst_af := io.ifio.inst_af_in
      inst_pf := io.ifio.inst_pf_in
      mem_af := io.intio.mem_af_in
      mem_pf := io.intio.mem_pf_in
      inst_info := io.iiio.inst_info_in.asUInt
      alu_val := io.aluio.alu_val_in
      inst_addr_misaligned := io.aluio.inst_addr_misaligned_in
      mem_wdata := io.aluio.mem_wdata_in
      soft_int := io.intio.software_int_in
      extern_int := io.intio.external_int_in
      timer_int := io.intio.timer_int_in
      s_extern_int := io.intio.s_external_int_in
    }
  }.elsewhen(!io.bsrio.next_stage_atomic_stall_req && io.bsrio.flush_one && !io.bsrio.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
    inst_pf := false.B
    mem_af := false.B
    mem_pf := false.B
    inst_info := default_inst_info
    alu_val := 0.U
    inst_addr_misaligned := false.B
    mem_wdata := 0.U
    soft_int := false.B
    extern_int := false.B
    timer_int := false.B
    s_extern_int := false.B
  }.otherwise {
    soft_int := false.B
    extern_int := false.B
    timer_int := false.B
    s_extern_int := false.B
  }

  io.bsrio.bubble_out := bubble
  io.bsrio.pc_out := pc
  io.instio.inst_out := inst
  io.ifio.inst_af_out := inst_af
  io.ifio.inst_pf_out := inst_pf
  io.intio.mem_af_out := mem_af
  io.intio.mem_pf_out := mem_pf
  io.iiio.inst_info_out := inst_info.asTypeOf(new InstInfo)
  io.aluio.alu_val_out := alu_val
  io.aluio.inst_addr_misaligned_out := inst_addr_misaligned
  io.aluio.mem_wdata_out := mem_wdata
  io.intio.external_int_out := extern_int
  io.intio.software_int_out := soft_int
  io.intio.timer_int_out := timer_int
  io.intio.s_external_int_out := s_extern_int
}

class RegMem1Mem2IO extends Bundle with phvntomParams {
  val bsrio = Flipped(Flipped(new BasicStageRegIO))
  val instio = Flipped(Flipped(new InstIO))
  val iiio = Flipped(Flipped(new InstInfoIO))
  val aluio = Flipped(Flipped(new ExeInfoIO))
  val csrio = Flipped(Flipped(new CSRInfoIO))
}

class RegMem1Mem2 extends Module with phvntomParams {
  val io = IO(new RegMem1Mem2IO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), BUBBLE) 
  val pc = RegInit(UInt(xlen.W), 0.U)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX, amoXXX, fwdXXX, flushXXX, false.B, 0.U(5.W), 0.U(5.W), 0.U(5.W))
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits + fwdBits + flushBits + 1 + 5 + 5 + 5).W),
    default_inst_info)
  val alu_val = RegInit(UInt(xlen.W), 0.U)
  val inst_addr_misaligned = RegInit(Bool(), false.B)
  val mem_wdata = RegInit(UInt(xlen.W), 0.U)
  val csr_val = RegInit(UInt(xlen.W), 0.U)
  val expt = RegInit(Bool(), false.B)
  val interrupt = RegInit(Bool(), false.B)
  val compare = RegInit(Bool(), false.B)
  val comp_res = RegInit(Bool(), false.B)
  val af = RegInit(Bool(), false.B)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.bsrio.stall || io.bsrio.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.bsrio.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.bsrio.stall) {
    when((last_delay && delay_flush) || io.bsrio.bubble_in || io.bsrio.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_info := default_inst_info
      alu_val := 0.U
      inst_addr_misaligned := false.B
      mem_wdata := 0.U
      csr_val := 0.U
      expt := false.B
      interrupt := false.B
      compare := false.B
      comp_res := false.B
      af := false.B
    }.otherwise {
      pc := io.bsrio.pc_in
      bubble := false.B
      inst := io.instio.inst_in
      inst_info := io.iiio.inst_info_in.asUInt
      alu_val := io.aluio.alu_val_in
      inst_addr_misaligned := io.aluio.inst_addr_misaligned_in
      mem_wdata := io.aluio.mem_wdata_in
      csr_val := io.csrio.csr_val_in
      expt := io.csrio.expt_in
      interrupt := io.csrio.int_resp_in
      compare := io.csrio.compare_in
      comp_res := io.csrio.comp_res_in
      af := io.csrio.af_in
    }
  }.elsewhen(!io.bsrio.next_stage_atomic_stall_req && io.bsrio.flush_one && !io.bsrio.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_info := default_inst_info
    alu_val := 0.U
    inst_addr_misaligned := false.B
    mem_wdata := 0.U
    csr_val := 0.U
    expt := false.B
    interrupt := false.B
    compare := false.B
    comp_res := false.B
    af := false.B
  }

  io.bsrio.bubble_out := bubble
  io.bsrio.pc_out := pc
  io.instio.inst_out := inst
  io.iiio.inst_info_out := inst_info.asTypeOf(new InstInfo)
  io.aluio.alu_val_out := alu_val
  io.aluio.inst_addr_misaligned_out := inst_addr_misaligned
  io.aluio.mem_wdata_out := mem_wdata
  io.csrio.csr_val_out := csr_val
  io.csrio.expt_out := expt
  io.csrio.int_resp_out := interrupt
  io.csrio.compare_out := compare
  io.csrio.comp_res_out := comp_res
  io.csrio.af_out := af
}

class RegMem3WbIO extends Bundle with phvntomParams {
  val bsrio = Flipped(Flipped(new BasicStageRegIO))
  val instio = Flipped(Flipped(new InstIO))
  val iiio = Flipped(Flipped(new InstInfoIO))
  val aluio = Flipped(Flipped(new ExeInfoIO))
  val csrio = Flipped(Flipped(new CSRInfoIO))
  val memio = Flipped(Flipped(new MemDataIO))
}

class RegMem3Wb extends Module with phvntomParams {
  val io = IO(new RegMem3WbIO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), BUBBLE) 
  val pc = RegInit(UInt(xlen.W), 0.U)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX, amoXXX, fwdXXX, flushXXX, false.B, 0.U(5.W), 0.U(5.W), 0.U(5.W))
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits + fwdBits + flushBits + 1 + 5 + 5 + 5).W),
    default_inst_info)
  val alu_val = RegInit(UInt(xlen.W), 0.U)
  val inst_addr_misaligned = RegInit(Bool(), false.B)
  val mem_wdata = RegInit(UInt(xlen.W), 0.U)
  val csr_val = RegInit(UInt(xlen.W), 0.U)
  val expt = RegInit(Bool(), false.B)
  val interrupt = RegInit(Bool(), false.B)
  val mem_val = RegInit(UInt(xlen.W), 0.U)
  val compare = RegInit(Bool(), false.B)
  val comp_res = RegInit(Bool(), false.B)
  val af = RegInit(Bool(), false.B)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.bsrio.stall || io.bsrio.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.bsrio.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.bsrio.stall) {
    when((last_delay && delay_flush) || io.bsrio.bubble_in || io.bsrio.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_info := default_inst_info
      alu_val := 0.U
      inst_addr_misaligned := false.B
      mem_wdata := 0.U
      csr_val := 0.U
      expt := false.B
      interrupt := false.B
      mem_val := 0.U
      compare := false.B
      comp_res := false.B
      af := false.B
    }.otherwise {
      pc := io.bsrio.pc_in
      bubble := false.B
      inst := io.instio.inst_in
      inst_info := io.iiio.inst_info_in.asUInt
      alu_val := io.aluio.alu_val_in
      inst_addr_misaligned := io.aluio.inst_addr_misaligned_in
      mem_wdata := io.aluio.mem_wdata_in
      csr_val := io.csrio.csr_val_in
      expt := io.csrio.expt_in
      interrupt := io.csrio.int_resp_in
      mem_val := io.memio.mem_val_in
      compare := io.csrio.compare_in
      comp_res := io.csrio.comp_res_in
      af := io.csrio.af_in
    }
  }.elsewhen(!io.bsrio.next_stage_atomic_stall_req && io.bsrio.flush_one && !io.bsrio.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_info := default_inst_info
    alu_val := 0.U
    inst_addr_misaligned := false.B
    mem_wdata := 0.U
    csr_val := 0.U
    expt := false.B
    interrupt := false.B
    mem_val := 0.U
    compare := false.B
    comp_res := false.B
    af := false.B
  }

  io.bsrio.bubble_out := bubble
  io.bsrio.pc_out := pc
  io.instio.inst_out := inst
  io.iiio.inst_info_out := inst_info.asTypeOf(new InstInfo)
  io.aluio.alu_val_out := alu_val
  io.aluio.inst_addr_misaligned_out := inst_addr_misaligned
  io.aluio.mem_wdata_out := mem_wdata
  io.csrio.csr_val_out := csr_val
  io.csrio.expt_out := expt
  io.csrio.int_resp_out := interrupt
  io.memio.mem_val_out := mem_val
  io.csrio.compare_out := compare
  io.csrio.comp_res_out := comp_res
  io.csrio.af_out := af
}