package rv64_nstage.core

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import common.Str
import device.MemIO
import rv64_nstage.control._
import rv64_nstage.control.ControlConst._
import rv64_nstage.fu._
import rv64_nstage.register._
import rv64_nstage.tlb._
import utils._
import mem._

class DataPathIO extends Bundle with phvntomParams {
  val ctrl = Flipped(new ControlPathIO)
  val imem = Flipped(new MemIO)
  val dmem = Flipped(new MemIO)
  val immu = Flipped(new MemIO(cachiLine * cachiBlock))
  val dmmu = Flipped(new MemIO(cachiLine * cachiBlock))
  val int = Flipped(Flipped(new InterruptIO))
}

// TODO This is a 8-stage pipeline
// TODO IF1 IF2 ID EXE DTLB MEM1 MEM2 WB
class DataPath extends Module with phvntomParams {
  val io = IO(new DataPathIO)

  // TODO forwarding machanism
  // TODO flush eariler, eg flush & stall_id_exe, but stall_req_exe === false, then just flush (like br_jump in ID_EXE)
  // TODO the original michanism should be reserved,
  // TODO furthermore, if the last stage's BUBBLE signal is high, just cut off the stall-chain, thus
  // TODO the total stalling time might be reduced

  val pc_gen = Module(new PcGen)
  val bpu = Module(new BPU)
  val immu = Module(new PTWalker("immu"))
  val reg_if1_if2 = Module(new RegIf1If2)
  val reg_if2_id = Module(new RegIf2Id)
  val reg_id_exe = Module(new RegIdExe)
  val branch_cond = Module(new BrCond)
  val imm_ext = Module(new ImmExt)
  val alu = Module(new ALU)
  val multiplier = Module(new Multiplier)
  val reg_exe_dtlb = Module(new RegExeDTLB)
  val dmmu = Module(new PTWalker("dmmu"))
  val reg_dtlb_mem1 = Module(new RegDTLBMem1)
  val csr = Module(new CSR)
  val reg_mem1_mem2 = Module(new RegMem1Mem2)
  val reg_mem2_wb = Module(new RegMem2Wb)
  val reg_file = Module(new RegFile)
  val scheduler = Module(new ALUScheduler)
  val amo_arbiter = Module(new AMOArbiter)
  val reservation = Module(new Reservation)

  // Stall Request Signals
  val stall_req_if1_atomic = WireInit(Bool(), false.B)
  val stall_req_if2_atomic = WireInit(Bool(), false.B)
  val stall_req_exe_atomic = WireInit(Bool(), false.B)
  val stall_req_exe_interruptable = WireInit(Bool(), false.B)
  val stall_req_dtlb_atomic = WireInit(Bool(), false.B)
  val stall_req_mem2_atomic = WireInit(Bool(), false.B)

  // Flush Signals
  val br_jump_flush = WireInit(Bool(), false.B)
  val i_fence_flush = WireInit(Bool(), false.B)
  val s_fence_flush = WireInit(Bool(), false.B)
  val expt_int_flush = WireInit(Bool(), false.B)
  val error_ret_flush = WireInit(Bool(), false.B)
  val write_satp_flush = WireInit(Bool(), false.B)

  // Stall Signals
  val stall_pc = WireInit(Bool(), false.B)
  val stall_if1_if2 = WireInit(Bool(), false.B)
  val stall_if2_id = WireInit(Bool(), false.B)
  val stall_id_exe = WireInit(Bool(), false.B)
  val stall_exe_dtlb = WireInit(Bool(), false.B)
  val stall_dtlb_mem1 = WireInit(Bool(), false.B)
  val stall_mem1_mem2 = WireInit(Bool(), false.B)
  val stall_mem2_wb = WireInit(Bool(), false.B)

  // If1 Signals
  val inst_af = WireInit(Bool(), false.B)
  val inst_pf = WireInit(Bool(), false.B)
  val feedback_pc = WireInit(UInt(xlen.W), 0.U)
  val feedback_xored_index = WireInit(UInt(bpuEntryBits.W), 0.U)
  val feedback_is_br = WireInit(Bool(), false.B)
  val feedback_target_pc = WireInit(UInt(xlen.W), 0.U)
  val feedback_br_taken = WireInit(Bool(), false.B)

  // If2 Signals
  val inst_if2 = WireInit(UInt(32.W), BUBBLE)

  // Exe Signals
  val inst_addr_misaligned = WireInit(Bool(), false.B)
  val mem_af = WireInit(Bool(), false.B)
  val rs1 = WireInit(UInt(xlen.W), 0.U)
  val rs2 = WireInit(UInt(xlen.W), 0.U)
  val misprediction = WireInit(Bool(), false.B)
  val predict_taken_but_not = WireInit(Bool(), false.B)
  val predict_not_but_taken = WireInit(Bool(), false.B)
  val wrong_target = WireInit(Bool(), false.B)
  val predict_taken_but_not_br = WireInit(Bool(), false.B)
  val jump_flush = WireInit(Bool(), false.B)

  // Mem Signals
  val mem_addr_misaligned = WireInit(Bool(), false.B)
  val amo_bubble_insert = WireInit(Bool(), false.B)

  // TODO When AS is decided, this should be changed
  def is_legal_addr(addr: UInt): Bool = {
    //addr(addr.getWidth - 1, addr.getWidth / 2) === 0.U
    true.B
  }

  // Stall Control Logic
  stall_mem2_wb := false.B
  stall_mem1_mem2 := stall_mem2_wb || stall_req_mem2_atomic
  stall_dtlb_mem1 := stall_mem1_mem2
  stall_exe_dtlb := stall_dtlb_mem1 || stall_req_dtlb_atomic || amo_bubble_insert
  stall_id_exe := stall_exe_dtlb || stall_req_exe_interruptable || stall_req_exe_atomic
  stall_if2_id := stall_id_exe || false.B
  stall_if1_if2 := stall_if2_id || stall_req_if2_atomic
  stall_pc := stall_if1_if2 || stall_req_if1_atomic

  // PC Generator
  pc_gen.io.stall := stall_pc
  pc_gen.io.expt_int := expt_int_flush
  pc_gen.io.error_ret := error_ret_flush
  pc_gen.io.write_satp := write_satp_flush
  pc_gen.io.flush_cache_tlb := i_fence_flush || s_fence_flush
  pc_gen.io.predict_jump := bpu.io.branch_taken
  pc_gen.io.predict_jump_target := bpu.io.pc_in_btb
  pc_gen.io.epc := csr.io.epc
  pc_gen.io.tvec := csr.io.evec
  pc_gen.io.pc_plus := (reg_dtlb_mem1.io.bsrio.pc_out + 4.U(3.W))
  pc_gen.io.branch_jump := br_jump_flush
  pc_gen.io.branch_pc := Mux(predict_taken_but_not || (predict_taken_but_not_br && !jump_flush),
    (reg_id_exe.io.bsrio.pc_out + 4.U), alu.io.out)
  pc_gen.io.inst_addr_misaligned := inst_addr_misaligned

  // BPU
  bpu.io.pc_to_predict := pc_gen.io.pc_out
  bpu.io.feedback_pc := feedback_pc
  bpu.io.feedback_xored_index := feedback_xored_index
  bpu.io.feedback_is_br := feedback_is_br
  bpu.io.feedback_target_pc := feedback_target_pc
  bpu.io.feedback_br_taken := feedback_br_taken
  bpu.io.stall_update := scheduler.io.stall_req

  // IMMU
  inst_af := !is_legal_addr(pc_gen.io.pc_out) || immu.io.af // TODO
  inst_pf := immu.io.pf
  immu.io.valid := !stall_if1_if2
  immu.io.is_mem := false.B
  immu.io.force_s_mode := false.B
  immu.io.sum := 0.U
  immu.io.va := pc_gen.io.pc_out
  immu.io.flush_all := write_satp_flush || s_fence_flush
  immu.io.satp_val := csr.io.satp_val
  immu.io.current_p := csr.io.current_p
  immu.io.is_inst := true.B
  immu.io.is_load := false.B
  immu.io.is_store := false.B
  io.immu <> immu.io.mmu

  stall_req_if1_atomic := immu.io.stall_req

  reg_if1_if2.io.bsrio.stall := stall_if1_if2
  reg_if1_if2.io.bsrio.flush_one := (br_jump_flush || expt_int_flush || error_ret_flush || write_satp_flush ||
    i_fence_flush || s_fence_flush)
  reg_if1_if2.io.bsrio.bubble_in := stall_req_if1_atomic
  reg_if1_if2.io.bsrio.pc_in := pc_gen.io.pc_out
  reg_if1_if2.io.bsrio.last_stage_atomic_stall_req := stall_req_if1_atomic
  reg_if1_if2.io.bsrio.next_stage_atomic_stall_req := stall_req_if2_atomic
  reg_if1_if2.io.ifio.inst_af_in := inst_af
  reg_if1_if2.io.ifio.inst_pf_in := inst_pf
  reg_if1_if2.io.bsrio.next_stage_flush_req := false.B
  reg_if1_if2.io.bpio.predict_taken_in := bpu.io.branch_taken
  reg_if1_if2.io.bpio.target_in := bpu.io.pc_in_btb
  reg_if1_if2.io.bpio.xored_index_in := bpu.io.xored_index_out

  // TODO parallel visiting of RAM and TLB
  io.imem.flush := i_fence_flush
  io.imem.stall := stall_if1_if2
  io.imem.req.bits.addr := immu.io.pa
  io.imem.req.bits.data := DontCare
  io.imem.req.valid := !stall_req_if1_atomic
  io.imem.req.bits.wen := false.B
  io.imem.req.bits.memtype := memWordU
  io.imem.resp.ready := true.B

  inst_if2 := io.imem.resp.bits.data
  stall_req_if2_atomic := !io.imem.req.ready || !io.imem.flush_ready

  // Reg IF2 ID
  reg_if2_id.io.bsrio.last_stage_atomic_stall_req := stall_req_if2_atomic
  reg_if2_id.io.bsrio.next_stage_atomic_stall_req := false.B
  reg_if2_id.io.bsrio.stall := stall_if2_id
  reg_if2_id.io.bsrio.flush_one := br_jump_flush || expt_int_flush || error_ret_flush || write_satp_flush || i_fence_flush || s_fence_flush
  reg_if2_id.io.bsrio.bubble_in := stall_req_if2_atomic || reg_if1_if2.io.bsrio.bubble_out
  reg_if2_id.io.instio.inst_in := Mux(reg_if1_if2.io.ifio.inst_af_out, BUBBLE, inst_if2)
  reg_if2_id.io.bsrio.pc_in := reg_if1_if2.io.bsrio.pc_out
  reg_if2_id.io.ifio.inst_af_in := reg_if1_if2.io.ifio.inst_af_out
  reg_if2_id.io.bsrio.next_stage_flush_req := i_fence_flush
  reg_if2_id.io.ifio.inst_pf_in := reg_if1_if2.io.ifio.inst_pf_out
  reg_if2_id.io.bpio.predict_taken_in := reg_if1_if2.io.bpio.predict_taken_out
  reg_if2_id.io.bpio.target_in := reg_if1_if2.io.bpio.target_out
  reg_if2_id.io.bpio.xored_index_in := reg_if1_if2.io.bpio.xored_index_out

  // Decoder
  io.ctrl.inst := reg_if2_id.io.instio.inst_out

  // Reg ID EXE
  reg_id_exe.io.bsrio.last_stage_atomic_stall_req := false.B
  reg_id_exe.io.bsrio.next_stage_atomic_stall_req := stall_req_exe_atomic
  reg_id_exe.io.bsrio.stall := stall_id_exe
  reg_id_exe.io.bsrio.flush_one := br_jump_flush || expt_int_flush || error_ret_flush || write_satp_flush || i_fence_flush || s_fence_flush
  reg_id_exe.io.bsrio.bubble_in := reg_if2_id.io.bsrio.bubble_out
  reg_id_exe.io.instio.inst_in := reg_if2_id.io.instio.inst_out
  reg_id_exe.io.bsrio.pc_in := reg_if2_id.io.bsrio.pc_out
  reg_id_exe.io.iiio.inst_info_in := io.ctrl.inst_info_out
  reg_id_exe.io.ifio.inst_af_in := reg_if2_id.io.ifio.inst_af_out
  reg_id_exe.io.bsrio.next_stage_flush_req := br_jump_flush
  reg_id_exe.io.ifio.inst_pf_in := reg_if2_id.io.ifio.inst_pf_out
  reg_id_exe.io.bpio.predict_taken_in := reg_if2_id.io.bpio.predict_taken_out
  reg_id_exe.io.bpio.target_in := reg_if2_id.io.bpio.target_out
  reg_id_exe.io.bpio.xored_index_in := reg_if2_id.io.bpio.xored_index_out

  // ALU, Multipier, Branch and Jump
  imm_ext.io.inst := reg_id_exe.io.instio.inst_out
  imm_ext.io.instType := reg_id_exe.io.iiio.inst_info_out.instType

  alu.io.opType := reg_id_exe.io.iiio.inst_info_out.aluType
  alu.io.a := Mux(reg_id_exe.io.iiio.inst_info_out.ASelect === APC, reg_id_exe.io.bsrio.pc_out, rs1)
  alu.io.b := Mux(reg_id_exe.io.iiio.inst_info_out.BSelect === BIMM, imm_ext.io.out, rs2)

  multiplier.io.start := reg_id_exe.io.iiio.inst_info_out.mult && !scheduler.io.stall_req && !stall_exe_dtlb
  multiplier.io.a := rs1
  multiplier.io.b := rs2
  multiplier.io.op := reg_id_exe.io.iiio.inst_info_out.aluType

  branch_cond.io.rs1 := rs1
  branch_cond.io.rs2 := rs2
  branch_cond.io.brType := reg_id_exe.io.iiio.inst_info_out.brType

  feedback_pc := reg_id_exe.io.bsrio.pc_out
  feedback_xored_index := reg_id_exe.io.bpio.xored_index_out
  feedback_is_br := (reg_id_exe.io.iiio.inst_info_out.brType.orR || jump_flush) && !scheduler.io.stall_req
  feedback_target_pc := alu.io.out
  feedback_br_taken := branch_cond.io.branch || jump_flush

  predict_taken_but_not_br := (!reg_id_exe.io.iiio.inst_info_out.brType.orR &&
    reg_id_exe.io.iiio.inst_info_out.pcSelect =/= pcJump && reg_id_exe.io.bpio.predict_taken_out)
  predict_not_but_taken := branch_cond.io.branch && !reg_id_exe.io.bpio.predict_taken_out
  predict_taken_but_not := (!branch_cond.io.branch && reg_id_exe.io.bpio.predict_taken_out &&
    reg_id_exe.io.iiio.inst_info_out.brType.orR)
  misprediction := predict_not_but_taken || predict_taken_but_not
  wrong_target := branch_cond.io.branch && reg_id_exe.io.bpio.predict_taken_out && alu.io.out =/= reg_id_exe.io.bpio.target_out
  jump_flush := reg_id_exe.io.iiio.inst_info_out.pcSelect === pcJump && (!reg_id_exe.io.bpio.predict_taken_out ||
    alu.io.out =/= reg_id_exe.io.bpio.target_out)
  br_jump_flush := ((misprediction || wrong_target || predict_taken_but_not_br ||
    jump_flush) && !scheduler.io.stall_req)
  inst_addr_misaligned := alu.io.out(1) && (reg_id_exe.io.iiio.inst_info_out.pcSelect === pcJump || branch_cond.io.branch)

  scheduler.io.is_bubble := reg_id_exe.io.bsrio.bubble_out
  scheduler.io.rs1_used_exe := (reg_id_exe.io.iiio.inst_info_out.ASelect === ARS1 ||
    reg_id_exe.io.iiio.inst_info_out.pcSelect === pcBranch) && reg_id_exe.io.instio.inst_out(19, 15).orR
  scheduler.io.rs1_addr_exe := reg_id_exe.io.instio.inst_out(19, 15)
  scheduler.io.rs2_used_exe := (reg_id_exe.io.iiio.inst_info_out.BSelect === BXXX ||
    reg_id_exe.io.iiio.inst_info_out.amoSelect =/= amoXXX ||
    reg_id_exe.io.iiio.inst_info_out.pcSelect === pcBranch ||
    reg_id_exe.io.iiio.inst_info_out.wbEnable === wenMem) && reg_id_exe.io.instio.inst_out(24, 20).orR
  scheduler.io.rs2_addr_exe := reg_id_exe.io.instio.inst_out(24, 20)
  scheduler.io.rd_used_dtlb := (reg_exe_dtlb.io.iiio.inst_info_out.wbEnable === wenReg ||
    reg_exe_dtlb.io.iiio.inst_info_out.wbEnable === wenCSRC ||
    reg_exe_dtlb.io.iiio.inst_info_out.wbEnable === wenCSRS ||
    reg_exe_dtlb.io.iiio.inst_info_out.wbEnable === wenCSRW ||
    reg_exe_dtlb.io.iiio.inst_info_out.wbEnable === wenRes ||
    reg_exe_dtlb.io.iiio.inst_info_out.wbSelect === wbCond)
  scheduler.io.rd_addr_dtlb := reg_exe_dtlb.io.instio.inst_out(11, 7)
  scheduler.io.rd_used_mem1 := (reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenReg ||
    reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenCSRC ||
    reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenCSRS ||
    reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenCSRW ||
    reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenRes ||
    reg_dtlb_mem1.io.iiio.inst_info_out.wbSelect === wbCond)
  scheduler.io.rd_addr_mem1 := reg_dtlb_mem1.io.instio.inst_out(11, 7)
  scheduler.io.rd_used_mem2 := (reg_mem1_mem2.io.iiio.inst_info_out.wbEnable === wenReg ||
    reg_mem1_mem2.io.iiio.inst_info_out.wbEnable === wenCSRC ||
    reg_mem1_mem2.io.iiio.inst_info_out.wbEnable === wenCSRS ||
    reg_mem1_mem2.io.iiio.inst_info_out.wbEnable === wenCSRW ||
    reg_mem1_mem2.io.iiio.inst_info_out.wbEnable === wenRes ||
    reg_mem1_mem2.io.iiio.inst_info_out.wbSelect === wbCond)
  scheduler.io.rd_addr_mem2 := reg_mem1_mem2.io.instio.inst_out(11, 7)
  scheduler.io.rd_used_wb := (reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenReg ||
    reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenCSRC ||
    reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenCSRS ||
    reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenCSRW ||
    reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenRes ||
    reg_mem2_wb.io.iiio.inst_info_out.wbSelect === wbCond)
  scheduler.io.rd_addr_wb := reg_mem2_wb.io.instio.inst_out(11, 7)
  scheduler.io.rs1_from_reg := reg_file.io.rs1_data
  scheduler.io.rs2_from_reg := reg_file.io.rs2_data
  scheduler.io.rd_fen_from_dtlb := reg_exe_dtlb.io.iiio.inst_info_out.fwd_stage <= fwdMem1
  scheduler.io.rd_from_dtlb := MuxLookup(
    reg_exe_dtlb.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_exe_dtlb.io.aluio.alu_val_out,
      wbPC -> (reg_exe_dtlb.io.bsrio.pc_out + 4.U)
    )
  )
  scheduler.io.rd_fen_from_mem1 := reg_dtlb_mem1.io.iiio.inst_info_out.fwd_stage <= fwdMem1
  scheduler.io.rd_from_mem1 := MuxLookup(
    reg_dtlb_mem1.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_dtlb_mem1.io.aluio.alu_val_out,
      wbPC -> (reg_dtlb_mem1.io.bsrio.pc_out + 4.U)
    )
  )
  scheduler.io.rd_fen_from_mem2 := reg_mem1_mem2.io.iiio.inst_info_out.fwd_stage <= fwdMem2
  scheduler.io.rd_from_mem2 := MuxLookup(
    reg_mem1_mem2.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_mem1_mem2.io.aluio.alu_val_out,
      wbCSR -> reg_mem1_mem2.io.csrio.csr_val_out,
      wbPC -> (reg_mem1_mem2.io.bsrio.pc_out + 4.U)
    )
  )
  scheduler.io.rd_fen_from_wb := reg_mem2_wb.io.iiio.inst_info_out.fwd_stage <= fwdWb
  scheduler.io.rd_from_wb := MuxLookup(
    reg_mem2_wb.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_mem2_wb.io.aluio.alu_val_out,
      wbMEM -> reg_mem2_wb.io.memio.mem_val_out,
      wbCSR -> reg_mem2_wb.io.csrio.csr_val_out,
      wbCond -> reg_mem2_wb.io.memio.mem_val_out,
      wbPC -> (reg_mem2_wb.io.bsrio.pc_out + 4.U)
    )
  )

  stall_req_exe_atomic := multiplier.io.stall_req
  stall_req_exe_interruptable := scheduler.io.stall_req
  rs1 := scheduler.io.rs1_val
  rs2 := scheduler.io.rs2_val

  // Reg EXE DTLB
  reg_exe_dtlb.io.bsrio.last_stage_atomic_stall_req := stall_req_exe_atomic
  reg_exe_dtlb.io.bsrio.next_stage_atomic_stall_req := stall_req_dtlb_atomic
  reg_exe_dtlb.io.bsrio.stall := stall_exe_dtlb
  reg_exe_dtlb.io.bsrio.flush_one := expt_int_flush || error_ret_flush || write_satp_flush || i_fence_flush || s_fence_flush
  reg_exe_dtlb.io.bsrio.bubble_in := (reg_id_exe.io.bsrio.bubble_out || stall_req_exe_atomic ||
    stall_req_exe_interruptable)
  reg_exe_dtlb.io.instio.inst_in := reg_id_exe.io.instio.inst_out
  reg_exe_dtlb.io.bsrio.pc_in := reg_id_exe.io.bsrio.pc_out
  reg_exe_dtlb.io.iiio.inst_info_in := reg_id_exe.io.iiio.inst_info_out
  reg_exe_dtlb.io.aluio.inst_addr_misaligned_in := inst_addr_misaligned
  reg_exe_dtlb.io.aluio.alu_val_in := Mux(reg_id_exe.io.iiio.inst_info_out.mult, multiplier.io.mult_out, alu.io.out)
  reg_exe_dtlb.io.aluio.mem_wdata_in := rs2
  reg_exe_dtlb.io.ifio.inst_af_in := reg_id_exe.io.ifio.inst_af_out
  reg_exe_dtlb.io.bsrio.next_stage_flush_req := false.B
  reg_exe_dtlb.io.ifio.inst_pf_in := reg_id_exe.io.ifio.inst_pf_out

  // DMMU
  mem_af := dmmu.io.af || (!is_legal_addr(reg_exe_dtlb.io.aluio.alu_val_out) &&
    reg_exe_dtlb.io.iiio.inst_info_out.memType.orR) // TODO
  dmmu.io.valid := reg_exe_dtlb.io.iiio.inst_info_out.memType.orR && !stall_dtlb_mem1
  dmmu.io.is_mem := true.B
  dmmu.io.force_s_mode := csr.io.force_s_mode_mem
  dmmu.io.sum := csr.io.mstatus_sum
  dmmu.io.va := reg_exe_dtlb.io.aluio.alu_val_out
  dmmu.io.flush_all := write_satp_flush || s_fence_flush
  dmmu.io.satp_val := csr.io.satp_val
  dmmu.io.current_p := csr.io.current_p
  dmmu.io.is_inst := false.B
  dmmu.io.is_load := reg_exe_dtlb.io.iiio.inst_info_out.memType.orR && reg_exe_dtlb.io.iiio.inst_info_out.wbSelect === wbMEM
  dmmu.io.is_store := reg_exe_dtlb.io.iiio.inst_info_out.memType.orR && reg_exe_dtlb.io.iiio.inst_info_out.wbSelect =/= wbMEM
  io.dmmu <> dmmu.io.mmu

  stall_req_dtlb_atomic := dmmu.io.stall_req

  // Reg DTLB MEM1
  reg_dtlb_mem1.io.bsrio.last_stage_atomic_stall_req := stall_req_dtlb_atomic
  reg_dtlb_mem1.io.bsrio.next_stage_atomic_stall_req := false.B
  reg_dtlb_mem1.io.bsrio.stall := stall_dtlb_mem1
  reg_dtlb_mem1.io.bsrio.flush_one := expt_int_flush || error_ret_flush || write_satp_flush || i_fence_flush || s_fence_flush
  reg_dtlb_mem1.io.bsrio.bubble_in := (reg_exe_dtlb.io.bsrio.bubble_out || stall_req_dtlb_atomic || amo_bubble_insert)
  reg_dtlb_mem1.io.instio.inst_in := reg_exe_dtlb.io.instio.inst_out
  reg_dtlb_mem1.io.bsrio.pc_in := reg_exe_dtlb.io.bsrio.pc_out
  reg_dtlb_mem1.io.iiio.inst_info_in := reg_exe_dtlb.io.iiio.inst_info_out
  reg_dtlb_mem1.io.aluio.inst_addr_misaligned_in := reg_exe_dtlb.io.aluio.inst_addr_misaligned_out
  reg_dtlb_mem1.io.aluio.alu_val_in := Mux(reg_exe_dtlb.io.iiio.inst_info_out.memType.orR, dmmu.io.pa, reg_exe_dtlb.io.aluio.alu_val_out)
  reg_dtlb_mem1.io.aluio.mem_wdata_in := reg_exe_dtlb.io.aluio.mem_wdata_out
  reg_dtlb_mem1.io.intio.timer_int_in := io.int.mtip
  reg_dtlb_mem1.io.intio.software_int_in := io.int.msip
  reg_dtlb_mem1.io.intio.external_int_in := io.int.meip
  reg_dtlb_mem1.io.intio.s_external_int_in := io.int.seip
  reg_dtlb_mem1.io.ifio.inst_af_in := reg_exe_dtlb.io.ifio.inst_af_out
  reg_dtlb_mem1.io.bsrio.next_stage_flush_req := expt_int_flush || error_ret_flush || write_satp_flush || i_fence_flush || s_fence_flush
  reg_dtlb_mem1.io.ifio.inst_pf_in := reg_exe_dtlb.io.ifio.inst_pf_out
  reg_dtlb_mem1.io.intio.mem_af_in := mem_af
  reg_dtlb_mem1.io.intio.mem_pf_in := dmmu.io.pf

  amo_bubble_insert := reg_dtlb_mem1.io.iiio.inst_info_out.amoSelect.orR

  // CSR
  mem_addr_misaligned := MuxLookup(reg_dtlb_mem1.io.iiio.inst_info_out.memType,
    false.B, Seq(
      memByte -> false.B,
      memByteU -> false.B,
      memHalf -> reg_dtlb_mem1.io.aluio.alu_val_out(0),
      memHalfU -> reg_dtlb_mem1.io.aluio.alu_val_out(0),
      memWord -> reg_dtlb_mem1.io.aluio.alu_val_out(1, 0).orR,
      memWordU -> reg_dtlb_mem1.io.aluio.alu_val_out(1, 0).orR,
      memDouble -> reg_dtlb_mem1.io.aluio.alu_val_out(2, 0).orR
    ))

//  printf("ext_int %x, csr_resp %x", io.int.meip, csr.io.int)

  csr.io.stall := stall_dtlb_mem1
  csr.io.cmd := reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable
  csr.io.in := reg_dtlb_mem1.io.aluio.alu_val_out
  csr.io.bubble := reg_dtlb_mem1.io.bsrio.bubble_out
  csr.io.pc := reg_dtlb_mem1.io.bsrio.pc_out
  csr.io.illegal_mem_addr := mem_addr_misaligned
  csr.io.illegal_inst_addr := reg_dtlb_mem1.io.aluio.inst_addr_misaligned_out
  csr.io.inst := reg_dtlb_mem1.io.instio.inst_out
  csr.io.illegal := reg_dtlb_mem1.io.iiio.inst_info_out.instType === Illegal
  csr.io.is_load := (reg_dtlb_mem1.io.iiio.inst_info_out.memType.orR &&
    reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable =/= wenMem)
  csr.io.is_store := (reg_dtlb_mem1.io.iiio.inst_info_out.memType.orR &&
    reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenMem)
  csr.io.inst_access_fault := reg_dtlb_mem1.io.ifio.inst_af_out
  csr.io.inst_page_fault := reg_dtlb_mem1.io.ifio.inst_pf_out
  csr.io.mem_access_fault := reg_dtlb_mem1.io.intio.mem_af_out
  csr.io.mem_page_fault := reg_dtlb_mem1.io.intio.mem_pf_out
  csr.io.tim_int := reg_dtlb_mem1.io.intio.timer_int_out
  csr.io.soft_int := reg_dtlb_mem1.io.intio.software_int_out
  csr.io.external_int := reg_dtlb_mem1.io.intio.external_int_out
  csr.io.s_external_int := reg_dtlb_mem1.io.intio.s_external_int_out

  expt_int_flush := csr.io.expt
  error_ret_flush := csr.io.ret
  write_satp_flush := csr.io.write_satp
  i_fence_flush := (reg_dtlb_mem1.io.iiio.inst_info_out.flushType === flushI ||
    reg_dtlb_mem1.io.iiio.inst_info_out.flushType === flushAll) && !expt_int_flush
  s_fence_flush := (reg_dtlb_mem1.io.iiio.inst_info_out.flushType === flushAll ||
    reg_dtlb_mem1.io.iiio.inst_info_out.flushType === flushTLB) && !expt_int_flush

  // TODO af is ONLY for Difftest
  // REG MEM1 MEM2
  reg_mem1_mem2.io.bsrio.last_stage_atomic_stall_req := false.B
  reg_mem1_mem2.io.bsrio.next_stage_atomic_stall_req := stall_req_mem2_atomic
  reg_mem1_mem2.io.bsrio.stall := stall_mem1_mem2
  reg_mem1_mem2.io.bsrio.flush_one := false.B
  reg_mem1_mem2.io.bsrio.bubble_in := reg_dtlb_mem1.io.bsrio.bubble_out
  reg_mem1_mem2.io.instio.inst_in := reg_dtlb_mem1.io.instio.inst_out
  reg_mem1_mem2.io.bsrio.pc_in := reg_dtlb_mem1.io.bsrio.pc_out
  reg_mem1_mem2.io.iiio.inst_info_in := reg_dtlb_mem1.io.iiio.inst_info_out
  reg_mem1_mem2.io.aluio.inst_addr_misaligned_in := reg_dtlb_mem1.io.aluio.inst_addr_misaligned_out
  reg_mem1_mem2.io.aluio.alu_val_in := reg_dtlb_mem1.io.aluio.alu_val_out
  reg_mem1_mem2.io.aluio.mem_wdata_in := reg_dtlb_mem1.io.aluio.mem_wdata_out
  reg_mem1_mem2.io.csrio.expt_in := csr.io.expt
  reg_mem1_mem2.io.csrio.int_resp_in := csr.io.int
  reg_mem1_mem2.io.csrio.csr_val_in := csr.io.out
  reg_mem1_mem2.io.bsrio.next_stage_flush_req := false.B
  reg_mem1_mem2.io.csrio.compare_in := reservation.io.compare
  reg_mem1_mem2.io.csrio.comp_res_in := (!reservation.io.succeed).asUInt
  reg_mem1_mem2.io.csrio.af_in := csr.io.expt && csr.io.inst_access_fault

  io.dmem.flush := false.B
  io.dmem.stall := !io.dmem.req.ready
  io.dmem.req.bits.addr := Mux(amo_arbiter.io.write_now, reg_mem1_mem2.io.aluio.alu_val_out, reg_dtlb_mem1.io.aluio.alu_val_out)
  io.dmem.req.bits.data := Mux(amo_arbiter.io.write_now, amo_arbiter.io.write_what, reg_dtlb_mem1.io.aluio.mem_wdata_out)
  io.dmem.req.valid := Mux(reservation.io.compare, reservation.io.succeed,
    (!csr.io.expt && ((reg_dtlb_mem1.io.iiio.inst_info_out.memType.orR &&
      !amo_arbiter.io.dont_read_again) ||
      amo_arbiter.io.write_now)))
  io.dmem.req.bits.wen := (reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenMem || amo_arbiter.io.write_now)
  io.dmem.req.bits.memtype := Mux(amo_arbiter.io.write_now, reg_mem1_mem2.io.iiio.inst_info_out.memType, reg_dtlb_mem1.io.iiio.inst_info_out.memType)
  io.dmem.resp.ready := true.B

  stall_req_mem2_atomic := !io.dmem.req.ready || amo_arbiter.io.stall_req

  amo_arbiter.io.early_amo_op := reg_dtlb_mem1.io.iiio.inst_info_out.amoSelect
  amo_arbiter.io.exception_or_int := reg_mem1_mem2.io.csrio.expt_out
  amo_arbiter.io.amo_op := reg_mem1_mem2.io.iiio.inst_info_out.amoSelect
  amo_arbiter.io.dmem_valid := io.dmem.resp.valid
  amo_arbiter.io.dmem_data := io.dmem.resp.bits.data
  amo_arbiter.io.reg_val := reg_mem1_mem2.io.aluio.mem_wdata_out
  amo_arbiter.io.mem_type := reg_mem1_mem2.io.iiio.inst_info_out.memType

  reservation.io.push := reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenRes
  reservation.io.push_is_word := reg_dtlb_mem1.io.iiio.inst_info_out.memType === memWord
  reservation.io.push_addr := reg_dtlb_mem1.io.aluio.alu_val_out
  reservation.io.compare := reg_dtlb_mem1.io.iiio.inst_info_out.wbSelect === wbCond
  reservation.io.compare_is_word := reg_dtlb_mem1.io.iiio.inst_info_out.memType === memWord
  reservation.io.compare_addr := reg_dtlb_mem1.io.aluio.alu_val_out
  reservation.io.flush := false.B

  // Reg MEM2 WB
  reg_mem2_wb.io.bsrio.last_stage_atomic_stall_req := stall_req_mem2_atomic
  reg_mem2_wb.io.bsrio.next_stage_atomic_stall_req := false.B
  reg_mem2_wb.io.bsrio.stall := stall_mem2_wb
  reg_mem2_wb.io.bsrio.flush_one := false.B
  reg_mem2_wb.io.bsrio.bubble_in := reg_mem1_mem2.io.bsrio.bubble_out || stall_req_mem2_atomic
  reg_mem2_wb.io.instio.inst_in := reg_mem1_mem2.io.instio.inst_out
  reg_mem2_wb.io.bsrio.pc_in := reg_mem1_mem2.io.bsrio.pc_out
  reg_mem2_wb.io.iiio.inst_info_in := reg_mem1_mem2.io.iiio.inst_info_out
  reg_mem2_wb.io.aluio.inst_addr_misaligned_in := reg_mem1_mem2.io.aluio.inst_addr_misaligned_out
  reg_mem2_wb.io.aluio.alu_val_in := reg_mem1_mem2.io.aluio.alu_val_out
  reg_mem2_wb.io.aluio.mem_wdata_in := reg_mem1_mem2.io.aluio.mem_wdata_out
  reg_mem2_wb.io.csrio.expt_in := reg_mem1_mem2.io.csrio.expt_out
  reg_mem2_wb.io.csrio.int_resp_in := reg_mem1_mem2.io.csrio.int_resp_out
  reg_mem2_wb.io.csrio.csr_val_in := reg_mem1_mem2.io.csrio.csr_val_out
  reg_mem2_wb.io.memio.mem_val_in := Mux(amo_arbiter.io.force_mem_val_out, amo_arbiter.io.mem_val_out,
    Mux(reg_mem1_mem2.io.csrio.compare_out, reg_mem1_mem2.io.csrio.comp_res_out, io.dmem.resp.bits.data))
  reg_mem2_wb.io.bsrio.next_stage_flush_req := false.B
  reg_mem2_wb.io.csrio.compare_in := reg_mem1_mem2.io.csrio.compare_out
  reg_mem2_wb.io.csrio.comp_res_in := reg_mem1_mem2.io.csrio.comp_res_out
  reg_mem2_wb.io.csrio.af_in := reg_mem1_mem2.io.csrio.af_out

  // Register File
  reg_file.io.wen := (reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenReg ||
    reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenCSRW ||
    reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenCSRC ||
    reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenCSRS ||
    reg_mem2_wb.io.iiio.inst_info_out.wbEnable === wenRes ||
    reg_mem2_wb.io.iiio.inst_info_out.wbSelect === wbCond) && reg_mem2_wb.io.csrio.expt_out === false.B
  reg_file.io.rd_addr := reg_mem2_wb.io.instio.inst_out(11, 7)
  reg_file.io.rd_data := MuxLookup(reg_mem2_wb.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U, Seq(
      wbALU -> reg_mem2_wb.io.aluio.alu_val_out,
      wbMEM -> reg_mem2_wb.io.memio.mem_val_out,
      wbPC -> (reg_mem2_wb.io.bsrio.pc_out + 4.U),
      wbCSR -> reg_mem2_wb.io.csrio.csr_val_out,
      wbCond -> reg_mem2_wb.io.memio.mem_val_out
    )
  )
  reg_file.io.rs1_addr := reg_id_exe.io.instio.inst_out(19, 15)
  reg_file.io.rs2_addr := reg_id_exe.io.instio.inst_out(24, 20)

  // TODO from here is difftest, which is not formally included in the CPU
  // TODO Difftest
  // TODO Don't care the low-end code style
  if (diffTest) {
    val dtest_pc = RegInit(UInt(xlen.W), 0.U)
    val dtest_inst = RegInit(UInt(xlen.W), BUBBLE)
    val dtest_wbvalid = RegInit(Bool(), false.B)
    val dtest_expt = RegInit(false.B)
    val dtest_int = RegInit(false.B)

    dtest_wbvalid := !reg_mem2_wb.io.bsrio.bubble_out && !reg_mem2_wb.io.csrio.int_resp_out

    when(!stall_mem2_wb) {
      dtest_pc := Mux(reg_mem2_wb.io.csrio.af_out || reg_mem2_wb.io.bsrio.bubble_out || reg_mem2_wb.io.csrio.int_resp_out, dtest_pc, reg_mem2_wb.io.bsrio.pc_out)
      dtest_inst := reg_mem2_wb.io.instio.inst_out
      dtest_expt := reg_mem2_wb.io.csrio.int_resp_out
    }
    dtest_int := reg_mem2_wb.io.csrio.int_resp_out // dtest_expt & (io.int.msip | io.int.mtip)

    BoringUtils.addSource(dtest_pc, "difftestPC")
    BoringUtils.addSource(dtest_inst, "difftestInst")
    BoringUtils.addSource(dtest_wbvalid, "difftestValid")
    BoringUtils.addSource(dtest_int, "difftestInt")

    if (pipeTrace) {
      printf("\t\tIF1\tIF2\tID\tEXE\tDTLB\tMEM1\tMEM2\tWB\t\n")
      printf("Stall Req\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", stall_req_if1_atomic, stall_req_if2_atomic, 0.U, stall_req_exe_atomic || stall_req_exe_interruptable, stall_req_dtlb_atomic, 0.U, stall_req_mem2_atomic, 0.U)
      printf("Stall\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", stall_pc, stall_if1_if2, stall_if2_id, stall_id_exe, stall_exe_dtlb, stall_dtlb_mem1, stall_mem1_mem2, stall_mem2_wb)
      printf("PC\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", pc_gen.io.pc_out(15, 0), reg_if1_if2.io.bsrio.pc_out(15, 0), reg_if2_id.io.bsrio.pc_out(15, 0), reg_id_exe.io.bsrio.pc_out(15, 0), reg_exe_dtlb.io.bsrio.pc_out(15, 0), reg_dtlb_mem1.io.bsrio.pc_out(15, 0), reg_mem1_mem2.io.bsrio.pc_out(15, 0), reg_mem2_wb.io.bsrio.pc_out(15, 0))
      printf("Inst\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", BUBBLE(15, 0), io.imem.resp.bits.data(15, 0), reg_if2_id.io.instio.inst_out(15, 0), reg_id_exe.io.instio.inst_out(15, 0), reg_exe_dtlb.io.instio.inst_out(15, 0), reg_dtlb_mem1.io.instio.inst_out(15, 0), reg_mem1_mem2.io.instio.inst_out(15, 0), reg_mem2_wb.io.instio.inst_out(15, 0))
      printf("AluO\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", 0.U, 0.U, 0.U, alu.io.out(15, 0), reg_exe_dtlb.io.aluio.alu_val_out(15, 0), reg_dtlb_mem1.io.aluio.alu_val_out(15, 0), reg_mem1_mem2.io.aluio.alu_val_out(15, 0), reg_mem2_wb.io.aluio.alu_val_out(15, 0))
      printf("MemO\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, io.dmem.resp.bits.data(15, 0), reg_mem2_wb.io.memio.mem_val_out(15, 0))
      printf("Bubb\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", 0.U, reg_if1_if2.io.bsrio.bubble_out, reg_if2_id.io.bsrio.bubble_out, reg_id_exe.io.bsrio.bubble_out, reg_exe_dtlb.io.bsrio.bubble_out, reg_dtlb_mem1.io.bsrio.bubble_out, reg_mem1_mem2.io.bsrio.bubble_out, reg_mem2_wb.io.bsrio.bubble_out)
      printf("Take\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", bpu.io.branch_taken, reg_if1_if2.io.bpio.predict_taken_out, reg_if2_id.io.bpio.predict_taken_out, reg_id_exe.io.bpio.predict_taken_out, 0.U, 0.U, 0.U, 0.U)
      printf("Tar\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", bpu.io.pc_in_btb(15, 0), reg_if1_if2.io.bpio.target_out(15, 0), reg_if2_id.io.bpio.target_out(15, 0), reg_id_exe.io.bpio.target_out(15, 0), 0.U, 0.U, 0.U, 0.U)
      printf("\n")
    }

    //    printf("------> compare %x, succeed %x, push %x\n", reservation.io.compare, reservation.io.succeed, reservation.io.push)

    //    printf("-------> exit flush %x, br_flush %x, pco %x, if_pco %x, \n", expt_int_flush, br_jump_flush, pc_gen.io.pc_out, reg_if2_id.io.pc_out)
    //    printf("-----> Mem req valid %x addr %x, resp valid %x data %x\n", io.dmem.req.valid, io.dmem.req.bits.addr, io.dmem.resp.valid, io.dmem.resp.bits.data)
    //    printf("<AMO-------regm1m2stall %x; \n", stall_mem1_mem2)
    //    printf("----->instmis %x; aluout %x, pcselect %x\n", inst_addr_misaligned, alu.io.out, reg_id_exe.io.inst_info_out.pcSelect)
    when(pipeTrace.B && dtest_expt) {
      // printf("[[[[[EXPT_OR_INTRESP %d,   INT_REQ %d]]]]]\n", dtest_expt, dtest_int);
    }

    // printf("Interrupt if %x exe: %x wb %x [EPC]] %x!\n", if_mtip, exe_mtip, wb_mtip, csrFile.io.epc);
    when(dtest_int) {
      // printf("Interrupt mtvec: %x stall_req %x!\n", csrFile.io.evec, csrFile.io.stall_req);
    }
    //    printf("------->stall_req %x, imenreq_valid %x, imem_pc %x, csr_out %x, dmemaddr %x!\n", csrFile.io.stall_req, io.imem.req.valid, if_pc, csrFile.io.out, io.dmem.req.bits.addr)


  }
}
