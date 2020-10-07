package rv64_3stage

import chisel3._
import chisel3.util._
import ControlConst._
import chisel3.util.experimental.BoringUtils

import common._

class BrCondIO extends Bundle with phvntomParams {
  val rs1 = Input(UInt(xlen.W))
  val rs2 = Input(UInt(xlen.W))
  val brType = Input(UInt(brBits.W))
  val branch = Output(Bool())
}

class BrCond extends Module with phvntomParams {
  val io = IO(new BrCondIO)

  val eq = io.rs1 === io.rs2
  val neq = !eq
  val lt = io.rs1.asSInt < io.rs2.asSInt
  val ge = !lt
  val ltu = io.rs1 < io.rs2
  val geu = !ltu
  io.branch :=
    ((io.brType === beqType) && eq) ||
      ((io.brType === bneType) && neq) ||
      ((io.brType === bltType) && lt) ||
      ((io.brType === bgeType) && ge) ||
      ((io.brType === bltuType) && ltu) ||
      ((io.brType === bgeuType) && geu)
}

class ImmExtIO extends Bundle with phvntomParams {
  val inst = Input(UInt(xlen.W))
  val instType = Input(UInt(instBits.W))
  val out = Output(UInt(xlen.W))
}

class ImmExt extends Module with phvntomParams {
  val io = IO(new ImmExtIO)

  // unsigned
  val IImm = Cat(Fill(20, io.inst(31)), io.inst(31, 20))
  val SImm = Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val BImm = Cat(
    Fill(19, io.inst(31)),
    io.inst(31),
    io.inst(7),
    io.inst(30, 25),
    io.inst(11, 8),
    0.U
  )
  val UImm = Cat(io.inst(31, 12), Fill(12, 0.U))
  val JImm = Cat(
    Fill(11, io.inst(31)),
    io.inst(31),
    io.inst(19, 12),
    io.inst(20),
    io.inst(30, 21),
    0.U
  )
  val ZImm = Cat(Fill(27, 0.U), io.inst(19, 15))

  val imm_32 = MuxLookup(
    io.instType,
    "hdeadbeef".U,
    Seq(
      IType -> IImm,
      SType -> SImm,
      BType -> BImm,
      UType -> UImm,
      JType -> JImm,
      ZType -> ZImm
    )
  )

  io.out := Cat(Fill(32, imm_32(31)), imm_32)
}

class ALUIO() extends Bundle with phvntomParams {
  val a = Input(UInt(xlen.W))
  val b = Input(UInt(xlen.W))
  val opType = Input(UInt(aluBits.W))
  val out = Output(UInt(xlen.W))
  val zero = Output(Bool())
}

class ALU extends Module with phvntomParams {
  val io = IO(new ALUIO)

  val shamt = io.b(bitWidth - 1, 0)
  def sign_ext32(a: UInt): UInt = { Cat(Fill(32, a(31)), a(31, 0)) }
  io.out := MuxLookup(
    io.opType,
    "hdeadbeef".U,
    Seq(
      aluADD -> (io.a + io.b),
      aluSUB -> (io.a - io.b),
      aluSLL -> (io.a << shamt),
      aluSLT -> (io.a.asSInt < io.b.asSInt),
      aluSLTU -> (io.a < io.b),
      aluXOR -> (io.a ^ io.b),
      aluSRL -> (io.a >> shamt),
      aluSRA -> (io.a.asSInt >> shamt).asUInt,
      aluOR -> (io.a | io.b),
      aluAND -> (io.a & io.b),
      aluCPA -> io.a,
      aluCPB -> io.b,
      aluADDW -> sign_ext32(io.a(31, 0) + io.b(31, 0)),
      aluSUBW -> sign_ext32(io.a(31, 0) - io.b(31, 0)),
      aluSLLW -> sign_ext32(io.a(31, 0) << shamt(4, 0)),
      aluSRLW -> sign_ext32(io.a(31, 0) >> shamt(4, 0)),
      aluSRAW -> sign_ext32((io.a(31, 0).asSInt >> shamt(4, 0)).asUInt)
    )
  )

  io.zero := ~io.out.orR

  //  printf(p"${io.opType} rs1: ${Hexadecimal(io.rs1)} rs2: ${Hexadecimal(io.rs2)} rd: ${Hexadecimal(io.rd)} zero: ${Hexadecimal(io.zero)}\n")
}

class DataPathIO extends Bundle with phvntomParams {
  val ctrl = Flipped(new ControlPathIO)

  val imem = Flipped(new MemIO)
  val dmem = Flipped(new MemIO)

  val int = Flipped(Flipped(new InterruptIO))
}

class DataPath extends Module with phvntomParams {
  val io = IO(new DataPathIO)

  // Module Used
  val regFile = Module(new RegFile)
  val immExt = Module(new ImmExt)
  val brCond = Module(new BrCond)
  val alu = Module(new ALU)
  val csrFile = Module(new CSR)

  /* Fetch / Execute Register */
  val if_mtip = RegInit(Bool(), false.B)
  val exe_inst = RegInit(UInt(xlen.W), BUBBLE)
  val exe_pc = RegInit(UInt(xlen.W), 0.U)
  val exe_inst_access_fault = RegInit(Bool(), false.B)
  val exe_mtip = RegInit(Bool(), false.B)

  /* Execute / Write Back Register */
  val inst_addr_misaligned = WireInit(false.B)
  val wb_inst = RegInit(UInt(xlen.W), BUBBLE)
  val wb_pc = RegInit(UInt(xlen.W), 0.U)
  val wb_alu = Reg(UInt(xlen.W))
  val wb_wdata = Reg(UInt(xlen.W))
  val wb_inst_addr_misaligned = RegInit(Bool(), false.B)
  val wb_inst_access_fault = RegInit(Bool(), false.B)
  val wb_mtip = RegInit(Bool(), false.B)

  // Control Signal of Write Back Stage (1 cycle delay)
  val wb_memType = Reg(UInt())
  val wb_select = Reg(UInt())
  val wen = Reg(UInt())
  val wb_illegal = RegInit(false.B)

  // ******************************
  //    Instruction Fetch Stage
  // ******************************

  val if_pc = RegInit(UInt(xlen.W), startAddr)
  val if_pc_4 = if_pc + 4.U(xlen.W)
  val inst_access_fault = if_pc(xlen - 1, 48).orR
  val istall = (!io.imem.resp.valid && !inst_access_fault)
  val dstall = (io.dmem.req.valid && !io.dmem.resp.valid)
  val if_stall = istall || dstall
  val exe_stall = dstall

  val if_npc = Mux(
    csrFile.io.expt,
    csrFile.io.evec,
    Mux(
      csrFile.io.ret,
      csrFile.io.epc,
      Mux(
        if_stall,
        if_pc,
        MuxLookup(
          io.ctrl.pcSelect,
          if_pc_4,
          Seq(
            pcPlus4 -> if_pc_4,
            pcBubble -> if_pc,
            pcBranch -> Mux(brCond.io.branch, Mux(inst_addr_misaligned, if_pc_4, alu.io.out), if_pc_4),
            pcJump -> Mux(inst_addr_misaligned, if_pc_4, Cat(alu.io.out(xlen - 1, 1), Fill(1, 0.U)))
          )
        )
      )
    )
  )

  when(csrFile.io.expt) {
    if_pc := csrFile.io.evec
    if_mtip := io.int.mtip
  }.elsewhen(csrFile.io.ret) {
    if_pc := csrFile.io.epc
    if_mtip := io.int.mtip
  }.elsewhen(brCond.io.branch || io.ctrl.pcSelect === pcJump) {
    if_pc := Cat(alu.io.out(xlen - 1, 1), Fill(1, 0.U))
    if_mtip := io.int.mtip
  }.elsewhen(!if_stall) {
    if_pc := if_npc
    if_mtip := io.int.mtip
  }

  io.imem.req.bits.addr := if_pc
  io.imem.req.bits.data := DontCare
  val if_inst = io.imem.resp.bits.data

  io.imem.req.valid := !io.ctrl.bubble && !inst_access_fault && !csrFile.io.stall_req
  io.imem.req.bits.wen := false.B
  io.imem.req.bits.memtype := memWordU

  when(!exe_stall) {
    exe_pc := if_pc
    exe_mtip := if_mtip
    when(io.ctrl.bubble || brCond.io.branch || istall || csrFile.io.expt || csrFile.io.ret) {
      exe_inst := BUBBLE
      exe_inst_access_fault := false.B
    }.otherwise {
      exe_inst := if_inst
      exe_inst_access_fault := inst_access_fault
    }
  }

  // ******************************
  //        Execute Stage
  // ******************************

  io.ctrl.inst := exe_inst

  val rs1_addr = exe_inst(19, 15)
  val rs2_addr = exe_inst(24, 20)
  val rd_addr = wb_inst(11, 7)

  regFile.io.rs1_addr := rs1_addr
  regFile.io.rs2_addr := rs2_addr

  val rs1Hazard = wen.orR && rs1_addr.orR && (rs1_addr === rd_addr)
  val rs2Hazard = wen.orR && rs2_addr.orR && (rs2_addr === rd_addr)

  val rs1 = Mux(
    wb_select === wbALU && rs1Hazard,
    wb_alu,
    Mux(
      wb_select === wbMEM && rs1Hazard,
      io.dmem.resp.bits.data,
      Mux(
        wb_select === wbPC && rs1Hazard,
        wb_pc + 4.U(xlen.W),
        Mux(wb_select === wbCSR && rs1Hazard, csrFile.io.out, regFile.io.rs1_data)
      )
    )
  )

  val rs2 = Mux(
    wb_select === wbALU && rs2Hazard,
    wb_alu,
    Mux(
      wb_select === wbMEM && rs2Hazard,
      io.dmem.resp.bits.data,
      Mux(
        wb_select === wbPC && rs2Hazard,
        wb_pc + 4.U(xlen.W),
        Mux(wb_select === wbCSR && rs2Hazard, csrFile.io.in, regFile.io.rs2_data)
      )
    )
  )

  immExt.io.inst := exe_inst
  immExt.io.instType := io.ctrl.instType

  brCond.io.rs1 := rs1
  brCond.io.rs2 := rs2
  brCond.io.brType := io.ctrl.brType
  // printf(
  //   "br_rs1 = %x, br_rs2 = %x, br_type = %d, br_branch = %d\n",
  //   brCond.io.rs1,
  //   brCond.io.rs2,
  //   brCond.io.brType,
  //   brCond.io.branch
  // )

  alu.io.opType := io.ctrl.aluType
  alu.io.a := Mux(io.ctrl.ASelect === APC, exe_pc, rs1)
  alu.io.b := Mux(io.ctrl.BSelect === BIMM, immExt.io.out, rs2)
  inst_addr_misaligned := alu.io.out(1) && (io.ctrl.pcSelect === pcJump || brCond.io.branch)

  when(!exe_stall) {
    wb_pc := exe_pc
    wb_alu := alu.io.out
    wb_wdata := rs2
    when(csrFile.io.expt || csrFile.io.ret) {
      wb_inst := BUBBLE
      wb_memType := memXXX
      wb_select := wbXXX
      wen := wenXXX
      wb_illegal := false.B
      wb_inst_addr_misaligned := false.B
      wb_inst_access_fault := false.B
      wb_mtip := false.B
    }.otherwise {
      wb_inst_addr_misaligned := inst_addr_misaligned
      wb_illegal := io.ctrl.instType === ControlConst.Illegal
      wb_inst_access_fault := exe_inst_access_fault
      when(inst_addr_misaligned || exe_inst_access_fault) {
        wb_memType := memXXX
        wb_select := wbXXX
        wen := wenXXX
      }.otherwise {
        wb_memType := io.ctrl.memType
        wb_select := io.ctrl.wbSelect
        wen := io.ctrl.wbEnable
      }
      when(exe_inst_access_fault) {
        wb_inst := BUBBLE
        wb_mtip := false.B
      }.otherwise {
        wb_inst := exe_inst
        when(exe_inst === BUBBLE) {
          wb_mtip := false.B
        }.otherwise {
          wb_mtip := io.int.mtip
        }
      }
    }
  }.otherwise {
    wb_mtip := false.B
  }

  // ******************************
  //        Write Back Stage
  // ******************************
  val mem_addr_misaligned = MuxLookup(wb_memType, false.B, Seq(
    memByte -> false.B,
    memByteU -> false.B,
    memHalf -> wb_alu(0),
    memHalfU -> wb_alu(0),
    memWord -> wb_alu(1, 0).orR,
    memWordU -> wb_alu(1, 0).orR,
    memDouble -> wb_alu(2, 0).orR
  ))
  val mem_access_fault = wb_memType.orR && wb_alu(xlen - 1, 48).orR
  val wb_pc_4 = wb_pc + 4.U(xlen.W)
  val wb_data = MuxLookup(wb_select, "hdeadbeef".U, Seq(
    wbALU -> wb_alu,
    wbMEM -> io.dmem.resp.bits.data,
    wbPC -> wb_pc_4,
    wbCSR -> csrFile.io.out))

  io.dmem.req.bits.addr := wb_alu
  io.dmem.req.bits.data := wb_wdata

  io.dmem.req.valid := wb_memType.orR && mem_addr_misaligned === false.B && mem_access_fault === false.B
  io.dmem.req.bits.wen := wen === wenMem
  io.dmem.req.bits.memtype := wb_memType

  regFile.io.wen := ((wen === wenReg &&
    csrFile.io.expt === false.B) ||
    wen === wenCSRW || wen === wenCSRC || wen === wenCSRS)
  regFile.io.rd_addr := rd_addr
  regFile.io.rd_data := wb_data

  // normal csr operations
  csrFile.io.stall := exe_stall
  csrFile.io.cmd := wen
  csrFile.io.in := wb_alu
  // minstret
  csrFile.io.bubble := wb_inst === BUBBLE
  // exception in
  csrFile.io.pc := wb_pc
  csrFile.io.illegal_mem_addr := mem_addr_misaligned
  csrFile.io.illegal_inst_addr := wb_inst_addr_misaligned
  csrFile.io.inst := wb_inst
  csrFile.io.illegal := wb_illegal
  csrFile.io.is_load := wb_memType.orR && wen =/= wenMem
  csrFile.io.is_store := wb_memType.orR && wen === wenMem
  csrFile.io.mem_type := wb_memType
  csrFile.io.pc_check := true.B
  csrFile.io.inst_access_fault := wb_inst_access_fault
  csrFile.io.mem_access_fault := mem_access_fault
  // interupt signals in, XIP from CLINT or PLIC
  csrFile.io.tim_int := wb_mtip
  csrFile.io.soft_int := io.int.msip
  csrFile.io.external_int := false.B  // TODO

  // ******************************
  //        Diff Test Stage
  // ******************************
  //printf("<----STALL IF[%x], EXE[%x]---->\n", istall, dstall)

  if (diffTest) {
    val dtest_pc = RegInit(UInt(xlen.W), 0.U)
    val dtest_inst = RegInit(UInt(xlen.W), BUBBLE)
    val dtest_wbvalid = WireInit(Bool(), false.B)
    val dtest_expt = RegInit(false.B)
    val dtest_int = RegInit(false.B)

    dtest_wbvalid := !(exe_stall || dtest_inst(31, 0) === ControlConst.BUBBLE)

    when(!exe_stall) {
      dtest_pc := wb_pc
      when(csrFile.io.expt) {
        dtest_inst := BUBBLE
      }.otherwise {
        dtest_inst := wb_inst
      }
      dtest_expt := csrFile.io.expt
    }
    dtest_int := dtest_expt & (io.int.msip | io.int.mtip)

    BoringUtils.addSource(dtest_pc, "difftestPC")
    BoringUtils.addSource(dtest_inst, "difftestInst")
    BoringUtils.addSource(dtest_wbvalid, "difftestValid")
    BoringUtils.addSource(dtest_int, "difftestInt")

    when (pipeTrace.B && dtest_expt){
      // printf("[[[[[EXPT_OR_INTRESP %d,   INT_REQ %d]]]]]\n", dtest_expt, dtest_int);
    }

    // printf("Interrupt if %x exe: %x wb %x [EPC]] %x!\n", if_mtip, exe_mtip, wb_mtip, csrFile.io.epc);
    when (dtest_int) {
      // printf("Interrupt mtvec: %x stall_req %x!\n", csrFile.io.evec, csrFile.io.stall_req);
    }
//    printf("------->stall_req %x, imenreq_valid %x, imem_pc %x, csr_out %x, dmemaddr %x!\n", csrFile.io.stall_req, io.imem.req.valid, if_pc, csrFile.io.out, io.dmem.req.bits.addr)

    if (pipeTrace) {
      // when (!stall) {
      printf("      if stage \t\t exe stage \t\t wb stage \t\t debug stage\n")
      printf("pc    %x\t %x\t %x\t %x \n", if_pc, exe_pc, wb_pc, dtest_pc)
      printf(
        "inst  %x\t %x\t %x\t %x \n",
        if_inst,
        exe_inst,
        wb_inst,
        dtest_inst
      )
      // printf("alu_in %x, alu_out %x, wb_alu %x\n", alu.io.a, alu.io.b, wb_alu)
      printf(
        "      if_stall [%c] \t exe_stall [%c] \t\t\t\t valid [%c]\n\n",
        Mux(if_stall, Str("*"), Str(" ")),
        Mux(exe_stall, Str("*"), Str(" ")),
        Mux(dtest_wbvalid, Str("*"), Str(" "))
      )
    }
  }
}
