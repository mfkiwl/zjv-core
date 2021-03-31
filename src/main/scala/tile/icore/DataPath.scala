package tile.icore

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import config.projectConfig
import tile.phvntomParams
import tile.common.control.ControlConst._
import tile.common.control._
import tile.common.rf._
import tile.common.fu._
import tile.common.mmu._
import tile.icore.rf._
import tile.icore.control._
import device.MemIO
import qarma64.QarmaEngine

class DataPathIO extends Bundle with phvntomParams {
  val ctrl = Flipped(new ControlPathIO)
  val imem = Flipped(new MemIO)
  val dmem = Flipped(new MemIO)
  val immu = Flipped(new MemIO(cachiLine * cachiBlock))
  val dmmu = Flipped(new MemIO(cachiLine * cachiBlock))
  val int = Flipped(Flipped(new InterruptIO))
}

// This is a 10-stage pipeline
// IF1 IF2 IF3 ID EXE DTLB MEM1 MEM2 MEM3 WB
// TODO 1. Change C Decoder ---- Done
// TODO 2. Change ImmExt and Datapath to support new inst-info bundle ---- Done
// TODO 3. Use ID to detect C instruction to flush former stages and reset PC to be (REG_IF3_ID.PC + 2.U) ---- Done
// TODO 4. Add 2 shadow bytes in every cacheline in I$ (These 2 stratigies guarantees C will be mostly dealt in frontend) ---- Done
// TODO 5. Change CSR to support writable MISA register ---- Done
// TODO 6. Instruction in 2 pages support ---- Done
// TODO 7. Not necessary, BPU support largely improves peformance
// TODO 8. Not necessary, update Shadow Bytes too
class DataPath extends Module with phvntomParams with projectConfig {
  val io = IO(new DataPathIO)

  val pc_gen = Module(new PcGen)
  val bpu = Module(new BPU)
  val immu = Module(new MMU()(MMUConfig(name = "immu", isdmmu = false)))
  val reg_if1_if2 = Module(new RegIf1If2)
  val reg_if2_if3 = Module(new RegIf1If2)
  val reg_if3_id = Module(new RegIf3Id)
  val dp_arbiter = Module(new DoublePageArbiter)
  val reg_id_exe = Module(new RegIdExe)
  val branch_cond = Module(new BrCond)
  val imm_ext = Module(new ImmExt)
  val alu = Module(new ALU)
  val multiplier = Module(new Multiplier)
  val reg_exe_dtlb = Module(new RegExeDTLB)
  val dmmu = Module(new MMU()(MMUConfig(name = "dmmu", isdmmu = true)))
  val reg_dtlb_mem1 = Module(new RegDTLBMem1)
  val csr = Module(new CSR)
  val reg_mem1_mem2 = Module(new RegMem1Mem2)
  val reg_mem2_mem3 = Module(new RegMem1Mem2)
  val reg_mem3_wb = Module(new RegMem3Wb)
  val reg_file = Module(new RegFile)
  val scheduler = Module(new ALUScheduler)
  val amo_arbiter = Module(new AMOArbiter)
  val reservation = Module(new Reservation)
  val pec_engine = if (enable_pec) Module(new QarmaEngine(pec_enable_ppl, static_round, pec_round)) else null

  // Stall Request Signals
  val stall_req_if1_atomic = WireInit(Bool(), false.B)
  val stall_req_if3_atomic = WireInit(Bool(), false.B)
  val stall_req_exe_atomic = WireInit(Bool(), false.B)
  val stall_req_exe_interruptable = WireInit(Bool(), false.B)
  val stall_req_dtlb_atomic = WireInit(Bool(), false.B)
  val stall_req_mem3_atomic = WireInit(Bool(), false.B)

  // Flush Signals
  val compr_flush = WireInit(Bool(), false.B)
  val br_jump_flush = WireInit(Bool(), false.B)
  val i_fence_flush = WireInit(Bool(), false.B)
  val s_fence_flush = WireInit(Bool(), false.B)
  val expt_int_flush = WireInit(Bool(), false.B)
  val error_ret_flush = WireInit(Bool(), false.B)
  val write_satp_flush = WireInit(Bool(), false.B)

  // Stall Signals
  val stall_pc = WireInit(Bool(), false.B)
  val stall_if1_if2 = WireInit(Bool(), false.B)
  val stall_if2_if3 = WireInit(Bool(), false.B)
  val stall_if3_id = WireInit(Bool(), false.B)
  val stall_id_exe = WireInit(Bool(), false.B)
  val stall_exe_dtlb = WireInit(Bool(), false.B)
  val stall_dtlb_mem1 = WireInit(Bool(), false.B)
  val stall_mem1_mem2 = WireInit(Bool(), false.B)
  val stall_mem2_mem3 = WireInit(Bool(), false.B)
  val stall_mem3_wb = WireInit(Bool(), false.B)

  // If1 Signals
  val inst_af = WireInit(Bool(), false.B)
  val inst_pf = WireInit(Bool(), false.B)
  val feedback_pc = WireInit(UInt(xlen.W), 0.U)
  val feedback_is_br = WireInit(Bool(), false.B)
  val feedback_target_pc = WireInit(UInt(xlen.W), 0.U)
  val feedback_br_taken = WireInit(Bool(), false.B)
  val is_immu_idle = WireInit(Bool(), true.B)
  val is_immu_idle_last = RegInit(Bool(), true.B)
  val immu_delay_flush_signal = RegInit(Bool(), false.B)
  val immu_flush = WireInit(Bool(), false.B)

  // If2 Signals
  val inst_if3 = WireInit(UInt(32.W), BUBBLE)
  val compr_flush_addr = WireInit(UInt(xlen.W), startAddr.asUInt)

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

  // DTLB Signals
  val is_dmmu_idle = WireInit(Bool(), true.B)
  val is_dmmu_idle_last = RegInit(Bool(), true.B)
  val dmmu_delay_flush_signal = RegInit(Bool(), false.B)
  val dmmu_flush = WireInit(Bool(), false.B)

  // ICACHE SIGNAL
  val is_icache_idle = WireInit(Bool(), true.B)
  val is_icache_idle_last = RegInit(Bool(), true.B)
  val icache_delay_flush_signal = RegInit(Bool(), false.B)
  val icache_flush = WireInit(Bool(), false.B)

  // Mem Signals
  val mem_addr_misaligned = WireInit(Bool(), false.B)
  val amo_bubble_insert = WireInit(Bool(), false.B)

  // Flush
  val csr_flush = expt_int_flush || error_ret_flush || write_satp_flush || i_fence_flush || s_fence_flush

  // TODO When AS is decided, this should be changed
  def is_legal_addr(addr: UInt): Bool = {
    //addr(addr.getWidth - 1, addr.getWidth / 2) === 0.U
    true.B
  }

  // Stall Control Logic
  stall_mem3_wb := false.B
  stall_mem2_mem3 := stall_mem3_wb || stall_req_mem3_atomic
  stall_mem1_mem2 := stall_mem2_mem3 || false.B
  stall_dtlb_mem1 := stall_mem1_mem2
  stall_exe_dtlb := stall_dtlb_mem1 || stall_req_dtlb_atomic || amo_bubble_insert
  stall_id_exe := stall_exe_dtlb || stall_req_exe_interruptable || stall_req_exe_atomic
  stall_if3_id := stall_id_exe || false.B
  stall_if2_if3 := stall_if3_id || stall_req_if3_atomic
  stall_if1_if2 := stall_if2_if3 || false.B
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
  pc_gen.io.pc_plus := reg_dtlb_mem1.io.bsrio.pc_out + Mux(reg_dtlb_mem1.io.instio.inst_out(1, 0).andR, 4.U(3.W), 2.U(3.W))
  pc_gen.io.branch_jump := br_jump_flush
  pc_gen.io.branch_pc := reg_exe_dtlb.io.bjio.bjpc_out
  pc_gen.io.inst_addr_misaligned := reg_exe_dtlb.io.aluio.inst_addr_misaligned_out
  pc_gen.io.compr_jump := compr_flush
  pc_gen.io.compr_pc := compr_flush_addr

  // BPU
  bpu.io.pc_to_predict := pc_gen.io.pc_out
  bpu.io.feedback_pc := reg_exe_dtlb.io.bsrio.pc_out
  bpu.io.feedback_is_br := reg_exe_dtlb.io.bjio.feedback_is_br_out
  bpu.io.feedback_target_pc := reg_exe_dtlb.io.bjio.feedback_target_pc_out
  bpu.io.feedback_br_taken := reg_exe_dtlb.io.bjio.feedback_br_taken_out
  bpu.io.stall_update := reg_exe_dtlb.io.bpufb_stall_update
  bpu.io.update_btb := br_jump_flush

  // IMMU
  inst_af := !is_legal_addr(pc_gen.io.pc_out) || immu.io.front.af // TODO
  inst_pf := immu.io.front.pf
  immu.io.front.valid := !bpu.io.stall_req
  immu.io.front.force_s_mode := false.B
  immu.io.front.sum := 0.U
  immu.io.front.mxr := 0.U
  immu.io.front.mpp_s := false.B
  immu.io.front.va := pc_gen.io.pc_out
  immu.io.front.flush_all := immu_flush
  immu.io.front.satp_val := csr.io.satp_val
  immu.io.front.current_p := csr.io.current_p
  immu.io.front.is_inst := true.B
  immu.io.front.is_load := false.B
  immu.io.front.is_store := false.B
  io.immu <> immu.io.back.mmu

  immu_flush := Mux(
    !is_immu_idle_last && is_immu_idle,
    immu_delay_flush_signal,
    false.B
  ) || s_fence_flush
  is_immu_idle_last := is_immu_idle
  is_immu_idle := immu.io.front.is_idle
  when(is_immu_idle_last && !is_immu_idle) {
    immu_delay_flush_signal := s_fence_flush
  }.elsewhen(!is_immu_idle && s_fence_flush) {
    immu_delay_flush_signal := true.B
  }

  stall_req_if1_atomic := immu.io.front.stall_req || bpu.io.stall_req

  reg_if1_if2.io.bsrio.stall := stall_if1_if2
  reg_if1_if2.io.bsrio.flush_one := (compr_flush || br_jump_flush || csr_flush)
  reg_if1_if2.io.bsrio.bubble_in := stall_req_if1_atomic
  reg_if1_if2.io.bsrio.pc_in := pc_gen.io.pc_out
  reg_if1_if2.io.bsrio.last_stage_atomic_stall_req := stall_req_if1_atomic
  reg_if1_if2.io.bsrio.next_stage_atomic_stall_req := false.B
  reg_if1_if2.io.ifio.inst_af_in := inst_af
  reg_if1_if2.io.ifio.inst_pf_in := inst_pf
  reg_if1_if2.io.bsrio.next_stage_flush_req := false.B
  reg_if1_if2.io.bpio.predict_taken_in := bpu.io.branch_taken
  reg_if1_if2.io.bpio.target_in := bpu.io.pc_in_btb
  reg_if1_if2.io.immuio.use_immu_in := immu.io.front.need_translate

  // TODO parallel visiting of RAM and TLB
  io.imem.flush := i_fence_flush
  io.imem.stall := stall_if1_if2
  io.imem.req.bits.addr := immu.io.front.pa
  io.imem.req.bits.data := DontCare
  io.imem.req.valid := !stall_req_if1_atomic
  io.imem.req.bits.wen := false.B
  io.imem.req.bits.memtype := memWordU
  io.imem.resp.ready := true.B

  icache_flush := Mux(
    !is_icache_idle_last && is_icache_idle,
    icache_delay_flush_signal,
    false.B
  ) || i_fence_flush
  is_icache_idle_last := !stall_req_if3_atomic
  is_icache_idle := !stall_req_if3_atomic
  when(is_icache_idle_last && !is_icache_idle) {
    icache_delay_flush_signal := i_fence_flush
  }.elsewhen(!is_icache_idle && i_fence_flush) {
    icache_delay_flush_signal := true.B
  }

  inst_if3 := io.imem.resp.bits.data
  stall_req_if3_atomic := !io.imem.req.ready || !io.imem.flush_ready

  // Reg IF2 IF3
  reg_if2_if3.io.bsrio.stall := stall_if2_if3
  reg_if2_if3.io.bsrio.flush_one := (compr_flush || br_jump_flush || csr_flush)
  reg_if2_if3.io.bsrio.bubble_in := reg_if1_if2.io.bsrio.bubble_out
  reg_if2_if3.io.bsrio.pc_in := reg_if1_if2.io.bsrio.pc_out
  reg_if2_if3.io.bsrio.last_stage_atomic_stall_req := false.B
  reg_if2_if3.io.bsrio.next_stage_atomic_stall_req := stall_req_if3_atomic
  reg_if2_if3.io.ifio.inst_af_in := reg_if1_if2.io.ifio.inst_af_out
  reg_if2_if3.io.ifio.inst_pf_in := reg_if1_if2.io.ifio.inst_pf_out
  reg_if2_if3.io.bsrio.next_stage_flush_req := false.B
  reg_if2_if3.io.bpio.predict_taken_in := reg_if1_if2.io.bpio.predict_taken_out
  reg_if2_if3.io.bpio.target_in := reg_if1_if2.io.bpio.target_out
  reg_if2_if3.io.immuio.use_immu_in := reg_if1_if2.io.immuio.use_immu_out

  // Reg IF3 ID
  reg_if3_id.io.bsrio.last_stage_atomic_stall_req := stall_req_if3_atomic
  reg_if3_id.io.bsrio.next_stage_atomic_stall_req := false.B
  reg_if3_id.io.bsrio.stall := stall_if3_id
  reg_if3_id.io.bsrio.flush_one := (compr_flush || br_jump_flush || csr_flush)
  reg_if3_id.io.bsrio.bubble_in := stall_req_if3_atomic || reg_if2_if3.io.bsrio.bubble_out
  reg_if3_id.io.instio.inst_in := Mux(
    reg_if2_if3.io.ifio.inst_af_out || reg_if2_if3.io.ifio.inst_pf_out,
    BUBBLE,
    inst_if3
  )
  reg_if3_id.io.bsrio.pc_in := reg_if2_if3.io.bsrio.pc_out
  reg_if3_id.io.ifio.inst_af_in := reg_if2_if3.io.ifio.inst_af_out
  reg_if3_id.io.bsrio.next_stage_flush_req := compr_flush && !(br_jump_flush || csr_flush)
  reg_if3_id.io.ifio.inst_pf_in := reg_if2_if3.io.ifio.inst_pf_out
  reg_if3_id.io.bpio.predict_taken_in := reg_if2_if3.io.bpio.predict_taken_out
  reg_if3_id.io.bpio.target_in := reg_if2_if3.io.bpio.target_out
  reg_if3_id.io.immuio.use_immu_in := reg_if2_if3.io.immuio.use_immu_out

  // Some Special Michanism to Deal with C
  val half_fetched = reg_if3_id.io.half_fetched_regif3id
  compr_flush := (!reg_if3_id.io.instio.inst_out(1, 0).andR && !(reg_if3_id.io.bpio.predict_taken_out)) || half_fetched || dp_arbiter.io.flush_req
  compr_flush_addr := Mux(dp_arbiter.io.flush_req, dp_arbiter.io.flush_target_vpc,
    Mux(half_fetched, reg_if3_id.io.bsrio.pc_out, reg_if3_id.io.bsrio.pc_out + 2.U))

  // DP Arbiter
  dp_arbiter.io.next_stage_ready := !stall_id_exe
  dp_arbiter.io.flush := (br_jump_flush || csr_flush)
  dp_arbiter.io.vpc := reg_if3_id.io.bsrio.pc_out
  dp_arbiter.io.inst := reg_if3_id.io.instio.inst_out
  dp_arbiter.io.page_fault := reg_if3_id.io.ifio.inst_pf_out
  dp_arbiter.io.is_compressed := !reg_if3_id.io.instio.inst_out(1, 0).andR
  dp_arbiter.io.use_immu := reg_if3_id.io.immuio.use_immu_out

  // Decoder
  io.ctrl.inst := Mux(dp_arbiter.io.full_inst_ready, dp_arbiter.io.full_inst, reg_if3_id.io.instio.inst_out)

  // Reg ID EXE
  // when a stall signal comes to reg_id_exe, reg_if3_id must be stalled unless it is flushed
  // if the flush signal is from later stages, then reg_id_exe will be flushed too, if the signal is from dp_arbiter, a bubble will be inserted
  // because dp_arbiter.io.insert_bubble_next will be high (bucause stall, then state === concat and next_state === idle will be false, then if flush form dp_arbiter, next
  // state must be s_wait
  // Here we know the input from if3_id does not matter or does not change when dp_arbiter oor reg_id_exe is stalled
  // Then we can say RegNext(if3_id) and if3_id will be the same

  // next stage flush req is for difftest, the insn must be done so it can go to wb stage
  // so, dp.io.flush_req will not be added to reg_if3_id.next_stage_flush_request
  reg_id_exe.io.bsrio.last_stage_atomic_stall_req := false.B  // Both IF3ID and DPARBITER are not atomic against flush signal
  reg_id_exe.io.bsrio.next_stage_atomic_stall_req := stall_req_exe_atomic
  reg_id_exe.io.bsrio.stall := stall_id_exe
  reg_id_exe.io.bsrio.flush_one := (br_jump_flush || csr_flush)
  reg_id_exe.io.bsrio.bubble_in := Mux(dp_arbiter.io.full_inst_ready, false.B,
    reg_if3_id.io.bsrio.bubble_out || half_fetched || dp_arbiter.io.insert_bubble_next)
  reg_id_exe.io.instio.inst_in := Mux(dp_arbiter.io.full_inst_ready, dp_arbiter.io.full_inst, reg_if3_id.io.instio.inst_out)
  reg_id_exe.io.bsrio.pc_in := Mux(dp_arbiter.io.full_inst_ready, dp_arbiter.io.full_inst_pc, reg_if3_id.io.bsrio.pc_out)
  reg_id_exe.io.iiio.inst_info_in := io.ctrl.inst_info_out
  reg_id_exe.io.ifio.inst_af_in := Mux(dp_arbiter.io.full_inst_ready, false.B, reg_if3_id.io.ifio.inst_af_out)  // TODO no access fault now, temply use false.B
  reg_id_exe.io.bsrio.next_stage_flush_req := false.B
  reg_id_exe.io.ifio.inst_pf_in := Mux(dp_arbiter.io.full_inst_ready, false.B, reg_if3_id.io.ifio.inst_pf_out)
  reg_id_exe.io.hpfio.high_pf_in := Mux(dp_arbiter.io.full_inst_ready, dp_arbiter.io.high_page_fault, false.B)
  reg_id_exe.io.bpio.predict_taken_in := Mux(dp_arbiter.io.full_inst_ready,
    RegNext(reg_if3_id.io.bpio.predict_taken_out), reg_if3_id.io.bpio.predict_taken_out)
  reg_id_exe.io.bpio.target_in := Mux(dp_arbiter.io.full_inst_ready,
    RegNext(reg_if3_id.io.bpio.target_out), reg_if3_id.io.bpio.target_out)

  // ALU, Multipier, Branch and Jump
  imm_ext.io.inst := reg_id_exe.io.instio.inst_out
  imm_ext.io.instType := reg_id_exe.io.iiio.inst_info_out.instType
  alu.io.opType := reg_id_exe.io.iiio.inst_info_out.aluType
  alu.io.a := Mux(
    reg_id_exe.io.iiio.inst_info_out.ASelect === APC,
    reg_id_exe.io.bsrio.pc_out,
    rs1
  )
  alu.io.b := Mux(
    reg_id_exe.io.iiio.inst_info_out.BSelect === BIMM,
    imm_ext.io.out,
    rs2
  )

  branch_cond.io.rs1 := rs1
  branch_cond.io.rs2 := rs2
  branch_cond.io.brType := reg_id_exe.io.iiio.inst_info_out.brType

  feedback_is_br := (reg_id_exe.io.iiio.inst_info_out.brType.orR || reg_id_exe.io.iiio.inst_info_out.pcSelect === pcJump)
  feedback_target_pc := alu.io.out
  feedback_br_taken := branch_cond.io.branch || reg_id_exe.io.iiio.inst_info_out.pcSelect === pcJump

  predict_taken_but_not_br := (!reg_id_exe.io.iiio.inst_info_out.brType.orR &&
    reg_id_exe.io.iiio.inst_info_out.pcSelect =/= pcJump && reg_id_exe.io.bpio.predict_taken_out)
  predict_not_but_taken := branch_cond.io.branch && !reg_id_exe.io.bpio.predict_taken_out
  predict_taken_but_not := (!branch_cond.io.branch && reg_id_exe.io.bpio.predict_taken_out &&
    reg_id_exe.io.iiio.inst_info_out.brType.orR)
  misprediction := predict_not_but_taken || predict_taken_but_not
  wrong_target := branch_cond.io.branch && reg_id_exe.io.bpio.predict_taken_out
  inst_addr_misaligned := !(withCExt.asBool) && alu.io.out(1) &&
    (reg_id_exe.io.iiio.inst_info_out.pcSelect === pcJump || branch_cond.io.branch) &&
    !csr.io.with_c

  scheduler.io.is_bubble := reg_id_exe.io.bsrio.bubble_out
  scheduler.io.rs1_used_exe := (reg_id_exe.io.iiio.inst_info_out.ASelect === AXXX ||
    reg_id_exe.io.iiio.inst_info_out.pcSelect === pcBranch) && reg_id_exe.io.iiio.inst_info_out.rs1Num.orR
  scheduler.io.rs1_addr_exe := reg_id_exe.io.iiio.inst_info_out.rs1Num
  scheduler.io.rs2_used_exe := (reg_id_exe.io.iiio.inst_info_out.BSelect === BXXX ||
    reg_id_exe.io.iiio.inst_info_out.amoSelect =/= amoXXX ||
    reg_id_exe.io.iiio.inst_info_out.pcSelect === pcBranch ||
    reg_id_exe.io.iiio.inst_info_out.wbEnable === wenMem) && reg_id_exe.io.iiio.inst_info_out.rs2Num.orR
  scheduler.io.rs2_addr_exe := reg_id_exe.io.iiio.inst_info_out.rs2Num
  scheduler.io.rd_used_dtlb := reg_exe_dtlb.io.iiio.inst_info_out.modifyRd
  scheduler.io.rd_addr_dtlb := reg_exe_dtlb.io.iiio.inst_info_out.rdNum
  scheduler.io.rd_used_mem1 := reg_dtlb_mem1.io.iiio.inst_info_out.modifyRd
  scheduler.io.rd_addr_mem1 := reg_dtlb_mem1.io.iiio.inst_info_out.rdNum
  scheduler.io.rd_used_mem2 := reg_mem1_mem2.io.iiio.inst_info_out.modifyRd && !reg_mem1_mem2.io.csrio.expt_out
  scheduler.io.rd_addr_mem2 := reg_mem1_mem2.io.iiio.inst_info_out.rdNum
  scheduler.io.rd_used_mem3 := reg_mem2_mem3.io.iiio.inst_info_out.modifyRd && !reg_mem2_mem3.io.csrio.expt_out
  scheduler.io.rd_addr_mem3 := reg_mem2_mem3.io.iiio.inst_info_out.rdNum
  scheduler.io.rd_used_wb := reg_mem3_wb.io.iiio.inst_info_out.modifyRd && !reg_mem3_wb.io.csrio.expt_out
  scheduler.io.rd_addr_wb := reg_mem3_wb.io.iiio.inst_info_out.rdNum
  scheduler.io.rs1_from_reg := reg_file.io.rs1_data
  scheduler.io.rs2_from_reg := reg_file.io.rs2_data
  scheduler.io.rd_fen_from_dtlb := reg_exe_dtlb.io.iiio.inst_info_out.fwd_stage <= fwdDTLB
  scheduler.io.rd_from_dtlb := MuxLookup(
    reg_exe_dtlb.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_exe_dtlb.io.aluio.alu_val_out,
      wbPC -> (reg_exe_dtlb.io.bsrio.pc_out + 4.U),
      wbCPC -> (reg_exe_dtlb.io.bsrio.pc_out + 2.U)
    )
  )
  scheduler.io.rd_fen_from_mem1 := reg_dtlb_mem1.io.iiio.inst_info_out.fwd_stage <= fwdMem1
  scheduler.io.rd_from_mem1 := MuxLookup(
    reg_dtlb_mem1.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_dtlb_mem1.io.aluio.alu_val_out,
      wbPC -> (reg_dtlb_mem1.io.bsrio.pc_out + 4.U),
      wbCPC -> (reg_dtlb_mem1.io.bsrio.pc_out + 2.U)
    )
  )
  scheduler.io.rd_fen_from_mem2 := reg_mem1_mem2.io.iiio.inst_info_out.fwd_stage <= fwdMem2
  scheduler.io.rd_from_mem2 := MuxLookup(
    reg_mem1_mem2.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_mem1_mem2.io.aluio.alu_val_out,
      wbCSR -> reg_mem1_mem2.io.csrio.csr_val_out,
      wbPC -> (reg_mem1_mem2.io.bsrio.pc_out + 4.U),
      wbCPC -> (reg_mem1_mem2.io.bsrio.pc_out + 2.U)
    )
  )
  scheduler.io.rd_fen_from_mem3 := reg_mem2_mem3.io.iiio.inst_info_out.fwd_stage <= fwdMem2
  scheduler.io.rd_from_mem3 := MuxLookup(
    reg_mem2_mem3.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_mem2_mem3.io.aluio.alu_val_out,
      wbCSR -> reg_mem2_mem3.io.csrio.csr_val_out,
      wbPC -> (reg_mem2_mem3.io.bsrio.pc_out + 4.U),
      wbCPC -> (reg_mem2_mem3.io.bsrio.pc_out + 2.U)
    )
  )
  scheduler.io.rd_fen_from_wb := reg_mem3_wb.io.iiio.inst_info_out.fwd_stage <= fwdWb
  scheduler.io.rd_from_wb := MuxLookup(
    reg_mem3_wb.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_mem3_wb.io.aluio.alu_val_out,
      wbMEM -> reg_mem3_wb.io.memio.mem_val_out,
      wbCSR -> reg_mem3_wb.io.csrio.csr_val_out,
      wbCond -> reg_mem3_wb.io.memio.mem_val_out,
      wbPC -> (reg_mem3_wb.io.bsrio.pc_out + 4.U),
      wbCPC -> (reg_mem3_wb.io.bsrio.pc_out + 2.U)
    )
  )

  stall_req_exe_atomic := false.B
  stall_req_exe_interruptable := scheduler.io.stall_req
  rs1 := scheduler.io.rs1_val
  rs2 := scheduler.io.rs2_val

  // Reg EXE DTLB
  reg_exe_dtlb.io.bsrio.last_stage_atomic_stall_req := stall_req_exe_atomic
  reg_exe_dtlb.io.bsrio.next_stage_atomic_stall_req := stall_req_dtlb_atomic
  reg_exe_dtlb.io.bsrio.stall := stall_exe_dtlb
  reg_exe_dtlb.io.bsrio.flush_one := br_jump_flush || csr_flush
  reg_exe_dtlb.io.bsrio.bubble_in := (reg_id_exe.io.bsrio.bubble_out || stall_req_exe_atomic ||
    stall_req_exe_interruptable)
  reg_exe_dtlb.io.instio.inst_in := reg_id_exe.io.instio.inst_out
  reg_exe_dtlb.io.bsrio.pc_in := reg_id_exe.io.bsrio.pc_out
  reg_exe_dtlb.io.iiio.inst_info_in := reg_id_exe.io.iiio.inst_info_out
  reg_exe_dtlb.io.aluio.inst_addr_misaligned_in := inst_addr_misaligned
  reg_exe_dtlb.io.aluio.alu_val_in := alu.io.out
  reg_exe_dtlb.io.aluio.mem_wdata_in := rs2
  reg_exe_dtlb.io.ifio.inst_af_in := reg_id_exe.io.ifio.inst_af_out
  reg_exe_dtlb.io.bsrio.next_stage_flush_req := (br_jump_flush && !csr_flush)
  reg_exe_dtlb.io.ifio.inst_pf_in := reg_id_exe.io.ifio.inst_pf_out
  reg_exe_dtlb.io.hpfio.high_pf_in := reg_id_exe.io.hpfio.high_pf_out
  reg_exe_dtlb.io.bjio.misprediction_in := misprediction
  reg_exe_dtlb.io.bjio.wrong_target_in := wrong_target
  reg_exe_dtlb.io.bjio.predict_taken_but_not_br_in := predict_taken_but_not_br
  reg_exe_dtlb.io.bjio.bjpc_in := Mux(
    predict_taken_but_not || (predict_taken_but_not_br && !jump_flush),
    reg_id_exe.io.bsrio.pc_out + Mux(reg_id_exe.io.instio.inst_out(1, 0).andR, 4.U, 2.U),
    alu.io.out
  )

  reg_exe_dtlb.io.bjio.feedback_is_br_in := feedback_is_br
  reg_exe_dtlb.io.bjio.feedback_target_pc_in := feedback_target_pc
  reg_exe_dtlb.io.bjio.feedback_br_taken_in := feedback_br_taken
  reg_exe_dtlb.io.mdio.rs1_after_fwd_in := rs1
  reg_exe_dtlb.io.mdio.rs2_after_fwd_in := rs2
  reg_exe_dtlb.io.bpio.predict_taken_in := reg_id_exe.io.bpio.predict_taken_out
  reg_exe_dtlb.io.bpio.target_in := reg_id_exe.io.bpio.target_out

  jump_flush := reg_exe_dtlb.io.iiio.inst_info_out.pcSelect === pcJump && (!reg_exe_dtlb.io.bpio.predict_taken_out ||
    reg_exe_dtlb.io.aluio.alu_val_out =/= reg_exe_dtlb.io.bpio.target_out)
  br_jump_flush := ((reg_exe_dtlb.io.bjio.misprediction_out ||
    (reg_exe_dtlb.io.bjio.wrong_target_out && reg_exe_dtlb.io.aluio.alu_val_out =/= reg_exe_dtlb.io.bpio.target_out) ||
    reg_exe_dtlb.io.bjio.predict_taken_but_not_br_out ||
    jump_flush))

  // MUL DIV
  multiplier.io.start := reg_exe_dtlb.io.iiio.inst_info_out.mult
  multiplier.io.a := reg_exe_dtlb.io.mdio.rs1_after_fwd_out
  multiplier.io.b := reg_exe_dtlb.io.mdio.rs2_after_fwd_out
  multiplier.io.op := reg_exe_dtlb.io.iiio.inst_info_out.aluType

  // PEC
  // input.ready is stall_req signal
  // input.valid is start signal
  // output.valid is result_valid, which can be replaced by input.ready
  // output.ready is the next stage pipeline is free
  if (enable_pec) {
    val kah = WireInit(0.U(64.W))
    val kal = WireInit(0.U(64.W))
    val kbh = WireInit(0.U(64.W))
    val kbl = WireInit(0.U(64.W))
    val kth = WireInit(0.U(64.W))
    val ktl = WireInit(0.U(64.W))
    val kmh = WireInit(0.U(64.W))
    val kml = WireInit(0.U(64.W))
    BoringUtils.addSink(kah, "pec_kah")
    BoringUtils.addSink(kal, "pec_kal")
    BoringUtils.addSink(kbh, "pec_kbh")
    BoringUtils.addSink(kbl, "pec_kbl")
    BoringUtils.addSink(kth, "pec_kth")
    BoringUtils.addSink(ktl, "pec_ktl")
    BoringUtils.addSink(kmh, "pec_kmh")
    BoringUtils.addSink(kml, "pec_kml")
    val key_sel = reg_exe_dtlb.io.instio.inst_out(14, 12)
    pec_engine.input.valid := reg_exe_dtlb.io.iiio.inst_info_out.pec
    pec_engine.input.bits.encrypt := ~(reg_exe_dtlb.io.instio.inst_out(25))
    pec_engine.input.bits.keyh := MuxLookup(key_sel, kth, Seq(
      "b000".U -> kth,
      "b001".U -> kmh,
      "b010".U -> kah,
      "b011".U -> kbh
    ))
    pec_engine.input.bits.keyl := MuxLookup(key_sel, ktl, Seq(
      "b000".U -> ktl,
      "b001".U -> kml,
      "b010".U -> kal,
      "b011".U -> kbl
    ))
    pec_engine.input.bits.tweak := reg_exe_dtlb.io.mdio.rs2_after_fwd_out
    pec_engine.input.bits.text := reg_exe_dtlb.io.mdio.rs1_after_fwd_out
    pec_engine.input.bits.actual_round := 7.U(3.W)
    pec_engine.output.ready := !stall_dtlb_mem1

    // when(pec_engine.input.valid) {
    //   printf("keysel %x, keyhigh %x, keylow %x, kth %x, ktl %x, text %x, tweak %x\n", key_sel, pec_engine.input.bits.keyh,
    //   pec_engine.input.bits.keyl, kth, ktl,
    //   pec_engine.input.bits.text, pec_engine.input.bits.tweak)
    // }
  }

  // DMMU
  mem_af := dmmu.io.front.af || (!is_legal_addr(
    reg_exe_dtlb.io.aluio.alu_val_out
  ) &&
    reg_exe_dtlb.io.iiio.inst_info_out.memType.orR)
  dmmu.io.front.valid := reg_exe_dtlb.io.iiio.inst_info_out.memType.orR
  dmmu.io.front.force_s_mode := csr.io.force_s_mode_mem
  dmmu.io.front.sum := csr.io.mstatus_sum
  dmmu.io.front.mxr := csr.io.mstatus_mxr
  dmmu.io.front.mpp_s := csr.io.is_mpp_s_mode
  dmmu.io.front.va := reg_exe_dtlb.io.aluio.alu_val_out
  dmmu.io.front.flush_all := dmmu_flush
  dmmu.io.front.satp_val := csr.io.satp_val
  dmmu.io.front.current_p := csr.io.current_p
  dmmu.io.front.is_inst := false.B
  dmmu.io.front.is_load := (reg_exe_dtlb.io.iiio.inst_info_out.memType.orR &&
    reg_exe_dtlb.io.iiio.inst_info_out.wbEnable =/= wenMem &&
    reg_exe_dtlb.io.iiio.inst_info_out.amoSelect === amoXXX)
  dmmu.io.front.is_store := ((reg_exe_dtlb.io.iiio.inst_info_out.memType.orR &&
    reg_exe_dtlb.io.iiio.inst_info_out.wbEnable === wenMem) ||
    reg_exe_dtlb.io.iiio.inst_info_out.amoSelect.orR)
  io.dmmu <> dmmu.io.back.mmu

  dmmu_flush := Mux(
    !is_dmmu_idle_last && is_dmmu_idle,
    dmmu_delay_flush_signal,
    false.B
  ) || s_fence_flush
  is_dmmu_idle_last := is_dmmu_idle
  is_dmmu_idle := dmmu.io.front.is_idle
  when(is_dmmu_idle_last && !is_dmmu_idle) {
    dmmu_delay_flush_signal := s_fence_flush
  }.elsewhen(!is_dmmu_idle && s_fence_flush) {
    dmmu_delay_flush_signal := true.B
  }

  val stall_req_qarma_atomic = if (enable_pec) (!pec_engine.input.ready) else false.B
  stall_req_dtlb_atomic := dmmu.io.front.stall_req || multiplier.io.stall_req || stall_req_qarma_atomic

  // Reg DTLB MEM1
  reg_dtlb_mem1.io.bsrio.last_stage_atomic_stall_req := stall_req_dtlb_atomic
  reg_dtlb_mem1.io.bsrio.next_stage_atomic_stall_req := false.B
  reg_dtlb_mem1.io.bsrio.stall := stall_dtlb_mem1
  reg_dtlb_mem1.io.bsrio.flush_one := csr_flush
  reg_dtlb_mem1.io.bsrio.bubble_in := (reg_exe_dtlb.io.bsrio.bubble_out || stall_req_dtlb_atomic || amo_bubble_insert)
  reg_dtlb_mem1.io.instio.inst_in := reg_exe_dtlb.io.instio.inst_out
  reg_dtlb_mem1.io.bsrio.pc_in := reg_exe_dtlb.io.bsrio.pc_out
  reg_dtlb_mem1.io.iiio.inst_info_in := reg_exe_dtlb.io.iiio.inst_info_out
  reg_dtlb_mem1.io.aluio.inst_addr_misaligned_in := reg_exe_dtlb.io.aluio.inst_addr_misaligned_out
  val reg_dtlb_mem1_alu_val = Mux(
    reg_exe_dtlb.io.iiio.inst_info_out.mult,
    multiplier.io.mult_out,
    Mux(
      reg_exe_dtlb.io.iiio.inst_info_out.memType.orR,
      dmmu.io.front.pa,
      reg_exe_dtlb.io.aluio.alu_val_out
    )
  )
  if (enable_pec) {
    reg_dtlb_mem1.io.aluio.alu_val_in := Mux(reg_exe_dtlb.io.iiio.inst_info_out.pec, pec_engine.output.bits.result, reg_dtlb_mem1_alu_val)
  } else {
    reg_dtlb_mem1.io.aluio.alu_val_in := reg_dtlb_mem1_alu_val
  }
  reg_dtlb_mem1.io.aluio.mem_wdata_in := reg_exe_dtlb.io.aluio.mem_wdata_out
  reg_dtlb_mem1.io.intio.timer_int_in := io.int.mtip
  reg_dtlb_mem1.io.intio.software_int_in := io.int.msip
  reg_dtlb_mem1.io.intio.external_int_in := io.int.meip
  reg_dtlb_mem1.io.intio.s_external_int_in := io.int.seip
  reg_dtlb_mem1.io.ifio.inst_af_in := reg_exe_dtlb.io.ifio.inst_af_out
  reg_dtlb_mem1.io.bsrio.next_stage_flush_req := csr_flush
  reg_dtlb_mem1.io.ifio.inst_pf_in := reg_exe_dtlb.io.ifio.inst_pf_out
  reg_dtlb_mem1.io.hpfio.high_pf_in := reg_exe_dtlb.io.hpfio.high_pf_out
  reg_dtlb_mem1.io.intio.mem_af_in := mem_af
  reg_dtlb_mem1.io.intio.mem_pf_in := dmmu.io.front.pf

  amo_bubble_insert := (reg_dtlb_mem1.io.iiio.inst_info_out.amoSelect.orR ||
    reg_mem1_mem2.io.iiio.inst_info_out.amoSelect.orR)

  // CSR
  mem_addr_misaligned := MuxLookup(
    reg_dtlb_mem1.io.iiio.inst_info_out.memType,
    false.B,
    Seq(
      memByte -> false.B,
      memByteU -> false.B,
      memHalf -> reg_dtlb_mem1.io.aluio.alu_val_out(0),
      memHalfU -> reg_dtlb_mem1.io.aluio.alu_val_out(0),
      memWord -> reg_dtlb_mem1.io.aluio.alu_val_out(1, 0).orR,
      memWordU -> reg_dtlb_mem1.io.aluio.alu_val_out(1, 0).orR,
      memDouble -> reg_dtlb_mem1.io.aluio.alu_val_out(2, 0).orR
    )
  )

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
    reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable =/= wenMem) && reg_dtlb_mem1.io.iiio.inst_info_out.amoSelect === amoXXX
  csr.io.is_store := (reg_dtlb_mem1.io.iiio.inst_info_out.memType.orR &&
    reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenMem) || reg_dtlb_mem1.io.iiio.inst_info_out.amoSelect.orR
  csr.io.inst_access_fault := reg_dtlb_mem1.io.ifio.inst_af_out
  csr.io.inst_page_fault := reg_dtlb_mem1.io.ifio.inst_pf_out
  csr.io.high_page_fault := reg_dtlb_mem1.io.hpfio.high_pf_out
  csr.io.mem_access_fault := reg_dtlb_mem1.io.intio.mem_af_out
  csr.io.mem_page_fault := reg_dtlb_mem1.io.intio.mem_pf_out
  csr.io.tim_int := reg_dtlb_mem1.io.intio.timer_int_out
  csr.io.soft_int := reg_dtlb_mem1.io.intio.software_int_out
  csr.io.external_int := reg_dtlb_mem1.io.intio.external_int_out
  csr.io.s_external_int := reg_dtlb_mem1.io.intio.s_external_int_out

  expt_int_flush := csr.io.expt
  error_ret_flush := csr.io.ret
  write_satp_flush := csr.io.write_satp || csr.io.write_status || csr.io.write_misa
  i_fence_flush := (reg_dtlb_mem1.io.iiio.inst_info_out.flushType === flushI ||
    reg_dtlb_mem1.io.iiio.inst_info_out.flushType === flushAll) && !expt_int_flush
  s_fence_flush := (reg_dtlb_mem1.io.iiio.inst_info_out.flushType === flushAll ||
    reg_dtlb_mem1.io.iiio.inst_info_out.flushType === flushTLB || csr.io.write_satp) && !expt_int_flush

  // TODO af is ONLY for Difftest
  // REG MEM1 MEM2
  reg_mem1_mem2.io.bsrio.last_stage_atomic_stall_req := false.B
  reg_mem1_mem2.io.bsrio.next_stage_atomic_stall_req := false.B
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
  reg_mem1_mem2.io.csrio.af_in := csr.io.expt && (csr.io.inst_access_fault || csr.io.inst_page_fault || csr.io.high_page_fault)

  // REG MEM2 MEM3
  reg_mem2_mem3.io.bsrio.last_stage_atomic_stall_req := false.B
  reg_mem2_mem3.io.bsrio.next_stage_atomic_stall_req := stall_req_mem3_atomic
  reg_mem2_mem3.io.bsrio.stall := stall_mem2_mem3
  reg_mem2_mem3.io.bsrio.flush_one := false.B
  reg_mem2_mem3.io.bsrio.bubble_in := reg_mem1_mem2.io.bsrio.bubble_out
  reg_mem2_mem3.io.instio.inst_in := reg_mem1_mem2.io.instio.inst_out
  reg_mem2_mem3.io.bsrio.pc_in := reg_mem1_mem2.io.bsrio.pc_out
  reg_mem2_mem3.io.iiio.inst_info_in := reg_mem1_mem2.io.iiio.inst_info_out
  reg_mem2_mem3.io.aluio.inst_addr_misaligned_in := reg_mem1_mem2.io.aluio.inst_addr_misaligned_out
  reg_mem2_mem3.io.aluio.alu_val_in := reg_mem1_mem2.io.aluio.alu_val_out
  reg_mem2_mem3.io.aluio.mem_wdata_in := reg_mem1_mem2.io.aluio.mem_wdata_out
  reg_mem2_mem3.io.csrio.expt_in := reg_mem1_mem2.io.csrio.expt_out
  reg_mem2_mem3.io.csrio.int_resp_in := reg_mem1_mem2.io.csrio.int_resp_out
  reg_mem2_mem3.io.csrio.csr_val_in := reg_mem1_mem2.io.csrio.csr_val_out
  reg_mem2_mem3.io.bsrio.next_stage_flush_req := false.B
  reg_mem2_mem3.io.csrio.compare_in := reg_mem1_mem2.io.csrio.compare_out
  reg_mem2_mem3.io.csrio.comp_res_in := reg_mem1_mem2.io.csrio.comp_res_out
  reg_mem2_mem3.io.csrio.af_in := reg_mem1_mem2.io.csrio.af_out

  io.dmem.flush := false.B
  io.dmem.stall := !io.dmem.req.ready
  io.dmem.req.bits.addr := Mux(
    amo_arbiter.io.write_now,
    reg_mem2_mem3.io.aluio.alu_val_out,
    reg_dtlb_mem1.io.aluio.alu_val_out
  )
  io.dmem.req.bits.data := Mux(
    amo_arbiter.io.write_now,
    amo_arbiter.io.write_what,
    reg_dtlb_mem1.io.aluio.mem_wdata_out
  )
  io.dmem.req.valid := Mux(
    reservation.io.compare,
    reservation.io.succeed,
    (!csr.io.expt && ((reg_dtlb_mem1.io.iiio.inst_info_out.memType.orR &&
      !amo_arbiter.io.dont_read_again) ||
      amo_arbiter.io.write_now))
  )
  io.dmem.req.bits.wen := (reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenMem || amo_arbiter.io.write_now)
  io.dmem.req.bits.memtype := Mux(
    amo_arbiter.io.write_now,
    reg_mem2_mem3.io.iiio.inst_info_out.memType,
    reg_dtlb_mem1.io.iiio.inst_info_out.memType
  )
  io.dmem.resp.ready := true.B

  stall_req_mem3_atomic := !io.dmem.req.ready || amo_arbiter.io.stall_req || !io.dmem.flush_ready

  amo_arbiter.io.exception_or_int := reg_mem2_mem3.io.csrio.expt_out
  amo_arbiter.io.amo_op := reg_mem2_mem3.io.iiio.inst_info_out.amoSelect
  amo_arbiter.io.dmem_valid := io.dmem.resp.valid
  amo_arbiter.io.dmem_data := io.dmem.resp.bits.data
  amo_arbiter.io.reg_val := reg_mem2_mem3.io.aluio.mem_wdata_out
  amo_arbiter.io.mem_type := reg_mem2_mem3.io.iiio.inst_info_out.memType

  reservation.io.push := reg_dtlb_mem1.io.iiio.inst_info_out.wbEnable === wenRes && !csr.io.expt
  reservation.io.push_is_word := reg_dtlb_mem1.io.iiio.inst_info_out.memType === memWord
  reservation.io.push_addr := reg_dtlb_mem1.io.aluio.alu_val_out
  reservation.io.compare := reg_dtlb_mem1.io.iiio.inst_info_out.wbSelect === wbCond && !csr.io.expt
  reservation.io.compare_is_word := reg_dtlb_mem1.io.iiio.inst_info_out.memType === memWord
  reservation.io.compare_addr := reg_dtlb_mem1.io.aluio.alu_val_out
  reservation.io.flush := false.B

  // Reg MEM3 WB
  reg_mem3_wb.io.bsrio.last_stage_atomic_stall_req := stall_req_mem3_atomic
  reg_mem3_wb.io.bsrio.next_stage_atomic_stall_req := false.B
  reg_mem3_wb.io.bsrio.stall := stall_mem3_wb
  reg_mem3_wb.io.bsrio.flush_one := false.B
  reg_mem3_wb.io.bsrio.bubble_in := reg_mem2_mem3.io.bsrio.bubble_out || stall_req_mem3_atomic
  reg_mem3_wb.io.instio.inst_in := reg_mem2_mem3.io.instio.inst_out
  reg_mem3_wb.io.bsrio.pc_in := reg_mem2_mem3.io.bsrio.pc_out
  reg_mem3_wb.io.iiio.inst_info_in := reg_mem2_mem3.io.iiio.inst_info_out
  reg_mem3_wb.io.aluio.inst_addr_misaligned_in := reg_mem2_mem3.io.aluio.inst_addr_misaligned_out
  reg_mem3_wb.io.aluio.alu_val_in := reg_mem2_mem3.io.aluio.alu_val_out
  reg_mem3_wb.io.aluio.mem_wdata_in := reg_mem2_mem3.io.aluio.mem_wdata_out
  reg_mem3_wb.io.csrio.expt_in := reg_mem2_mem3.io.csrio.expt_out
  reg_mem3_wb.io.csrio.int_resp_in := reg_mem2_mem3.io.csrio.int_resp_out
  reg_mem3_wb.io.csrio.csr_val_in := reg_mem2_mem3.io.csrio.csr_val_out
  reg_mem3_wb.io.memio.mem_val_in := Mux(
    amo_arbiter.io.force_mem_val_out,
    amo_arbiter.io.mem_val_out,
    Mux(
      reg_mem2_mem3.io.csrio.compare_out,
      reg_mem2_mem3.io.csrio.comp_res_out,
      io.dmem.resp.bits.data
    )
  )
  reg_mem3_wb.io.bsrio.next_stage_flush_req := false.B
  reg_mem3_wb.io.csrio.compare_in := reg_mem2_mem3.io.csrio.compare_out
  reg_mem3_wb.io.csrio.comp_res_in := reg_mem2_mem3.io.csrio.comp_res_out
  reg_mem3_wb.io.csrio.af_in := reg_mem2_mem3.io.csrio.af_out

  // Register File
  reg_file.io.wen := (reg_mem3_wb.io.iiio.inst_info_out.wbEnable === wenReg ||
    reg_mem3_wb.io.iiio.inst_info_out.wbEnable === wenCSRW ||
    reg_mem3_wb.io.iiio.inst_info_out.wbEnable === wenCSRC ||
    reg_mem3_wb.io.iiio.inst_info_out.wbEnable === wenCSRS ||
    reg_mem3_wb.io.iiio.inst_info_out.wbEnable === wenRes ||
    reg_mem3_wb.io.iiio.inst_info_out.wbSelect === wbCond) && reg_mem3_wb.io.csrio.expt_out === false.B
  reg_file.io.rd_addr := reg_mem3_wb.io.iiio.inst_info_out.rdNum
  reg_file.io.rd_data := MuxLookup(
    reg_mem3_wb.io.iiio.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_mem3_wb.io.aluio.alu_val_out,
      wbMEM -> reg_mem3_wb.io.memio.mem_val_out,
      wbPC -> (reg_mem3_wb.io.bsrio.pc_out + 4.U),
      wbCSR -> reg_mem3_wb.io.csrio.csr_val_out,
      wbCond -> reg_mem3_wb.io.memio.mem_val_out,
      wbCPC -> (reg_mem3_wb.io.bsrio.pc_out + 2.U)
    )
  )
  reg_file.io.rs1_addr := reg_id_exe.io.iiio.inst_info_out.rs1Num
  reg_file.io.rs2_addr := reg_id_exe.io.iiio.inst_info_out.rs2Num

  // TODO from here is difftest, which is not formally included in the CPU
  // TODO Difftest
  // TODO Don't care the low-end code style
  if (diffTest) {
    val dtest_pc = RegInit(UInt(xlen.W), 0.U)
    val dtest_inst = RegInit(UInt(xlen.W), BUBBLE)
    val dtest_wbvalid = RegInit(Bool(), false.B)
    val dtest_expt = RegInit(false.B)
    val dtest_int = RegInit(false.B)
    val dtest_alu = RegInit(UInt(xlen.W), "hdeadbeef".U)
    val dtest_mem = RegInit(false.B)
    val stall_req_counters = RegInit(VecInit(Seq.fill(10)(0.U(xlen.W))))
    val starting = RegInit(false.B)
    val counterr = RegInit(0.U(xlen.W))

    // when (dtest_pc(31, 0).asUInt > "h006033e0".U && dtest_pc(39, 32) === "he0".U && dtest_pc(31, 0).asUInt < "h006033e8".U) {
    //   starting := true.B
    // }.elsewhen (dtest_pc(31, 0) === "h00603408".U || counterr >= 800.U) {
    //   // starting := false.B
    // }
    // when (starting && counterr < 800.U) {
    //    counterr := counterr + 1.U
    // }.otherwise {
    //   counterr := 0.U
    // }

    stall_req_counters(0) := stall_req_counters(0) + Mux(
      stall_req_if1_atomic,
      1.U,
      0.U
    )
    stall_req_counters(1) := stall_req_counters(1) + Mux(false.B, 1.U, 0.U)
    stall_req_counters(2) := stall_req_counters(2) + Mux(
      stall_req_if3_atomic,
      1.U,
      0.U
    )
    stall_req_counters(3) := stall_req_counters(3) + Mux(false.B, 1.U, 0.U)
    stall_req_counters(4) := stall_req_counters(4) + Mux(
      stall_req_exe_atomic || stall_req_exe_interruptable,
      1.U,
      0.U
    )
    stall_req_counters(5) := stall_req_counters(5) + Mux(
      stall_req_dtlb_atomic,
      1.U,
      0.U
    )
    stall_req_counters(6) := stall_req_counters(6) + Mux(false.B, 1.U, 0.U)
    stall_req_counters(7) := stall_req_counters(7) + Mux(
      reg_mem3_wb.io.iiio.inst_info_out.pcSelect === pcJump ||
        reg_mem3_wb.io.iiio.inst_info_out.pcSelect === pcBranch,
      1.U,
      0.U
    ) // FixMe Total Jump Banch
    stall_req_counters(8) := stall_req_counters(8) + Mux(
      stall_req_mem3_atomic,
      1.U,
      0.U
    )
    stall_req_counters(9) := stall_req_counters(9) + Mux(
      br_jump_flush,
      1.U,
      0.U
    ) // Total Miss-prediction

    dtest_wbvalid := !reg_mem3_wb.io.bsrio.bubble_out && !reg_mem3_wb.io.csrio.int_resp_out

    when(!stall_mem3_wb) {
      dtest_pc := Mux(
        reg_mem3_wb.io.csrio.af_out || reg_mem3_wb.io.bsrio.bubble_out || reg_mem3_wb.io.csrio.int_resp_out,
        dtest_pc,
        reg_mem3_wb.io.bsrio.pc_out
      )
      dtest_inst := reg_mem3_wb.io.instio.inst_out
      dtest_expt := reg_mem3_wb.io.csrio.int_resp_out
      dtest_alu := reg_mem3_wb.io.aluio.alu_val_out
      dtest_mem := (reg_mem3_wb.io.iiio.inst_info_out.memType.orR && !reg_mem3_wb.io.csrio.expt_out &&
      reg_mem3_wb.io.iiio.inst_info_out.wbEnable =/= wenRes && reg_mem3_wb.io.iiio.inst_info_out.wbSelect =/= wbCond)
    }
    dtest_int := reg_mem3_wb.io.csrio.int_resp_out // dtest_expt & (io.int.msip | io.int.mtip)

    if (diffTest) {
      BoringUtils.addSource(dtest_pc, "difftestPC")
      BoringUtils.addSource(dtest_inst, "difftestInst")
      BoringUtils.addSource(dtest_wbvalid, "difftestValid")
      BoringUtils.addSource(dtest_int, "difftestInt")
      BoringUtils.addSource(dtest_alu, "difftestALU")
      BoringUtils.addSource(dtest_mem, "difftestMem")
    } else if (ila) {
//      BoringUtils.addSource(dtest_pc, "ilaPC")
//      BoringUtils.addSource(dtest_inst, "ilaInst")
//      BoringUtils.addSource(dtest_wbvalid, "ilaValid")
//      BoringUtils.addSource(dtest_int, "ilaInt")
//      BoringUtils.addSource(dtest_alu, "ilaALU")
//      BoringUtils.addSource(dtest_mem, "ilaMem")
    }

//    printf("REG IF1 IF2 pc %x, tar %x\n", reg_if1_if2.io.bsrio.pc_in, reg_if1_if2.io.bpio.target_in)
//    printf("WT wrong target %x, is_br %x, predict_tk %x, alu %x, sup_tar %x\n",
//      wrong_target, branch_cond.io.branch, reg_id_exe.io.bpio.predict_taken_out, alu.io.out, reg_id_exe.io.bpio.target_out)

    if (pipeTrace) {
        if (vscode) {
        printf(
          "\t\tIF1\t\tIF2\t\tIF3\t\tID\t\tEXE\t\tDTLB\t\tMEM1\t\tMEM2\t\tWB\n"
        )
        printf(
          "Stall Req\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\n",
          stall_req_if1_atomic,
          0.U,
          stall_req_if3_atomic,
          0.U,
          stall_req_exe_atomic || stall_req_exe_interruptable,
          stall_req_dtlb_atomic,
          0.U,
          stall_req_mem3_atomic,
          0.U
        )
        printf(
          "Stall\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\n",
          stall_pc,
          stall_if1_if2,
          stall_if2_if3,
          stall_if3_id,
          stall_id_exe,
          stall_exe_dtlb,
          stall_dtlb_mem1,
          stall_mem1_mem2,
          stall_mem3_wb
        )
        printf(
          "PC\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
          pc_gen.io.pc_out(31, 0),
          reg_if1_if2.io.bsrio.pc_out(31, 0),
          reg_if2_if3.io.bsrio.pc_out(31, 0),
          reg_if3_id.io.bsrio.pc_out(31, 0),
          reg_id_exe.io.bsrio.pc_out(31, 0),
          reg_exe_dtlb.io.bsrio.pc_out(31, 0),
          reg_dtlb_mem1.io.bsrio.pc_out(31, 0),
          reg_mem1_mem2.io.bsrio.pc_out(31, 0),
          reg_mem3_wb.io.bsrio.pc_out(31, 0)
        )
        printf(
          "Inst\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
          BUBBLE(31, 0),
          BUBBLE(31, 0),
          io.imem.resp.bits.data(31, 0),
          reg_if3_id.io.instio.inst_out(31, 0),
          reg_id_exe.io.instio.inst_out(31, 0),
          reg_exe_dtlb.io.instio.inst_out(31, 0),
          reg_dtlb_mem1.io.instio.inst_out(31, 0),
          reg_mem1_mem2.io.instio.inst_out(31, 0),
          reg_mem3_wb.io.instio.inst_out(31, 0)
        )
        printf(
          "AluO\t\t%x\t\t%x\t\t%x\t\t%x\t%x\t%x\t%x\t%x\t%x\n",
          0.U,
          0.U,
          0.U,
          0.U,
          alu.io.out(31, 0),
          reg_exe_dtlb.io.aluio.alu_val_out(31, 0),
          reg_dtlb_mem1.io.aluio.alu_val_out(31, 0),
          reg_mem1_mem2.io.aluio.alu_val_out(31, 0),
          reg_mem3_wb.io.aluio.alu_val_out(31, 0)
        )
        printf(
          "MemO\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t%x\t%x\n",
          0.U,
          0.U,
          0.U,
          0.U,
          0.U,
          0.U,
          0.U,
          io.dmem.resp.bits.data(31, 0),
          reg_mem3_wb.io.memio.mem_val_out(31, 0)
        )
        printf(
          "Bubb\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\t\t%x\n",
          0.U,
          reg_if1_if2.io.bsrio.bubble_out,
          reg_if2_if3.io.bsrio.bubble_out,
          reg_if3_id.io.bsrio.bubble_out,
          reg_id_exe.io.bsrio.bubble_out,
          reg_exe_dtlb.io.bsrio.bubble_out,
          reg_dtlb_mem1.io.bsrio.bubble_out,
          reg_mem1_mem2.io.bsrio.bubble_out,
          reg_mem3_wb.io.bsrio.bubble_out
        )
      } else {
        when (starting === true.B /*&& counterr < 800.U*/) {
          printf("\t\tIF1\tIF2\tIF3\tID\tEXE\tDTLB\tMEM1\tMEM2\tMEM3\tWB\n")
          printf(
            "Stall Req\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
            stall_req_if1_atomic,
            0.U,
            stall_req_if3_atomic,
            0.U,
            stall_req_exe_atomic || stall_req_exe_interruptable,
            stall_req_dtlb_atomic,
            0.U,
            0.U,
            stall_req_mem3_atomic,
            0.U
          )
          printf(
            "Stall\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
            stall_pc,
            stall_if1_if2,
            stall_if2_if3,
            stall_if3_id,
            stall_id_exe,
            stall_exe_dtlb,
            stall_dtlb_mem1,
            stall_mem1_mem2,
            stall_mem2_mem3,
            stall_mem3_wb
          )
          printf(
            "PC\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
            pc_gen.io.pc_out(15, 0),
            reg_if1_if2.io.bsrio.pc_out(15, 0),
            reg_if2_if3.io.bsrio.pc_out(15, 0),
            reg_if3_id.io.bsrio.pc_out(15, 0),
            reg_id_exe.io.bsrio.pc_out(15, 0),
            reg_exe_dtlb.io.bsrio.pc_out(15, 0),
            reg_dtlb_mem1.io.bsrio.pc_out(15, 0),
            reg_mem1_mem2.io.bsrio.pc_out(15, 0),
            reg_mem2_mem3.io.bsrio.pc_out(15, 0),
            reg_mem3_wb.io.bsrio.pc_out(15, 0)
          )
          printf(
            "Inst\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
            BUBBLE(15, 0),
            BUBBLE(15, 0),
            io.imem.resp.bits.data(15, 0),
            reg_if3_id.io.instio.inst_out(15, 0),
            reg_id_exe.io.instio.inst_out(15, 0),
            reg_exe_dtlb.io.instio.inst_out(15, 0),
            reg_dtlb_mem1.io.instio.inst_out(15, 0),
            reg_mem1_mem2.io.instio.inst_out(15, 0),
            reg_mem2_mem3.io.instio.inst_out(15, 0),
            reg_mem3_wb.io.instio.inst_out(15, 0)
          )
          printf(
            "AluO\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
            0.U,
            0.U,
            0.U,
            0.U,
            alu.io.out(15, 0),
            reg_exe_dtlb.io.aluio.alu_val_out(15, 0),
            reg_dtlb_mem1.io.aluio.alu_val_out(15, 0),
            reg_mem1_mem2.io.aluio.alu_val_out(15, 0),
            reg_mem2_mem3.io.aluio.alu_val_out(15, 0),
            reg_mem3_wb.io.aluio.alu_val_out(15, 0)
          )
          printf(
            "MemO\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
            0.U,
            0.U,
            0.U,
            0.U,
            0.U,
            0.U,
            0.U,
            0.U,
            io.dmem.resp.bits.data(15, 0),
            reg_mem3_wb.io.memio.mem_val_out(15, 0)
          )
          printf(
            "Bubb\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
            0.U,
            reg_if1_if2.io.bsrio.bubble_out,
            reg_if2_if3.io.bsrio.bubble_out,
            reg_if3_id.io.bsrio.bubble_out,
            reg_id_exe.io.bsrio.bubble_out,
            reg_exe_dtlb.io.bsrio.bubble_out,
            reg_dtlb_mem1.io.bsrio.bubble_out,
            reg_mem1_mem2.io.bsrio.bubble_out,
            reg_mem2_mem3.io.bsrio.bubble_out,
            reg_mem3_wb.io.bsrio.bubble_out
          )
          printf(
            "VA To DMMU %x, VA valid %x, brj_flush %x, PA %x\n",
            dmmu.io.front.va,
            dmmu.io.front.valid,
            br_jump_flush,
            dmmu.io.front.pa
          )
          printf(
            "PC to dmem %x, Valid to dmem %x, Wen to dmem %x, Wdata to dmem %x, PA to dmem %x, dmem PC out %x, dmem Data out %x\n",
            reg_dtlb_mem1.io.bsrio.pc_out,
            io.dmem.req.valid,
            io.dmem.req.bits.wen,
            io.dmem.req.bits.data,
            io.dmem.req.bits.addr,
            reg_mem2_mem3.io.bsrio.pc_out,
            io.dmem.resp.bits.data
          )
          printf("half_fetched %x, dp_insert_bubb %x\n", half_fetched, dp_arbiter.io.insert_bubble_next)
          printf("\n")
        }
      }
      

      //      if(traceBPU) {
      //        printf(
      //          "Take\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
      //          bpu.io.branch_taken,
      //          reg_if1_if2.io.bpio.predict_taken_out,
      //          reg_if3_id.io.bpio.predict_taken_out,
      //          reg_id_exe.io.bpio.predict_taken_out,
      //          0.U,
      //          0.U,
      //          0.U,
      //          0.U
      //        )
      //        printf(
      //          "Tar\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
      //          bpu.io.pc_in_btb(15, 0),
      //          reg_if1_if2.io.bpio.target_out(15, 0),
      //          reg_if3_id.io.bpio.target_out(15, 0),
      //          reg_id_exe.io.bpio.target_out(15, 0),
      //          0.U,
      //          0.U,
      //          0.U,
      //          0.U
      //        )
      //      }
      //      printf("Priv %x\t\tInstAddrO %x\t\tMemAddr0 %x\t\tMemFS %x\n",
      //        csr.io.current_p, immu.io.front.pa, dmmu.io.front.pa, csr.io.force_s_mode_mem)
    }

    if (prtHotSpot) {
      if (!pipeTrace)
        printf("\t\tIF1\tIF2\tIF3\tID\tEXE\tDTLB\tMEM1\tMEM2\tMEM3\tWB\n")
      printf(
        "BubbMaker\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n",
        stall_req_counters(0)(15, 0),
        stall_req_counters(1)(15, 0),
        stall_req_counters(2)(15, 0),
        stall_req_counters(3)(15, 0),
        stall_req_counters(4)(15, 0),
        stall_req_counters(5)(15, 0),
        stall_req_counters(6)(15, 0),
        stall_req_counters(7)(15, 0),
        stall_req_counters(8)(15, 0),
        stall_req_counters(9)(15, 0)
      )
    }

    BoringUtils.addSource(
      VecInit((0 to 9).map(i => stall_req_counters(i))),
      "difftestStreqs"
    )

    if (pipeTrace || prtHotSpot) {
      when (starting === true.B && counterr < 20.U) {printf("\n")}
    }

    //    printf("------> compare %x, succeed %x, push %x\n", reservation.io.compare, reservation.io.succeed, reservation.io.push)

    //    printf("-------> exit flush %x, br_flush %x, pco %x, if_pco %x, \n", expt_int_flush, br_jump_flush, pc_gen.io.pc_out, reg_if3_id.io.pc_out)
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
