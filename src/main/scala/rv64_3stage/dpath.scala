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
}

class DataPath extends Module with phvntomParams {
  val io = IO(new DataPathIO)

  // Module Used
  val regFile = Module(new RegFile)
  val immExt = Module(new ImmExt)
  val brCond = Module(new BrCond)
  val alu = Module(new ALU)

  /* Fetch / Execute Register */
  val exe_inst = RegInit(UInt(xlen.W), BUBBLE)
  val exe_pc = RegInit(UInt(xlen.W), 0.U)

  /* Execute / Write Back Register */
  val wb_inst = RegInit(UInt(xlen.W), BUBBLE)
  val wb_pc = RegInit(UInt(xlen.W), 0.U)
  val wb_alu = Reg(UInt(xlen.W))
  val wb_wdata = Reg(UInt(xlen.W))

  // Control Signal of Write Back Stage (1 cycle delay)
  val wb_memType = Reg(UInt())
  val wb_select = Reg(UInt())
  val wen = Reg(UInt())

  // ******************************
  //    Instruction Fetch Stage
  // ******************************

  val istall = !io.imem.resp.valid
  val dstall = (wb_memType.orR && !io.dmem.resp.valid)
  val if_stall = istall || dstall
  val exe_stall = dstall
  val if_pc = RegInit(UInt(xlen.W), startAddr)
  val if_pc_4 = if_pc + 4.U(xlen.W)
  val if_npc = Mux(
    if_stall,
    if_pc,
    MuxLookup(
      io.ctrl.pcSelect,
      if_pc_4,
      Seq(
        pcPlus4 -> if_pc_4,
        pcBubble -> if_pc,
        pcBranch -> Mux(brCond.io.branch, alu.io.out, if_pc_4),
        pcJump -> alu.io.out,
        pcEPC -> 0.U(xlen.W) /*TODO*/
      )
    )
  )

  when(!if_stall) {
    if_pc := if_npc
  }.elsewhen(brCond.io.branch || io.ctrl.pcSelect === pcJump) {
    if_pc := alu.io.out
  }

  io.imem.req.bits.addr := if_pc
  io.imem.req.bits.data := DontCare
  val if_inst = io.imem.resp.bits.data

  io.imem.req.valid := !io.ctrl.bubble
  io.imem.req.bits.wen := false.B
  io.imem.req.bits.memtype := memWordU

  when(!exe_stall) {
    when(io.ctrl.bubble || brCond.io.branch || istall) {
      exe_pc := if_pc
      exe_inst := BUBBLE
    }.otherwise {
      exe_pc := if_pc
      exe_inst := if_inst
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
        Mux(wb_select === wbCSR && rs1Hazard, 0.U(xlen.W), regFile.io.rs1_data)
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
        Mux(wb_select === wbCSR && rs2Hazard, 0.U(xlen.W), regFile.io.rs2_data)
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

  when(!exe_stall) {
    wb_pc := exe_pc
    wb_inst := exe_inst
    wb_alu := alu.io.out
    wb_wdata := rs2

    wb_memType := io.ctrl.memType
    wb_select := io.ctrl.wbSelect
    wen := io.ctrl.wbEnable
  }

  // ******************************
  //        Write Back Stage
  // ******************************
  val wb_pc_4 = wb_pc + 4.U(xlen.W)

  io.dmem.req.bits.addr := wb_alu
  io.dmem.req.bits.data := wb_wdata

  io.dmem.req.valid := wb_memType.orR
  io.dmem.req.bits.wen := wen === wenMem
  io.dmem.req.bits.memtype := wb_memType

  regFile.io.wen := wen === wenReg && !exe_stall
  regFile.io.rd_addr := rd_addr
  regFile.io.rd_data := MuxLookup(
    wb_select,
    "hdeadbeef".U,
    Seq(
      wbALU -> wb_alu,
      wbMEM -> io.dmem.resp.bits.data,
      wbPC -> wb_pc_4,
      wbCSR -> 0.U(xlen.W) /*TODO*/
    )
  )

  // printf(
  //   "RegFile: wen = %d, rd_addr = %x, rd_data = %x, rs1_addr = %x, rs1_data = %x, rs2_addr = %x, rs2_data = %x\n",
  //   regFile.io.wen,
  //   regFile.io.rd_addr,
  //   regFile.io.rd_data,
  //   regFile.io.rs1_addr,
  //   regFile.io.rs1_data,
  //   regFile.io.rs2_addr,
  //   regFile.io.rs2_data
  // )

  // Difftest
  if (diffTest) {
    val dtest_pc = RegInit(UInt(xlen.W), 0.U)
    val dtest_inst = RegInit(UInt(xlen.W), BUBBLE)
    val dtest_wbvalid = WireInit(Bool(), false.B)
    val dtest_trmt = WireInit(Bool(), false.B)

    dtest_wbvalid := !(exe_stall || dtest_inst(31, 0) === ControlConst.BUBBLE)
    dtest_trmt := dtest_inst(31, 0) === ControlConst.TRMT

    when(!exe_stall) {
      dtest_pc := wb_pc
      dtest_inst := wb_inst
    }

    BoringUtils.addSource(dtest_pc, "difftestPC")
    BoringUtils.addSource(dtest_inst, "difftestInst")
    BoringUtils.addSource(dtest_wbvalid, "difftestValid")
    BoringUtils.addSource(dtest_trmt, "difftestTerminate")

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
      printf(
        "      if_stall [%c] \t\texe_stall [%c]\t\t\t valid [%c]\n\n",
        Mux(if_stall, Str("*"), Str(" ")),
        Mux(exe_stall, Str("*"), Str(" ")),
        Mux(dtest_wbvalid, Str("*"), Str(" "))
      )
      // }
    }
  }
}
