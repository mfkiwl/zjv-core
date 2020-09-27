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
  val IType   = 1.U(3.W)
  val SType   = 2.U(3.W)
  val BType   = 3.U(3.W)
  val UType   = 4.U(3.W)
  val JType   = 5.U(3.W)
  val ZType   = 6.U(3.W) // Zicsr

  // BrCond.io.brType
  val brXXX    = 0.U(3.W)
  val beqType  = 1.U(3.W)
  val bneType  = 2.U(3.W)
  val bltType  = 3.U(3.W)
  val bgeType  = 4.U(3.W)
  val bltuType = 5.U(3.W)
  val bgeuType = 6.U(3.W)

  // io.ASelect
  val AXXX = 0.U(2.W)
  val APC  = 1.U(2.W)
  val ARS1 = 2.U(2.W)

  // io.BSelect
  val BXXX = 0.U(2.W)
  val BIMM = 1.U(2.W)
  val BRS2 = 2.U(2.W)

  // ALU.io.aluType
  val aluXXX  = 0.U(4.W)
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

  val memXXX    = 0.U(3.W)
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
}

class ControlPath extends Module with phvntomParams {
  val io = IO(new ControlPathIO)

  val controlSignal = ListLookup(io.inst,
                     List(instXXX, pcPlus4, False, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, False),
    Array(         /*      Inst  |   PC   |  Bubble | Branch |   A    |   B    |  alu   |  Mem  |     wb    |  wb    |  */
                   /*      Type  | Select |         |  Type  | Select | Select |  Type  | Type  |   Select  | Enable |  */
        LUI       -> List(UType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbALU,   True),
        AUIPC     -> List(UType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbALU,   True),
        JAL       -> List(JType,   pcJump,   True,    brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbPC,    True),
        JALR      -> List(IType,   pcJump,   True,    brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbPC,    True),
        BEQ       -> List(BType,   pcBranch, False,   beqType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False),
        BNE       -> List(BType,   pcBranch, False,   bneType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False),
        BLT       -> List(BType,   pcBranch, False,   bltType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False),
        BGE       -> List(BType,   pcBranch, False,   bgeType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False),
        BLTU      -> List(BType,   pcBranch, False,   bltuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False),
        BGEU      -> List(BType,   pcBranch, False,   bgeuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   False),
        LB        -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memByte,   wbMEM,   True),
        LH        -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memHalf,   wbMEM,   True),
        LW        -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memWord,   wbMEM,   True),
        LBU       -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memByteU,  wbMEM,   True),
        LHU       -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BIMM,   aluADD,  memHalfU,  wbMEM,   True),
        SB        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memByte,   wbXXX,   False),
        SH        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memHalf,   wbXXX,   False),
        SW        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memWord,   wbXXX,   False),
        ADDI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memXXX,    wbALU,   True),
        SLTI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLT,  memXXX,    wbALU,   True),
        SLTIU     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLTU, memXXX,    wbALU,   True),
        XORI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluXOR,  memXXX,    wbALU,   True),
        ORI       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluOR,   memXXX,    wbALU,   True),
        ANDI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluAND,  memXXX,    wbALU,   True),
        SLLI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLL,  memXXX,    wbALU,   True),
        SRLI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRL,  memXXX,    wbALU,   True),
        SRAI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRA,  memXXX,    wbALU,   True),
        ADD       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluADD,  memXXX,    wbALU,   True),
        SUB       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSUB,  memXXX,    wbALU,   True),
        SLL       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLL,  memXXX,    wbALU,   True),
        SLT       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLT,  memXXX,    wbALU,   True),
        SLTU      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLTU, memXXX,    wbALU,   True),
        XOR       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXOR,  memXXX,    wbALU,   True),
        SRL       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRL,  memXXX,    wbALU,   True),
        SRA       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRA,  memXXX,    wbALU,   True),
        OR        -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluOR,   memXXX,    wbALU,   True),
        AND       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluAND,  memXXX,    wbALU,   True),
        FENCE     -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False),
        ECALL     -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False),
        EBREAK    -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False),
        LWU       -> List(instXXX, pcBubble, True,    brXXX,    ARS1,    BXXX,   aluADD,  memWordU,  wbMEM,   True),
        LD        -> List(instXXX, pcBubble, True,    brXXX,    ARS1,    BXXX,   aluADD,  memDouble, wbMEM,   True),
        SD        -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluADD,  memDouble, wbXXX,   False),
        ADDIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memXXX,    wbALU,   True),
        SLLIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLL,  memXXX,    wbALU,   True),
        SRLIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRL,  memXXX,    wbALU,   True),
        SRAIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRA,  memXXX,    wbALU,   True),
        ADDW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True),
        SUBW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True),
        SLLW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True),
        SRLW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True),
        SRAW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbALU,   True),
        FENCE_I   -> List(IType,   pcBubble, True,    brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False),
        CSRRW     -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbCSR,   True),
        CSRRS     -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbCSR,   True),
        CSRRC     -> List(IType,   pcBubble, True,    brXXX,    ARS1,    BXXX,   aluXXX,  memXXX,    wbCSR,   True),
        CSRRWI    -> List(ZType,   pcBubble, True,    brXXX,    AXXX,    BIMM,   aluXXX,  memXXX,    wbCSR,   True),
        CSRRSI    -> List(ZType,   pcBubble, True,    brXXX,    AXXX,    BIMM,   aluXXX,  memXXX,    wbCSR,   True),
        CSRRCI    -> List(ZType,   pcBubble, True,    brXXX,    AXXX,    BIMM,   aluXXX,  memXXX,    wbCSR,   True),
        MRET      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   False),
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

}

