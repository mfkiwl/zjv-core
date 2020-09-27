package rv64_3stage

import chisel3._
import chisel3.util._
import ControlConst._
import chisel3.util.experimental.BoringUtils

class BrCondIO extends Bundle with phvntomParams {
  val rs1 = Input(UInt(xlen.W))
  val rs2 = Input(UInt(xlen.W))
  val brType = Input(UInt(3.W))
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
  val instType = Input(UInt(3.W))
  val extType = Input(UInt(1.W))
  val out = Output(UInt(xlen.W))
}

class ImmExt extends Module with phvntomParams {
  val io = IO(new ImmExtIO)

  // unsigned
  val IImm = Cat(Fill(20, io.inst(31)), io.inst(31, 20))
  val SImm = Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val BImm = Cat(Fill(19, io.inst(31)), io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U)
  val UImm = Cat(io.inst(31, 12), Fill(12, 0.U))
  val JImm = Cat(Fill(11, io.inst(31)), io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U)
  val ZImm = Cat(Fill(27, 0.U), io.inst(19, 15))

  // signed
  val SIImm = Cat(Fill(52, io.inst(31)), io.inst(31, 20))
  val SSImm = Cat(Fill(52, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val SBImm = Cat(Fill(51, io.inst(31)), io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U)
  val SUImm = Cat(Fill(32, io.inst(31)), io.inst(31, 12), Fill(12, 0.U))
  val SJImm = Cat(Fill(43, io.inst(31)), io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U)
  val SZImm = Cat(Fill(27, 0.U), io.inst(19, 15))

  when(io.extType === 0.U) {
    io.out := MuxLookup(io.instType, "hdeadbeef".U, Seq(
      IType -> IImm, SType -> SImm, BType -> BImm, UType -> UImm, JType -> JImm, ZType -> ZImm))
  }.otherwise {
    io.out := MuxLookup(io.instType, "hdeadbeef".U, Seq(
      IType -> SIImm, SType -> SSImm, BType -> SBImm, UType -> SUImm, JType -> SJImm, ZType -> SZImm))
  }
}

class ALUIO() extends Bundle with phvntomParams {
  val a = Input(UInt(xlen.W))
  val b = Input(UInt(xlen.W))
  val opType = Input(UInt(4.W))
  val ignoreUp = Input(Bool())
  val out = Output(UInt(xlen.W))
  val zero = Output(Bool())
}

class ALU extends Module with phvntomParams {
  val io = IO(new ALUIO)

  val shamt = io.b(bitWidth - 1, 0)
  val lower =  MuxLookup(io.opType, "hdeadbeef".U, Seq(
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
  ))

  when(io.ignoreUp) {
    io.out := Cat(Fill(32, lower(31)), lower(31, 0))
  }.otherwise {
    io.out := lower
  }

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
  val exe_inst = RegInit(UInt(32.W), BUBBLE)
  val exe_pc = RegInit(UInt(xlen.W), 0.U)

  /* Execute / Write Back Register */
  val wb_inst = RegInit(UInt(32.W), BUBBLE)
  val wb_pc = RegInit(UInt(xlen.W), 0.U)
  val wb_alu = Reg(UInt(xlen.W))
  val wb_wdata = Reg(UInt(xlen.W))

  // Control Signal of Write Back Stage (1 cycle delay)
  val wb_memType = Reg(UInt())
  val wb_select = Reg(UInt())
  val wen = Reg(Bool())

  // ******************************
  //    Instruction Fetch Stage
  // ******************************

  val stall = !io.imem.resp.valid || (wb_memType.orR && !io.dmem.resp.valid)
  val if_pc = RegInit(UInt(xlen.W), startAddr)
  val if_pc_4 = if_pc + 4.U(xlen.W)
  val if_npc = Mux(stall, if_pc,
    MuxLookup(io.ctrl.pcSelect, if_pc_4, Seq(
      pcPlus4 -> if_pc_4,
      pcBubble -> if_pc,
      pcBranch -> Mux(brCond.io.branch, alu.io.out, if_pc_4),
      pcJump -> alu.io.out,
      pcEPC -> 0.U(xlen.W) /*TODO*/)))

  when(!stall) {
    if_pc := if_npc
  }

  io.imem.req.bits.addr := if_pc
  io.imem.req.bits.data := DontCare
  val if_inst = io.imem.resp.bits.data

  io.imem.req.valid := true.B
  io.imem.req.bits.wen := false.B
  io.imem.req.bits.memtype := memWordU

  when((io.ctrl.brType =/= ControlConst.brXXX || io.ctrl.bubble) && brCond.io.branch) {
    exe_pc := if_pc
    exe_inst := BUBBLE
  }.elsewhen(!stall) {
    exe_pc := if_pc
    exe_inst := if_inst
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

  val rs1Hazard = wen && rs1_addr.orR && (rs1_addr === rd_addr)
  val rs2Hazard = wen && rs2_addr.orR && (rs2_addr === rd_addr)

  val rs1 = Mux(wb_select === wbALU && rs1Hazard, wb_alu, regFile.io.rs1_data)
  val rs2 = Mux(wb_select === wbALU && rs2Hazard, wb_alu, regFile.io.rs2_data)

  immExt.io.inst := exe_inst
  immExt.io.instType := io.ctrl.instType
  immExt.io.extType := io.ctrl.extType

  brCond.io.rs1 := rs1
  brCond.io.rs2 := rs2
  brCond.io.brType := io.ctrl.brType

  alu.io.opType := io.ctrl.aluType
  alu.io.a := Mux(io.ctrl.ASelect === APC, exe_pc, rs1)
  alu.io.b := Mux(io.ctrl.BSelect === BIMM, immExt.io.out, rs2)
  alu.io.ignoreUp := io.ctrl.ignoreUp

  when(!stall) {
    wb_pc := exe_pc
    wb_inst := exe_inst
    wb_wdata := rs2

    wb_memType := io.ctrl.memType
    wb_select := io.ctrl.wbSelect
    wen := io.ctrl.wbEnable
  }

  // ******************************
  //        Write Back Stage
  // ******************************
  val wb_pc_4 = wb_pc + 4.U(xlen.W)

  wb_alu := alu.io.out

  io.dmem.req.bits.addr := wb_alu
  io.dmem.req.bits.data := wb_wdata

  io.dmem.req.valid := wb_memType.orR
  io.dmem.req.bits.wen := wb_memType.orR
  io.dmem.req.bits.memtype := wb_memType

  regFile.io.wen := wen
  regFile.io.rd_addr := rd_addr
  regFile.io.rd_data := MuxLookup(wb_select, "hdeadbeef".U, Seq(
    wbALU -> wb_alu,
    wbMEM -> io.dmem.resp.bits.data,
    wbPC -> wb_pc_4,
    wbCSR -> 0.U(xlen.W) /*TODO*/))


  // Difftest
  if (diffTest) {
    val dtest_pc = RegInit(UInt(xlen.W), 0.U)
    val dtest_inst = RegInit(UInt(xlen.W), 0.U)
    val dtest_valid = RegInit(Bool(), true.B)

    dtest_valid := !(stall || wb_inst(31, 0) === ControlConst.BUBBLE)
    when(!stall) {
      dtest_pc := wb_pc
      dtest_inst := wb_inst
    }

    BoringUtils.addSource(dtest_pc, "difftestPc")
    BoringUtils.addSource(dtest_inst, "difftestInst")
    BoringUtils.addSource(dtest_valid, "difftestValid")
  }

  //  printf("if_pc=[%x] if_inst=%x exe_pc=[%x] Inst=%x stall=%x instRype=%x pcSelect=%x\n",
  //    if_pc,
  //    if_inst,
  //    exe_pc,
  //    exe_inst,
  //    stall,
  //    io.ctrl.instType,
  //    io.ctrl.pcSelect
  //  )


}
