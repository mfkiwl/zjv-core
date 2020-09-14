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
  val aluXXX   =  0.U(5.W)
  val aluADD   =  1.U(5.W)
  val aluSUB   =  2.U(5.W)
  val aluSLL   =  3.U(5.W)
  val aluSLT   =  4.U(5.W)
  val aluSLTU  =  5.U(5.W)
  val aluXOR   =  6.U(5.W)
  val aluSRL   =  7.U(5.W)
  val aluSRA   =  8.U(5.W)
  val aluOR    =  9.U(5.W)
  val aluAND   = 10.U(5.W)
  val aluCPA   = 11.U(5.W)
  val aluCPB   = 12.U(5.W)
  val aluADDIW = 13.U(5.W)
  val aluSLLIW = 14.U(5.W)
  val aluSRLIW = 15.U(5.W)
  val aluSRAIW = 16.U(5.W)
  val aluADDW  = 17.U(5.W)
  val aluSUBW  = 18.U(5.W)
  val aluSLLW  = 19.U(5.W)
  val aluSRLW  = 20.U(5.W)
  val aluSRAW  = 21.U(5.W)

  // io.stType
  val stXXX = 0.U(3.W)
  val stByte   = 1.U(3.W)
  val stHalf   = 2.U(3.W)
  val stWord   = 3.U(3.W)
  val stDouble = 4.U(3.W)

  // io.ldType
  val ldXXX = 0.U(3.W)
  val ldByte   = 1.U(3.W)
  val ldHalf   = 2.U(3.W)
  val ldWord   = 3.U(3.W)
  val ldDouble = 4.U(3.W)
  val ldByteU  = 5.U(3.W)
  val ldHalfU  = 6.U(3.W)
  val ldWordU  = 7.U(3.W)

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
  val aluType   = Output(UInt(3.W))
  val stType    = Output(UInt(2.W))
  val ldType    = Output(UInt(3.W))
  val wbSelect  = Output(UInt(3.W))
  val wbEnable  = Output(Bool())
}

class ControlPath extends Module with phvntomParams {
  val io = IO(new ControlPathIO)

  val controlSignal = ListLookup(io.inst,
                     List(instXXX, pcPlus4, False, brXXX, AXXX, BXXX, aluXXX, stXXX, ldXXX, wbXXX, False),
    Array(         /*      Inst  |   PC   | Bubble | Branch |   A    |  */
                   /*      Type  | Select |        | Type   | Select |  */
        LUI       -> List(UType,   pcPlus4,  False, brXXX,    AXXX,    BIMM, aluCPB,  stXXX,    ldXXX,    wbALU, True),
        AUIPC     -> List(UType,   pcPlus4,  False, brXXX,    AXXX,    BIMM, aluCPB,  stXXX,    ldXXX,    wbALU, True),
        JAL       -> List(JType,   pcJump,   True,  brXXX,    APC,     BIMM, aluADD,  stXXX,    ldXXX,    wbPC,  True),
        JALR      -> List(IType,   pcJump,   True,  brXXX,    APC,     BIMM, aluADD,  stXXX,    ldXXX,    wbPC,  True),
        BEQ       -> List(BType,   pcBranch, False, beqType,  APC,     BIMM, aluADD,  stXXX,    ldXXX,    wbXXX, False),
        BNE       -> List(BType,   pcBranch, False, bneType,  APC,     BIMM, aluADD,  stXXX,    ldXXX,    wbXXX, False),
        BLT       -> List(BType,   pcBranch, False, bltType,  APC,     BIMM, aluADD,  stXXX,    ldXXX,    wbXXX, False),
        BGE       -> List(BType,   pcBranch, False, bgeType,  APC,     BIMM, aluADD,  stXXX,    ldXXX,    wbXXX, False),
        BLTU      -> List(BType,   pcBranch, False, bltuType, APC,     BIMM, aluADD,  stXXX,    ldXXX,    wbXXX, False),
        BGEU      -> List(BType,   pcBranch, False, bgeuType, APC,     BIMM, aluADD,  stXXX,    ldXXX,    wbXXX, False),
        LB        -> List(IType,   pcBubble, True,  brXXX,    ARS1,    BIMM, aluADD,  stXXX,    ldByte,   wbMEM, True),
        LH        -> List(IType,   pcBubble, True,  brXXX,    ARS1,    BIMM, aluADD,  stXXX,    ldHalf,   wbMEM, True),
        LW        -> List(IType,   pcBubble, True,  brXXX,    ARS1,    BIMM, aluADD,  stXXX,    ldWord,   wbMEM, True),
        LBU       -> List(IType,   pcBubble, True,  brXXX,    ARS1,    BIMM, aluADD,  stXXX,    ldByteU,  wbMEM, True),
        LHU       -> List(IType,   pcBubble, True,  brXXX,    ARS1,    BIMM, aluADD,  stXXX,    ldHalfU,  wbMEM, True),
        SB        -> List(SType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluADD,  stByte,   ldXXX,    wbXXX, False),
        SH        -> List(SType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluADD,  stHalf,   ldXXX,    wbXXX, False),
        SW        -> List(SType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluADD,  stWord,   ldXXX,    wbXXX, False),
        ADDI      -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluADD,  stXXX,    ldXXX,    wbALU, True),
        SLTI      -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluSLT,  stXXX,    ldXXX,    wbALU, True),
        SLTIU     -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluSLTU, stXXX,    ldXXX,    wbALU, True),
        XORI      -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluXOR,  stXXX,    ldXXX,    wbALU, True),
        ORI       -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluOR,   stXXX,    ldXXX,    wbALU, True),
        ANDI      -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluAND,  stXXX,    ldXXX,    wbALU, True),
        SLLI      -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluSLL,  stXXX,    ldXXX,    wbALU, True),
        SRLI      -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluSRL,  stXXX,    ldXXX,    wbALU, True),
        SRAI      -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluSRA,  stXXX,    ldXXX,    wbALU, True),
        ADD       -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluADD,  stXXX,    ldXXX,    wbALU, True),
        SUB       -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSUB,  stXXX,    ldXXX,    wbALU, True),
        SLL       -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSLL,  stXXX,    ldXXX,    wbALU, True),
        SLT       -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSLT,  stXXX,    ldXXX,    wbALU, True),
        SLTU      -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSLTU, stXXX,    ldXXX,    wbALU, True),
        XOR       -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluXOR,  stXXX,    ldXXX,    wbALU, True),
        SRL       -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSRL,  stXXX,    ldXXX,    wbALU, True),
        SRA       -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSRA,  stXXX,    ldXXX,    wbALU, True),
        OR        -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluOR,   stXXX,    ldXXX,    wbALU, True),
        AND       -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluAND,  stXXX,    ldXXX,    wbALU, True),
        FENCE     -> List(IType,   pcPlus4,  False, brXXX,    AXXX,    BXXX, aluXXX,  stXXX,    ldXXX,    wbXXX, False),
        ECALL     -> List(instXXX, pcPlus4,  False, brXXX,    AXXX,    BXXX, aluXXX,  stXXX,    ldXXX,    wbXXX, False),
        EBREAK    -> List(instXXX, pcPlus4,  False, brXXX,    AXXX,    BXXX, aluXXX,  stXXX,    ldXXX,    wbXXX, False),
        LWU       -> List(instXXX, pcBubble, True,  brXXX,    ARS1,    BXXX, aluADD,  stXXX,    ldWordU,  wbMEM, True),
        LD        -> List(instXXX, pcBubble, True,  brXXX,    ARS1,    BXXX, aluADD,  stXXX,    ldDouble, wbMEM, True),
        SD        -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluADD,  stDouble, ldXXX,    wbXXX, False),
        ADDIW     -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluADDIW,  stXXX,    ldXXX,    wbALU, True),
        SLLIW     -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluSLLIW,  stXXX,    ldXXX,    wbALU, True),
        SRLIW     -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluSRLIW,  stXXX,    ldXXX,    wbALU, True),
        SRAIW     -> List(IType,   pcPlus4,  False, brXXX,    ARS1,    BIMM, aluSRAIW,  stXXX,    ldXXX,    wbALU, True),
        ADDW      -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluADDW,  stXXX,    ldXXX,    wbALU, True),
        SUBW      -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSUBW,  stXXX,    ldXXX,    wbALU, True),
        SLLW      -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSLLW,  stXXX,    ldXXX,    wbALU, True),
        SRLW      -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSRLW,  stXXX,    ldXXX,    wbALU, True),
        SRAW      -> List(instXXX, pcPlus4,  False, brXXX,    ARS1,    BXXX, aluSRAW,  stXXX,    ldXXX,    wbALU, True),
        FENCE_I   -> List(IType,   pcBubble, True,  brXXX,    AXXX,    BXXX, aluXXX,  stXXX,    ldXXX,    wbXXX, False),
        CSRRW     -> List(IType,   pcBubble, True,  brXXX,    ARS1,    BXXX, aluXXX,  stXXX,    ldXXX,    wbCSR, True),
        CSRRS     -> List(IType,   pcBubble, True,  brXXX,    ARS1,    BXXX, aluXXX,  stXXX,    ldXXX,    wbCSR, True),
        CSRRC     -> List(IType,   pcBubble, True,  brXXX,    ARS1,    BXXX, aluXXX,  stXXX,    ldXXX,    wbCSR, True),
        CSRRWI    -> List(ZType,   pcBubble, True,  brXXX,    AXXX,    BIMM, aluXXX,  stXXX,    ldXXX,    wbCSR, True),
        CSRRSI    -> List(ZType,   pcBubble, True,  brXXX,    AXXX,    BIMM, aluXXX,  stXXX,    ldXXX,    wbCSR, True),
        CSRRCI    -> List(ZType,   pcBubble, True,  brXXX,    AXXX,    BIMM, aluXXX,  stXXX,    ldXXX,    wbCSR, True),
        MRET      -> List(instXXX, pcPlus4,  False, brXXX,    AXXX,    BXXX, aluXXX,  stXXX,    ldXXX,    wbXXX, False),
    )
  )

  io.instType := controlSignal(0)
  io.pcSelect := controlSignal(1)
  io.bubble   := controlSignal(2)
  io.brType   := controlSignal(3)

  io.ASelect  := controlSignal(4)
  io.BSelect  := controlSignal(5)
  io.aluType  := controlSignal(6)

  io.stType   := controlSignal(7)
  io.ldType   := controlSignal(8)
  io.wbSelect := controlSignal(9)
  io.wbEnable := controlSignal(10)

}

