package rv64_nstage.core

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import common.Str
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

class DataPath extends Module with phvntomParams {
  val io = IO(new DataPathIO)

  // TODO forwarding machanism

  // TODO now there is not pipelined I$ and D$, so we treat it as a 5-stage pipeline
  // TODO Hence, if1 === if2 and mem1 === mem2
  val pc_gen = Module(new PcGen)
  val reg_if2_id = Module(new RegIf2Id)
  val reg_id_exe = Module(new RegIdExe)
  val branch_cond = Module(new BrCond)
  val imm_ext = Module(new ImmExt)
  val alu = Module(new ALU)
  val multiplier = Module(new Multiplier)
  val reg_exe_mem1 = Module(new RegExeMem1)
  val csr = Module(new CSR)
  val reg_mem2_wb = Module(new RegMem2Wb)
  val reg_file = Module(new RegFile)
  val scheduler = Module(new ALUScheduler)

  // Stall Request Signals
  val stall_req_if2 = WireInit(Bool(), false.B)
  val stall_req_exe = WireInit(Bool(), false.B)
  val stall_req_mem2 = WireInit(Bool(), false.B)

  // Flush Signals
  val br_jump_flush = WireInit(Bool(), false.B)
  val expt_int_flush = WireInit(Bool(), false.B)
  val error_ret_flush = WireInit(Bool(), false.B)

  // Stall Signals
  val stall_pc = WireInit(Bool(), false.B)
  val stall_if2_id = WireInit(Bool(), false.B)
  val stall_id_exe = WireInit(Bool(), false.B)
  val stall_exe_mem1 = WireInit(Bool(), false.B)
  val stall_mem2_wb = WireInit(Bool(), false.B)

  // If2 Signals
  val inst_if2 = WireInit(UInt(32.W), BUBBLE)

  // Exe Signals
  val inst_addr_misaligned = WireInit(Bool(), false.B)
  val rs1 = WireInit(UInt(xlen.W), 0.U)
  val rs2 = WireInit(UInt(xlen.W), 0.U)

  // Mem Signals
  val mem_addr_misaligned = WireInit(Bool(), false.B)

  // Stall Control Logic
  stall_mem2_wb := false.B
  stall_exe_mem1 := stall_mem2_wb || stall_req_mem2
  stall_id_exe := stall_exe_mem1 || stall_req_exe
  stall_if2_id := stall_id_exe || false.B
  stall_pc := stall_if2_id || stall_req_if2

  // PC Generator
  pc_gen.io.stall := stall_pc
  pc_gen.io.expt_int := expt_int_flush
  pc_gen.io.error_ret := error_ret_flush
  pc_gen.io.epc := csr.io.epc
  pc_gen.io.tvec := csr.io.evec
  pc_gen.io.branch_jump := br_jump_flush
  pc_gen.io.branch_pc := alu.io.out
  pc_gen.io.inst_addr_misaligned := inst_addr_misaligned

  // I$ and Stall Request
  io.imem.req.bits.addr := pc_gen.io.pc_out
  io.imem.req.bits.data := DontCare
  io.imem.req.valid := true.B
  io.imem.req.bits.wen := false.B
  io.imem.req.bits.memtype := memWordU

  inst_if2 := io.imem.resp.bits.data
  stall_req_if2 := io.imem.req.valid && !io.imem.resp.valid

  // Reg IF2 ID
  reg_if2_id.io.last_stage_stall_req := stall_req_if2
  reg_if2_id.io.stall := stall_if2_id
  reg_if2_id.io.flush_one := br_jump_flush || expt_int_flush || error_ret_flush
  reg_if2_id.io.bubble_in := stall_req_if2
  reg_if2_id.io.inst_in := inst_if2
  reg_if2_id.io.pc_in := pc_gen.io.pc_out

  // Decoder
  io.ctrl.inst := reg_if2_id.io.inst_out

  // Reg ID EXE
  reg_id_exe.io.last_stage_stall_req := false.B
  reg_id_exe.io.stall := stall_id_exe
  reg_id_exe.io.flush_one := br_jump_flush || expt_int_flush || error_ret_flush
  reg_id_exe.io.bubble_in := reg_if2_id.io.bubble_out
  reg_id_exe.io.inst_in := reg_if2_id.io.inst_out
  reg_id_exe.io.pc_in := reg_if2_id.io.pc_out
  reg_id_exe.io.inst_info_in := io.ctrl.inst_info_out

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

  br_jump_flush := branch_cond.io.branch || reg_id_exe.io.inst_info_out.pcSelect === pcJump
  inst_addr_misaligned := alu.io.out(1) && (reg_id_exe.io.inst_info_out.pcSelect === pcJump || branch_cond.io.branch)

  scheduler.io.rs1_used_exe := reg_id_exe.io.inst_info_out.ASelect === ARS1
  scheduler.io.rs1_addr_exe := reg_id_exe.io.inst_out(19, 15)
  scheduler.io.rs2_used_exe := reg_id_exe.io.inst_info_out.BSelect === BRS2
  scheduler.io.rs2_addr_exe := reg_id_exe.io.inst_out(24, 20)
  scheduler.io.rd_used_mem1 := (reg_exe_mem1.io.inst_info_out.wbEnable === wenReg ||
    reg_exe_mem1.io.inst_info_out.wbEnable === wenCSRC ||
    reg_exe_mem1.io.inst_info_out.wbEnable === wenCSRS ||
    reg_exe_mem1.io.inst_info_out.wbEnable === wenCSRW)
  scheduler.io.rd_addr_mem1 := reg_exe_mem1.io.inst_out(11, 7)
  scheduler.io.rd_used_wb := (reg_mem2_wb.io.inst_info_out.wbEnable === wenReg ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRC ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRS ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRW)
  scheduler.io.rd_addr_wb := reg_mem2_wb.io.inst_out(11, 7)
  scheduler.io.rs1_from_reg := reg_file.io.rs1_data
  scheduler.io.rs2_from_reg := reg_file.io.rs2_data
  scheduler.io.rd_from_mem1 := reg_exe_mem1.io.alu_val_out
  scheduler.io.rd_from_wb := MuxLookup(
    reg_mem2_wb.io.inst_info_out.wbSelect,
    0.U,
    Seq(
      wbALU -> reg_mem2_wb.io.alu_val_out,
      wbMEM -> reg_mem2_wb.io.mem_val_out,
      wbCSR -> reg_mem2_wb.io.csr_val_out
    )
  )

  stall_req_exe := scheduler.io.stall_req || multiplier.io.stall_req
  rs1 := scheduler.io.rs1_val
  rs2 := scheduler.io.rs2_val

  // Reg EXE MEM1
  reg_exe_mem1.io.last_stage_stall_req := stall_req_exe
  reg_exe_mem1.io.stall := stall_exe_mem1
  reg_exe_mem1.io.flush_one := expt_int_flush || error_ret_flush
  reg_exe_mem1.io.bubble_in := reg_id_exe.io.bubble_out || stall_req_exe
  reg_exe_mem1.io.inst_in := reg_id_exe.io.inst_out
  reg_exe_mem1.io.pc_in := reg_id_exe.io.pc_out
  reg_exe_mem1.io.inst_info_in := reg_id_exe.io.inst_info_out
  reg_exe_mem1.io.inst_addr_misaligned_in := inst_addr_misaligned
  reg_exe_mem1.io.alu_val_in := Mux(reg_id_exe.io.inst_info_out.mult, multiplier.io.mult_out, alu.io.out)
  reg_exe_mem1.io.mem_wdata_in := rs2
  reg_exe_mem1.io.timer_int_in := io.int.mtip
  reg_exe_mem1.io.software_int_in := io.int.msip
  reg_exe_mem1.io.external_int_in := false.B

  // CSR and Memory
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

  io.dmem.req.bits.addr := reg_exe_mem1.io.alu_val_out
  io.dmem.req.bits.data := reg_exe_mem1.io.mem_wdata_out
  io.dmem.req.valid := (reg_exe_mem1.io.inst_info_out.memType.orR && csr.io.expt === false.B)
  io.dmem.req.bits.wen := reg_exe_mem1.io.inst_info_out.wbEnable === wenMem && csr.io.expt === false.B
  io.dmem.req.bits.memtype := reg_exe_mem1.io.inst_info_out.memType

  // TODO AMOALU and D$ wrapper

  csr.io.stall := false.B
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
  csr.io.mem_type := reg_exe_mem1.io.inst_info_out.memType
  csr.io.pc_check := true.B
  csr.io.inst_access_fault := false.B
  csr.io.mem_access_fault := false.B
  csr.io.tim_int := reg_exe_mem1.io.timer_int_out
  csr.io.soft_int := reg_exe_mem1.io.software_int_out
  csr.io.external_int := reg_exe_mem1.io.external_int_out

  expt_int_flush := csr.io.expt
  error_ret_flush := csr.io.ret
  stall_req_mem2 := io.dmem.req.valid && !io.dmem.resp.valid

  // Reg MEM2 WB
  reg_mem2_wb.io.last_stage_stall_req := stall_req_mem2
  reg_mem2_wb.io.stall := stall_mem2_wb
  reg_mem2_wb.io.flush_one := false.B
  reg_mem2_wb.io.bubble_in := reg_exe_mem1.io.bubble_out || stall_req_mem2
  reg_mem2_wb.io.inst_in := reg_exe_mem1.io.inst_out
  reg_mem2_wb.io.pc_in := reg_exe_mem1.io.pc_out
  reg_mem2_wb.io.inst_info_in := reg_exe_mem1.io.inst_info_out
  reg_mem2_wb.io.inst_addr_misaligned_in := reg_exe_mem1.io.inst_addr_misaligned_out
  reg_mem2_wb.io.alu_val_in := reg_exe_mem1.io.alu_val_out
  reg_mem2_wb.io.mem_wdata_in := reg_exe_mem1.io.mem_wdata_out
  reg_mem2_wb.io.timer_int_in := reg_exe_mem1.io.timer_int_out
  reg_mem2_wb.io.software_int_in := reg_exe_mem1.io.software_int_out
  reg_mem2_wb.io.external_int_in := reg_exe_mem1.io.external_int_out
  reg_mem2_wb.io.expt_in := csr.io.expt
  reg_mem2_wb.io.int_resp_in := csr.io.int
  reg_mem2_wb.io.csr_val_in := csr.io.out
  reg_mem2_wb.io.mem_val_in := io.dmem.resp.bits.data

  // Register File
  reg_file.io.wen := (reg_mem2_wb.io.inst_info_out.wbEnable === wenReg ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRW ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRC ||
    reg_mem2_wb.io.inst_info_out.wbEnable === wenCSRS) && reg_mem2_wb.io.expt_out === false.B
  reg_file.io.rd_addr := reg_mem2_wb.io.inst_out(11, 7)
  reg_file.io.rd_data := MuxLookup(reg_mem2_wb.io.inst_info_out.wbSelect,
    "hdeadbeef".U, Seq(
    wbALU -> reg_mem2_wb.io.alu_val_out,
    wbMEM -> reg_mem2_wb.io.mem_val_out,
    wbPC -> (reg_mem2_wb.io.pc_out + 4.U),
    wbCSR -> reg_mem2_wb.io.csr_val_out)
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

    dtest_wbvalid := !reg_mem2_wb.io.bubble_out && !reg_mem2_wb.io.expt_out

    when(!stall_mem2_wb) {
      dtest_pc := reg_mem2_wb.io.pc_out
      dtest_inst := reg_mem2_wb.io.inst_out
      dtest_expt := reg_mem2_wb.io.int_resp_out
    }
    dtest_int := reg_mem2_wb.io.int_resp_out // dtest_expt & (io.int.msip | io.int.mtip)

    BoringUtils.addSource(dtest_pc, "difftestPC")
    BoringUtils.addSource(dtest_inst, "difftestInst")
    BoringUtils.addSource(dtest_wbvalid, "difftestValid")
    BoringUtils.addSource(dtest_int, "difftestInt")

//    printf("-------> exit flush %x, br_flush %x, pco %x, if_pco %x, \n", expt_int_flush, br_jump_flush, pc_gen.io.pc_out, reg_if2_id.io.pc_out)
//    printf("-----> Mem req valid %x addr %x, resp valid %x data %x\n", io.dmem.req.valid, io.dmem.req.bits.addr, io.dmem.resp.valid, io.dmem.resp.bits.data)


    when (pipeTrace.B && dtest_expt){
      // printf("[[[[[EXPT_OR_INTRESP %d,   INT_REQ %d]]]]]\n", dtest_expt, dtest_int);
    }

    // printf("Interrupt if %x exe: %x wb %x [EPC]] %x!\n", if_mtip, exe_mtip, wb_mtip, csrFile.io.epc);
    when (dtest_int) {
      // printf("Interrupt mtvec: %x stall_req %x!\n", csrFile.io.evec, csrFile.io.stall_req);
    }
    //    printf("------->stall_req %x, imenreq_valid %x, imem_pc %x, csr_out %x, dmemaddr %x!\n", csrFile.io.stall_req, io.imem.req.valid, if_pc, csrFile.io.out, io.dmem.req.bits.addr)

//    if (pipeTrace) {
//      // when (!stall) {
//      printf("      if stage \t\t exe stage \t\t wb stage \t\t debug stage\n")
//      printf("pc    %x\t %x\t %x\t %x \n", if_pc, exe_pc, wb_pc, dtest_pc)
//      printf(
//        "inst  %x\t %x\t %x\t %x \n",
//        if_inst,
//        exe_inst,
//        wb_inst,
//        dtest_inst
//      )
//      // printf("alu_in %x, alu_out %x, wb_alu %x\n", alu.io.a, alu.io.b, wb_alu)
//      printf(
//        "      if_stall [%c] \t exe_stall [%c] \t\t\t\t valid [%c]\n\n",
//        Mux(if_stall, Str("*"), Str(" ")),
//        Mux(exe_stall, Str("*"), Str(" ")),
//        Mux(dtest_wbvalid, Str("*"), Str(" "))
//      )
//    }
  }
}
