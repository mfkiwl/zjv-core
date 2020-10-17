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

class DataPathIO extends Bundle with phvntomParams {
  val ctrl = Flipped(new ControlPathIO)
  val imem = Flipped(new MemIO)
  val dmem = Flipped(new MemIO)
  val int = Flipped(Flipped(new InterruptIO))
}

// TODO This is a 7-stage pipeline
// TODO Add TLB and 2-stage pipeliend I$ and D$
// TODO In my opinion, D$ may have to be extended to 3 stages
// TODO due to the high fan out in MEM-related stages
class DataPath extends Module with phvntomParams {
  val io = IO(new DataPathIO)

  // TODO forwarding machanism
  // TODO flush eariler, eg flush & stall_id_exe, but stall_req_exe === false, then just flush (like br_jump in ID_EXE)
  // TODO the original michanism should be reserved,
  // TODO furthermore, if the last stage's BUBBLE signal is high, just cut off the stall-chain, thus
  // TODO the total stalling time might be reduced

  val pc_gen = Module(new PcGen)
  val reg_if1_if2 = Module(new RegIf1If2)
  val reg_if2_id = Module(new RegIf2Id)
  val reg_id_exe = Module(new RegIdExe)
  val branch_cond = Module(new BrCond)
  val imm_ext = Module(new ImmExt)
  val alu = Module(new ALU)
  val multiplier = Module(new Multiplier)
  val reg_exe_mem1 = Module(new RegExeMem1)
  val csr = Module(new CSR)
  val reg_mem1_mem2 = Module(new RegMem1Mem2)
  val reg_mem2_wb = Module(new RegMem2Wb)
  val reg_file = Module(new RegFile)
  val scheduler = Module(new ALUScheduler)
  val amo_arbiter = Module(new AMOArbiter)
  val reservation = Module(new Reservation)

  // Stall Request Signals
  val stall_req_if2_atomic = WireInit(Bool(), false.B)
  val stall_req_exe_atomic = WireInit(Bool(), false.B)
  val stall_req_exe_interruptable = WireInit(Bool(), false.B)
  val stall_req_mem2_atomic = WireInit(Bool(), false.B)

  // Strange Bubble Inserter
  val amo_bubble_inserter = WireInit(Bool(), false.B)

  // Flush Signals
  val br_jump_flush = WireInit(Bool(), false.B)
  val expt_int_flush = WireInit(Bool(), false.B)
  val error_ret_flush = WireInit(Bool(), false.B)

  // Stall Signals
  val stall_pc = WireInit(Bool(), false.B)
  val stall_if1_if2 = WireInit(Bool(), false.B)
  val stall_if2_id = WireInit(Bool(), false.B)
  val stall_id_exe = WireInit(Bool(), false.B)
  val stall_exe_mem1 = WireInit(Bool(), false.B)
  val stall_mem1_mem2 = WireInit(Bool(), false.B)
  val stall_mem2_wb = WireInit(Bool(), false.B)

  // If1 Signals
  val inst_af = WireInit(Bool(), false.B)

  // If2 Signals
  val inst_if2 = WireInit(UInt(32.W), BUBBLE)

  // Exe Signals
  val inst_addr_misaligned = WireInit(Bool(), false.B)
  val mem_af = WireInit(Bool(), false.B)
  val rs1 = WireInit(UInt(xlen.W), 0.U)
  val rs2 = WireInit(UInt(xlen.W), 0.U)

  // Mem Signals
  val mem_addr_misaligned = WireInit(Bool(), false.B)

  // TODO When AS is decided, this should be changed
  def is_legal_addr(addr: UInt): Bool = {
    addr(addr.getWidth - 1, addr.getWidth / 2) === 0.U
  }

  // Stall Control Logic
  stall_mem2_wb := false.B
  stall_mem1_mem2 := stall_mem2_wb || stall_req_mem2_atomic
  stall_exe_mem1 := stall_mem1_mem2 || false.B
  stall_id_exe := stall_exe_mem1 || stall_req_exe_interruptable || stall_req_exe_atomic || amo_bubble_inserter
  stall_if2_id := stall_id_exe || false.B
  stall_if1_if2 := stall_if2_id || stall_req_if2_atomic
  stall_pc := stall_if1_if2 || false.B

  // PC Generator
  pc_gen.io.stall := stall_pc
  pc_gen.io.expt_int := expt_int_flush
  pc_gen.io.error_ret := error_ret_flush
  pc_gen.io.epc := csr.io.epc
  pc_gen.io.tvec := csr.io.evec
  pc_gen.io.branch_jump := br_jump_flush
  pc_gen.io.branch_pc := alu.io.out
  pc_gen.io.inst_addr_misaligned := inst_addr_misaligned

  // Inst Access Fault Detector
  inst_af := !is_legal_addr(pc_gen.io.pc_out)

  // TODO Dummy stage
  // TODO ultimately, in this stage, I$ should access SRAM
  reg_if1_if2.io.stall := stall_if1_if2
  reg_if1_if2.io.flush_one := br_jump_flush || expt_int_flush || error_ret_flush
  reg_if1_if2.io.bubble_in := false.B
  reg_if1_if2.io.pc_in := pc_gen.io.pc_out
  reg_if1_if2.io.last_stage_atomic_stall_req := false.B
  reg_if1_if2.io.next_stage_atomic_stall_req := stall_req_if2_atomic
  reg_if1_if2.io.inst_af_in := inst_af
  reg_if1_if2.io.next_stage_flush_req := false.B

  // TODO before this is a dummy stage, because we only have 1-stage I$
  // TODO which is bound to be 2-or-3-stage later
  // I$ and Stall Request
  io.imem.req.bits.addr := reg_if1_if2.io.pc_out
  io.imem.req.bits.data := DontCare
  io.imem.req.valid := !reg_if1_if2.io.bubble_out && !reg_if1_if2.io.inst_af_out
  io.imem.req.bits.wen := false.B
  io.imem.req.bits.memtype := memWordU
  io.imem.resp.ready := true.B

  inst_if2 := io.imem.resp.bits.data
  stall_req_if2_atomic := io.imem.req.valid && !io.imem.resp.valid

  // Reg IF2 ID
  reg_if2_id.io.last_stage_atomic_stall_req := stall_req_if2_atomic
  reg_if2_id.io.next_stage_atomic_stall_req := false.B
  reg_if2_id.io.stall := stall_if2_id
  reg_if2_id.io.flush_one := br_jump_flush || expt_int_flush || error_ret_flush
  reg_if2_id.io.bubble_in := stall_req_if2_atomic || reg_if1_if2.io.bubble_out
  reg_if2_id.io.inst_in := Mux(reg_if1_if2.io.inst_af_out, BUBBLE, inst_if2)
  reg_if2_id.io.pc_in := reg_if1_if2.io.pc_out
  reg_if2_id.io.inst_af_in := reg_if1_if2.io.inst_af_out
  reg_if2_id.io.next_stage_flush_req := false.B

  // Decoder
  io.ctrl.inst := reg_if2_id.io.inst_out

  // Reg ID EXE
  reg_id_exe.io.last_stage_atomic_stall_req := false.B
  reg_id_exe.io.next_stage_atomic_stall_req := stall_req_exe_atomic
  reg_id_exe.io.stall := stall_id_exe
  reg_id_exe.io.flush_one := br_jump_flush || expt_int_flush || error_ret_flush
  reg_id_exe.io.bubble_in := reg_if2_id.io.bubble_out
  reg_id_exe.io.inst_in := reg_if2_id.io.inst_out
  reg_id_exe.io.pc_in := reg_if2_id.io.pc_out
  reg_id_exe.io.inst_info_in := io.ctrl.inst_info_out
  reg_id_exe.io.inst_af_in := reg_if2_id.io.inst_af_out
  reg_id_exe.io.next_stage_flush_req := br_jump_flush

  // ALU, Multipier, Branch and Jump
  imm_ext.io.inst := reg_id_exe.io.inst_out
  imm_ext.io.instType := reg_id_exe.io.inst_info_out.instType

  alu.io.opType := reg_id_exe.io.inst_info_out.aluType
  alu.io.a := Mux(reg_id_exe.io.inst_info_out.ASelect === APC, reg_id_exe.io.pc_out, rs1)
  alu.io.b := Mux(reg_id_exe.io.inst_info_out.BSelect === BIMM, imm_ext.io.out, rs2)

  multiplier.io.start := reg_id_exe.io.inst_info_out.mult && !scheduler.io.stall_req
  multiplier.io.a := rs1
  multiplier.io.b := rs2
  multiplier.io.op := reg_id_exe.io.inst_info_out.aluType

  branch_cond.io.rs1 := rs1
  branch_cond.io.rs2 := rs2
  branch_cond.io.brType := reg_id_exe.io.inst_info_out.brType

  br_jump_flush := (branch_cond.io.branch || reg_id_exe.io.inst_info_out.pcSelect === pcJump) && !scheduler.io.stall_req
  inst_addr_misaligned := alu.io.out(1) && (reg_id_exe.io.inst_info_out.pcSelect === pcJump || branch_cond.io.branch)

  scheduler.io.is_bubble := reg_id_exe.io.bubble_out
  scheduler.io.rs1_used_exe := (reg_id_exe.io.inst_info_out.ASelect === ARS1 ||
    reg_id_exe.io.inst_info_out.pcSelect === pcBranch) && reg_id_exe.io.inst_out(19, 15).orR
  scheduler.io.rs1_addr_exe := reg_id_exe.io.inst_out(19, 15)
  scheduler.io.rs2_used_exe := (reg_id_exe.io.inst_info_out.BSelect === BXXX ||
    reg_id_exe.io.inst_info_out.amoSelect =/= amoXXX ||
    reg_id_exe.io.inst_info_out.pcSelect === pcBranch ||
    reg_id_exe.io.inst_info_out.wbEnable === wenMem) && reg_id_exe.io.inst_out(24, 20).orR
  scheduler.io.rs2_addr_exe := reg_id_exe.io.inst_out(24, 20)
  scheduler.io.rd_used_mem1 := (reg_exe_mem1.io.inst_info_out.wbEnable === wenReg ||
    reg_exe_mem1.io.inst_info_out.wbEnable === wenCSRC ||
    reg_exe_mem1.io.inst_info_out.wbEnable === wenCSRS ||
    reg_exe_mem1.io.inst_info_out.wbEnable === wenCSRW ||
    reg_exe_mem1.io.inst_info_out.wbEnable === wenRes ||
    reg_exe_mem1.io.inst_info_out.wbSelect === wbCond)
  scheduler.io.rd_addr_mem1 := reg_exe_mem1.io.inst_out(11, 7)
  scheduler.io.rd_used_mem2 := (reg_mem1_mem2.io.inst_info_out.wbEnable === wenReg ||
    reg_mem1_mem2.io.inst_info_out.wbEnable === wenCSRC ||
    reg_mem1_mem2.io.inst_info_out.wbEnable === wenCSRS ||
    reg_mem1_mem2.io.inst_info_out.wbEnable === wenCSRW ||
    reg_mem1_mem2.io.inst_info_out.wbEnable === wenRes ||
    reg_mem1_mem2.io.inst_info_out.wbSelect === wbCond)
  scheduler.io.rd_addr_mem2 := reg_mem1_mem2.io.inst_out(11, 7)
  scheduler.io.rd_used_wb := (reg_mem2_wb.io.inst_info_out.wbEnable === wenReg ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRC ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRS ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRW ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenRes ||
    reg_mem2_wb.io.inst_info_out.wbSelect === wbCond)
  scheduler.io.rd_addr_wb := reg_mem2_wb.io.inst_out(11, 7)
  scheduler.io.rs1_from_reg := reg_file.io.rs1_data
  scheduler.io.rs2_from_reg := reg_file.io.rs2_data
  scheduler.io.rd_fen_from_mem1 := reg_exe_mem1.io.inst_info_out.fwd_stage <= fwdMem1
  scheduler.io.rd_from_mem1 := reg_exe_mem1.io.alu_val_out
  scheduler.io.rd_fen_from_mem2 := reg_mem1_mem2.io.inst_info_out.fwd_stage <= fwdMem2
  scheduler.io.rd_from_mem2 := MuxLookup(
    reg_mem1_mem2.io.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_mem1_mem2.io.alu_val_out,
      wbCSR -> reg_mem1_mem2.io.csr_val_out
    )
  )
  scheduler.io.rd_fen_from_wb := reg_mem2_wb.io.inst_info_out.fwd_stage <= fwdWb
  scheduler.io.rd_from_wb := MuxLookup(
    reg_mem2_wb.io.inst_info_out.wbSelect,
    "hdeadbeef".U,
    Seq(
      wbALU -> reg_mem2_wb.io.alu_val_out,
      wbMEM -> reg_mem2_wb.io.mem_val_out,
      wbCSR -> reg_mem2_wb.io.csr_val_out,
      wbCond -> reg_mem2_wb.io.mem_val_out
    )
  )

  stall_req_exe_atomic := multiplier.io.stall_req
  stall_req_exe_interruptable := scheduler.io.stall_req
  rs1 := scheduler.io.rs1_val
  rs2 := scheduler.io.rs2_val

  // Reg EXE MEM1
  reg_exe_mem1.io.last_stage_atomic_stall_req := stall_req_exe_atomic
  reg_exe_mem1.io.next_stage_atomic_stall_req := false.B
  reg_exe_mem1.io.stall := stall_exe_mem1
  reg_exe_mem1.io.flush_one := expt_int_flush || error_ret_flush
  reg_exe_mem1.io.bubble_in := (reg_id_exe.io.bubble_out || stall_req_exe_atomic ||
    stall_req_exe_interruptable || amo_bubble_inserter)
  reg_exe_mem1.io.inst_in := reg_id_exe.io.inst_out
  reg_exe_mem1.io.pc_in := reg_id_exe.io.pc_out
  reg_exe_mem1.io.inst_info_in := reg_id_exe.io.inst_info_out
  reg_exe_mem1.io.inst_addr_misaligned_in := inst_addr_misaligned
  reg_exe_mem1.io.alu_val_in := Mux(reg_id_exe.io.inst_info_out.mult, multiplier.io.mult_out, alu.io.out)
  reg_exe_mem1.io.mem_wdata_in := rs2
  reg_exe_mem1.io.timer_int_in := io.int.mtip
  reg_exe_mem1.io.software_int_in := io.int.msip
  reg_exe_mem1.io.external_int_in := io.int.meip
  reg_exe_mem1.io.inst_af_in := reg_id_exe.io.inst_af_out
  reg_exe_mem1.io.next_stage_flush_req := expt_int_flush || error_ret_flush

  amo_bubble_inserter := reg_exe_mem1.io.inst_info_out.amoSelect.orR

  // CSR
  mem_addr_misaligned := MuxLookup(reg_exe_mem1.io.inst_info_out.memType,
    false.B, Seq(
    memByte -> false.B,
    memByteU -> false.B,
    memHalf -> reg_exe_mem1.io.alu_val_out(0),
    memHalfU -> reg_exe_mem1.io.alu_val_out(0),
    memWord -> reg_exe_mem1.io.alu_val_out(1, 0).orR,
    memWordU -> reg_exe_mem1.io.alu_val_out(1, 0).orR,
    memDouble -> reg_exe_mem1.io.alu_val_out(2, 0).orR
  ))
  mem_af := reg_exe_mem1.io.inst_info_out.memType.orR && !is_legal_addr(reg_exe_mem1.io.alu_val_out)

  csr.io.stall := stall_exe_mem1
  csr.io.cmd := reg_exe_mem1.io.inst_info_out.wbEnable
  csr.io.in := reg_exe_mem1.io.alu_val_out
  csr.io.bubble := reg_exe_mem1.io.bubble_out
  csr.io.pc := reg_exe_mem1.io.pc_out
  csr.io.illegal_mem_addr := mem_addr_misaligned
  csr.io.illegal_inst_addr := reg_exe_mem1.io.inst_addr_misaligned_out
  csr.io.inst := reg_exe_mem1.io.inst_out
  csr.io.illegal := reg_exe_mem1.io.inst_info_out.instType === Illegal
  csr.io.is_load := (reg_exe_mem1.io.inst_info_out.memType.orR &&
    reg_exe_mem1.io.inst_info_out.wbEnable =/= wenMem)
  csr.io.is_store := (reg_exe_mem1.io.inst_info_out.memType.orR &&
    reg_exe_mem1.io.inst_info_out.wbEnable === wenMem)
  csr.io.inst_access_fault := reg_exe_mem1.io.inst_af_out
  csr.io.mem_access_fault := mem_af
  csr.io.tim_int := reg_exe_mem1.io.timer_int_out
  csr.io.soft_int := reg_exe_mem1.io.software_int_out
  csr.io.external_int := reg_exe_mem1.io.external_int_out

  expt_int_flush := csr.io.expt
  error_ret_flush := csr.io.ret

  // TODO 2-stage D$
  // REG MEM1 MEM2
  reg_mem1_mem2.io.last_stage_atomic_stall_req := false.B
  reg_mem1_mem2.io.next_stage_atomic_stall_req := stall_req_mem2_atomic
  reg_mem1_mem2.io.stall := stall_mem1_mem2
  reg_mem1_mem2.io.flush_one := false.B
  reg_mem1_mem2.io.bubble_in := reg_exe_mem1.io.bubble_out
  reg_mem1_mem2.io.inst_in := reg_exe_mem1.io.inst_out
  reg_mem1_mem2.io.pc_in := reg_exe_mem1.io.pc_out
  reg_mem1_mem2.io.inst_info_in := reg_exe_mem1.io.inst_info_out
  reg_mem1_mem2.io.inst_addr_misaligned_in := reg_exe_mem1.io.inst_addr_misaligned_out
  reg_mem1_mem2.io.alu_val_in := reg_exe_mem1.io.alu_val_out
  reg_mem1_mem2.io.mem_wdata_in := reg_exe_mem1.io.mem_wdata_out
  reg_mem1_mem2.io.timer_int_in := reg_exe_mem1.io.timer_int_out
  reg_mem1_mem2.io.software_int_in := reg_exe_mem1.io.software_int_out
  reg_mem1_mem2.io.external_int_in := reg_exe_mem1.io.external_int_out
  reg_mem1_mem2.io.expt_in := csr.io.expt
  reg_mem1_mem2.io.int_resp_in := csr.io.int
  reg_mem1_mem2.io.csr_val_in := csr.io.out
  reg_mem1_mem2.io.inst_af_in := reg_exe_mem1.io.inst_af_out
  reg_mem1_mem2.io.next_stage_flush_req := false.B

  // Memory and AMO
  io.dmem.req.bits.addr := reg_mem1_mem2.io.alu_val_out
  io.dmem.req.bits.data := Mux(amo_arbiter.io.write_now, amo_arbiter.io.write_what, reg_mem1_mem2.io.mem_wdata_out)
  io.dmem.req.valid := Mux(reservation.io.compare, reservation.io.succeed,
    (reg_mem1_mem2.io.expt_out === false.B && ((reg_mem1_mem2.io.inst_info_out.memType.orR &&
    !amo_arbiter.io.dont_read_again) ||
    amo_arbiter.io.write_now)))
  io.dmem.req.bits.wen := (reg_mem1_mem2.io.inst_info_out.wbEnable === wenMem || amo_arbiter.io.write_now)
  io.dmem.req.bits.memtype := reg_mem1_mem2.io.inst_info_out.memType
  io.dmem.resp.ready := true.B

  amo_arbiter.io.exception_or_int := reg_mem1_mem2.io.expt_out
  amo_arbiter.io.amo_op := reg_mem1_mem2.io.inst_info_out.amoSelect
  amo_arbiter.io.dmem_valid := io.dmem.resp.valid
  amo_arbiter.io.dmem_data := io.dmem.resp.bits.data
  amo_arbiter.io.reg_val := reg_mem1_mem2.io.mem_wdata_out
  amo_arbiter.io.mem_type := reg_mem1_mem2.io.inst_info_out.memType

  reservation.io.push := reg_mem1_mem2.io.inst_info_out.wbEnable === wenRes
  reservation.io.push_is_word := reg_mem1_mem2.io.inst_info_out.memType === memWord
  reservation.io.push_addr := reg_mem1_mem2.io.alu_val_out
  reservation.io.compare := reg_mem1_mem2.io.inst_info_out.wbSelect === wbCond
  reservation.io.compare_is_word := reg_mem1_mem2.io.inst_info_out.memType === memWord
  reservation.io.compare_addr := reg_mem1_mem2.io.alu_val_out
  reservation.io.flush := false.B
  reservation.io.sc_mem_resp := io.dmem.resp.valid

  stall_req_mem2_atomic := io.dmem.req.valid && !io.dmem.resp.valid || amo_arbiter.io.stall_req

  // Reg MEM2 WB
  // TODO *** CAUTION ***
  // TODO some redundent registers are only for debug, they should be removed
  reg_mem2_wb.io.last_stage_atomic_stall_req := stall_req_mem2_atomic
  reg_mem2_wb.io.next_stage_atomic_stall_req := false.B
  reg_mem2_wb.io.stall := stall_mem2_wb
  reg_mem2_wb.io.flush_one := false.B
  reg_mem2_wb.io.bubble_in := reg_mem1_mem2.io.bubble_out || stall_req_mem2_atomic
  reg_mem2_wb.io.inst_in := reg_mem1_mem2.io.inst_out
  reg_mem2_wb.io.pc_in := reg_mem1_mem2.io.pc_out
  reg_mem2_wb.io.inst_info_in := reg_mem1_mem2.io.inst_info_out
  reg_mem2_wb.io.inst_addr_misaligned_in := reg_mem1_mem2.io.inst_addr_misaligned_out
  reg_mem2_wb.io.alu_val_in := reg_mem1_mem2.io.alu_val_out
  reg_mem2_wb.io.mem_wdata_in := reg_mem1_mem2.io.mem_wdata_out
  reg_mem2_wb.io.timer_int_in := reg_mem1_mem2.io.timer_int_out
  reg_mem2_wb.io.software_int_in := reg_mem1_mem2.io.software_int_out
  reg_mem2_wb.io.external_int_in := reg_mem1_mem2.io.external_int_out
  reg_mem2_wb.io.expt_in := reg_mem1_mem2.io.expt_out
  reg_mem2_wb.io.int_resp_in := reg_mem1_mem2.io.int_resp_out
  reg_mem2_wb.io.csr_val_in := reg_mem1_mem2.io.csr_val_out
  reg_mem2_wb.io.mem_val_in := Mux(amo_arbiter.io.force_mem_val_out, amo_arbiter.io.mem_val_out,
    Mux(reservation.io.compare, (!reservation.io.succeed).asUInt, io.dmem.resp.bits.data))
  reg_mem2_wb.io.inst_af_in := reg_mem1_mem2.io.inst_af_out
  reg_mem2_wb.io.next_stage_flush_req := false.B

  // Register File
  reg_file.io.wen := (reg_mem2_wb.io.inst_info_out.wbEnable === wenReg ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRW ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRC ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRS ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenRes ||
    reg_mem2_wb.io.inst_info_out.wbSelect === wbCond) && reg_mem2_wb.io.expt_out === false.B
  reg_file.io.rd_addr := reg_mem2_wb.io.inst_out(11, 7)
  reg_file.io.rd_data := MuxLookup(reg_mem2_wb.io.inst_info_out.wbSelect,
    "hdeadbeef".U, Seq(
      wbALU -> reg_mem2_wb.io.alu_val_out,
      wbMEM -> reg_mem2_wb.io.mem_val_out,
      wbPC -> (reg_mem2_wb.io.pc_out + 4.U),
      wbCSR -> reg_mem2_wb.io.csr_val_out,
      wbCond -> reg_mem2_wb.io.mem_val_out
    )
  )
  reg_file.io.rs1_addr := reg_id_exe.io.inst_out(19, 15)
  reg_file.io.rs2_addr := reg_id_exe.io.inst_out(24, 20)

  // TODO from here is difftest, which is not formally included in the CPU
  // TODO Difftest
  // TODO Don't care the low-end code style
  if (diffTest) {
    val dtest_pc = RegInit(UInt(xlen.W), 0.U)
    val dtest_inst = RegInit(UInt(xlen.W), BUBBLE)
    val dtest_wbvalid = RegInit(Bool(), false.B)
    val dtest_expt = RegInit(false.B)
    val dtest_int = RegInit(false.B)

    dtest_wbvalid := !reg_mem2_wb.io.bubble_out && !reg_mem2_wb.io.int_resp_out

    when(!stall_mem2_wb) {
      dtest_pc := Mux(reg_mem2_wb.io.inst_af_out || reg_mem2_wb.io.bubble_out || reg_mem2_wb.io.int_resp_out, dtest_pc, reg_mem2_wb.io.pc_out)
      dtest_inst := reg_mem2_wb.io.inst_out
      dtest_expt := reg_mem2_wb.io.int_resp_out
    }
    dtest_int := reg_mem2_wb.io.int_resp_out // dtest_expt & (io.int.msip | io.int.mtip)

    BoringUtils.addSource(dtest_pc, "difftestPC")
    BoringUtils.addSource(dtest_inst, "difftestInst")
    BoringUtils.addSource(dtest_wbvalid, "difftestValid")
    BoringUtils.addSource(dtest_int, "difftestInt")

    // if(pipeTrace) {
    //   printf("\t\tIF1\tIF2\tID\tEXE\tMEM1\tMEM2\tWB\t\n")
    //   printf("Stall Req\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", 0.U, stall_req_if2_atomic, 0.U, stall_req_exe_atomic || stall_req_exe_interruptable, 0.U, stall_req_mem2_atomic, 0.U)
    //   printf("Stall\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", stall_pc, stall_if1_if2, stall_if2_id, stall_id_exe, stall_exe_mem1, stall_mem1_mem2, stall_mem2_wb)
    //   printf("PC\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", pc_gen.io.pc_out(15, 0), reg_if1_if2.io.pc_out(15, 0), reg_if2_id.io.pc_out(15, 0), reg_id_exe.io.pc_out(15, 0), reg_exe_mem1.io.pc_out(15, 0), reg_mem1_mem2.io.pc_out(15, 0), reg_mem2_wb.io.pc_out(15, 0))
    //   printf("Inst\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", BUBBLE(15, 0), BUBBLE(15, 0), reg_if2_id.io.inst_out(15, 0), reg_id_exe.io.inst_out(15, 0), reg_exe_mem1.io.inst_out(15, 0), reg_mem1_mem2.io.inst_out(15, 0), reg_mem2_wb.io.inst_out(15, 0))
    //   printf("Bubb\t\t%x\t%x\t%x\t%x\t%x\t%x\t%x\n", 0.U, reg_if1_if2.io.bubble_out, reg_if2_id.io.bubble_out, reg_id_exe.io.bubble_out, reg_exe_mem1.io.bubble_out, reg_mem1_mem2.io.bubble_out, reg_mem2_wb.io.bubble_out)
    // }
//    printf("alu %x, mem %x, csr %x, ioin %x\n", reg_mem2_wb.io.alu_val_out, reg_mem2_wb.io.mem_val_out, reg_mem2_wb.io.csr_val_out, scheduler.io.rd_from_wb)
      //    printf("------> compare %x, succeed %x, push %x\n", reservation.io.compare, reservation.io.succeed, reservation.io.push)

//    printf("-------> exit flush %x, br_flush %x, pco %x, if_pco %x, \n", expt_int_flush, br_jump_flush, pc_gen.io.pc_out, reg_if2_id.io.pc_out)
//    printf("-----> Mem req valid %x addr %x, resp valid %x data %x\n", io.dmem.req.valid, io.dmem.req.bits.addr, io.dmem.resp.valid, io.dmem.resp.bits.data)
//    printf("<AMO-------regm1m2stall %x; \n", stall_mem1_mem2)
//    printf("----->instmis %x; aluout %x, pcselect %x\n", inst_addr_misaligned, alu.io.out, reg_id_exe.io.inst_info_out.pcSelect)
    when (pipeTrace.B && dtest_expt){
      // printf("[[[[[EXPT_OR_INTRESP %d,   INT_REQ %d]]]]]\n", dtest_expt, dtest_int);
    }

    // printf("Interrupt if %x exe: %x wb %x [EPC]] %x!\n", if_mtip, exe_mtip, wb_mtip, csrFile.io.epc);
    when (dtest_int) {
      // printf("Interrupt mtvec: %x stall_req %x!\n", csrFile.io.evec, csrFile.io.stall_req);
    }
    //    printf("------->stall_req %x, imenreq_valid %x, imem_pc %x, csr_out %x, dmemaddr %x!\n", csrFile.io.stall_req, io.imem.req.valid, if_pc, csrFile.io.out, io.dmem.req.bits.addr)


  }
}
