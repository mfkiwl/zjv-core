package rv64_3stage

import chisel3._
import chisel3.util._
import common.ISA._
import ControlConst._
import firrtl.PrimOps.AsAsyncReset


object ControlConst {

  val True  = true.B
  val False = false.B

  val startAddr = "h80000000".U

  // io.pcSelect
  val pcPlus4  = 0.U(3.W)
  val pcBubble = 1.U(3.W)
  val pcBranch = 2.U(3.W)
  val pcJump   = 3.U(3.W)
  val pcEPC    = 4.U(3.W)

  // ImmExt.io.instType
  val instXXX = 0.U(3.W)
  val IType = 1.U(3.W)
  val SType = 2.U(3.W)
  val BType = 3.U(3.W)
  val UType = 4.U(3.W)
  val JType = 5.U(3.W)
  val ZType = 6.U(3.W) // Zicsr

  // BrCond.io.brType
  val brXXX = 0.U(3.W)
  val beqType  = 1.U(3.W)
  val bneType  = 2.U(3.W)
  val bltType  = 3.U(3.W)
  val bgeType  = 4.U(3.W)
  val bltuType = 5.U(3.W)
  val bgeuType = 6.U(3.W)

  // io.ASelect
  val AXXX = 0.U(2.W)
  val APC = 1.U(2.W)
  val ARS1 = 2.U(2.W)

  // io.BSelect
  val BXXX = 0.U(2.W)
  val BIMM = 1.U(2.W)
  val BRS2 = 2.U(2.W)

  // ALU.io.aluType
  val aluXXX = 0.U(4.W)
  val aluADD  =  1.U(4.W)
  val aluSUB  =  2.U(4.W)
  val aluSLL  =  3.U(4.W)
  val aluSLT  =  4.U(4.W)
  val aluSLTU =  5.U(4.W)
  val aluXOR  =  6.U(4.W)
  val aluSRL  =  7.U(4.W)
  val aluSRA  =  8.U(4.W)
  val aluOR   =  9.U(4.W)
  val aluAND  = 10.U(4.W)
  val aluCPA  = 11.U(4.W)
  val aluCPB  = 12.U(4.W)

  val memXXX = 0.U(3.W)
  val memByte   = 1.U(3.W)
  val memHalf   = 2.U(3.W)
  val memWord   = 3.U(3.W)
  val memDouble = 4.U(3.W)
  val memByteU  = 5.U(3.W)
  val memHalfU  = 6.U(3.W)
  val memWordU  = 7.U(3.W)

  // io.wbSelect
  val wbXXX = 0.U(3.W)
  val wbALU = 1.U(3.W)
  val wbMEM = 2.U(3.W)
  val wbPC  = 3.U(3.W)
  val wbCSR = 4.U(3.W)

  // sign extend or unsigned extend
  val unsignedExt = 0.U(1.W)
  val signedExt = 1.U(1.W)

  // Following is From riscv-sodor:
  // The Bubble Instruction (Machine generated NOP)
  // Insert (XOR x0,x0,x0) which is different from software compiler
  // generated NOPs which are (ADDI x0, x0, 0).
  // Reasoning for this is to let visualizers and stat-trackers differentiate
  // between software NOPs and machine-generated Bubbles in the pipeline.
  val BUBBLE  = "b00000000000000000100000000110011".U(32.W)  // 0x000004033

}

class ControlPathIO extends Bundle with phvntomParams {
  val inst      = Input(UInt(xlen.W))
  val instType  = Output(UInt(3.W))
  val pcSelect  = Output(UInt(2.W))
  val bubble    = Output(Bool())
  val brType    = Output(UInt(3.W))
  val ASelect   = Output(UInt(2.W))
  val BSelect   = Output(UInt(2.W))
  val aluType   = Output(UInt(4.W))
  val memType   = Output(UInt(3.W))
  val wbSelect  = Output(UInt(3.W))
  val wbEnable  = Output(Bool())
  val extType   = Output(UInt(1.W))   // 0 for unsinged, 1 for signed
  val ignoreUp  = Output(Bool())
}

class ControlPath extends Module with phvntomParams {
  val io = IO(new ControlPathIO)

  val controlSignal = ListLookup(io.inst,
                     List(instXXX, pcPlus4, False, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, False, unsignedExt, False),
    Array(         /*      Inst  |   PC   |  Bubble | Branch |   A    |   B    |  alu   |  Mem  |     wb    |  wb    |  Ext   | ignore  */
                   /*      Type  | Select |         |  Type  | Select | Select |  Type  | Type  |   Select  | Enable |  sign  |  Up     */
        LUI       -> List(UType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbALU,   True,    signedExt, False),
        AUIPC     -> List(UType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbALU,   True,    signedExt, False),
        JAL       -> List(JType,   pcJump,   True,    brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbPC,    True,    signedExt, False),
        JALR      -> List(IType,   pcJump,   True,    brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbPC,    True,    signedExt, False),
        BEQ       -> List(BType,   pcBranch, False,   beqType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False,   signedExt, False),
        BNE       -> List(BType,   pcBranch, False,   bneType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False,   signedExt, False),
        BLT       -> List(BType,   pcBranch, False,   bltType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False,   signedExt, False),
        BGE       -> List(BType,   pcBranch, False,   bgeType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False,   signedExt, False),
        BLTU      -> List(BType,   pcBranch, False,   bltuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False,   signedExt, False),
        BGEU      -> List(BType,   pcBranch, False,   bgeuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False,   signedExt, False),
        LB        -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memByte,   wbMEM,   True,    signedExt, False),
        LH        -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memHalf,   wbMEM,   True,    signedExt, False),
        LW        -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memWord,   wbMEM,   True,    signedExt, False),
        LBU       -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memByteU,  wbMEM,   True,    signedExt, False),
        LHU       -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memHalfU,  wbMEM,   True,    signedExt, False),
        SB        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memByte,   wbXXX,   False,   signedExt, False),
        SH        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memHalf,   wbXXX,   False,   signedExt, False),
        SW        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memWord,   wbXXX,   False,   signedExt, False),
        ADDI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memXXX,    wbALU,   True,    signedExt, False),
        SLTI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLT,  memXXX,    wbALU,   True,    signedExt, False),
        SLTIU     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLTU, memXXX,    wbALU,   True,    signedExt, False),
        XORI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluXOR,  memXXX,    wbALU,   True,    signedExt, False),
        ORI       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluOR,   memXXX,    wbALU,   True,    signedExt, False),
        ANDI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluAND,  memXXX,    wbALU,   True,    signedExt, False),
        SLLI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLL,  memXXX,    wbALU,   True,    signedExt, False),
        SRLI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRL,  memXXX,    wbALU,   True,    signedExt, False),
        SRAI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRA,  memXXX,    wbALU,   True,    signedExt, False),
        ADD       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluADD,  memXXX,    wbALU,   True,    signedExt, False),
        SUB       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSUB,  memXXX,    wbALU,   True,    signedExt, False),
        SLL       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLL,  memXXX,    wbALU,   True,    signedExt, False),
        SLT       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLT,  memXXX,    wbALU,   True,    signedExt, False),
        SLTU      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLTU, memXXX,    wbALU,   True,    signedExt, False),
        XOR       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXOR,  memXXX,    wbALU,   True,    signedExt, False),
        SRL       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRL,  memXXX,    wbALU,   True,    signedExt, False),
        SRA       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRA,  memXXX,    wbALU,   True,    signedExt, False),
        OR        -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluOR,   memXXX,    wbALU,   True,    signedExt, False),
        AND       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluAND,  memXXX,    wbALU,   True,    signedExt, False),
        FENCE     -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False,   signedExt, False),
        ECALL     -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False,   signedExt, False),
        EBREAK    -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False,   signedExt, False),
        LWU       -> List(instXXX, pcBubble, True,    brXXX,    ARS1,    BXXX,   aluADD,  memWordU,  wbMEM,   True,    signedExt, False),
        LD        -> List(instXXX, pcBubble, True,    brXXX,    ARS1,    BXXX,   aluADD,  memDouble, wbMEM,   True,    signedExt, False),
        SD        -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluADD,  memDouble, wbXXX,   False,   signedExt, False),
        ADDIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memXXX,    wbALU,   True,    signedExt,  True),
        SLLIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLL,  memXXX,    wbALU,   True,    signedExt,  True),
        SRLIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRL,  memXXX,    wbALU,   True,    signedExt,  True),
        SRAIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRA,  memXXX,    wbALU,   True,    signedExt,  True),
        ADDW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True,    signedExt,  True),
        SUBW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True,    signedExt,  True),
        SLLW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True,    signedExt,  True),
        SRLW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True,    signedExt,  True),
        SRAW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True,    signedExt,  True),
        FENCE_I   -> List(IType,   pcBubble, True,    brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False,   signedExt, False),
        CSRRW     -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbCSR,   True,    signedExt, False),
        CSRRS     -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbCSR,   True,    signedExt, False),
        CSRRC     -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbCSR,   True,    signedExt, False),
        CSRRWI    -> List(ZType,   pcBubble, True,    brXXX,    AXXX,    BIMM,   aluXXX,  memXXX,    wbCSR,   True,    signedExt, False),
        CSRRSI    -> List(ZType,   pcBubble, True,    brXXX,    AXXX,    BIMM,   aluXXX,  memXXX,    wbCSR,   True,    signedExt, False),
        CSRRCI    -> List(ZType,   pcBubble, True,    brXXX,    AXXX,    BIMM,   aluXXX,  memXXX,    wbCSR,   True,    signedExt, False),
        MRET      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False,   signedExt, False),
    )
  )

  io.instType := controlSignal(0)
  io.pcSelect := controlSignal(1)
  io.bubble   := controlSignal(2)
  io.brType   := controlSignal(3)

  io.ASelect  := controlSignal(4)
  io.BSelect  := controlSignal(5)
  io.aluType  := controlSignal(6)

  io.memType  := controlSignal(7)
  io.wbSelect := controlSignal(8)
  io.wbEnable := controlSignal(9)

  io.extType  := controlSignal(10)
  io.ignoreUp := controlSignal(11)
}

