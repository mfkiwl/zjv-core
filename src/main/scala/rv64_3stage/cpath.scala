package rv64_3stage

import chisel3._
import chisel3.util._
import common.ISA._
import ControlConst._
import firrtl.PrimOps.AsAsyncReset


object ControlConst {

  val True  = true.B
  val False = false.B

  val startAddr = if(false) {"h40000000".U} else {"h80000000".U}

  // io.pcSelect
  val pcPlus4  = 0.U(3.W)
  val pcBubble = 1.U(3.W)
  val pcBranch = 2.U(3.W)
  val pcJump   = 3.U(3.W)
  val pcEPC    = 4.U(3.W)
  val pcSelectBits = pcPlus4.getWidth

  // ImmExt.io.instType
  val instXXX = 0.U(3.W)
  val IType   = 1.U(3.W)
  val SType   = 2.U(3.W)
  val BType   = 3.U(3.W)
  val UType   = 4.U(3.W)
  val JType   = 5.U(3.W)
  val ZType   = 6.U(3.W) // Zicsr
  val Illegal = 7.U(3.W)
  val instBits = instXXX.getWidth

  // BrCond.io.brType
  val brXXX    = 0.U(3.W)
  val beqType  = 1.U(3.W)
  val bneType  = 2.U(3.W)
  val bltType  = 3.U(3.W)
  val bgeType  = 4.U(3.W)
  val bltuType = 5.U(3.W)
  val bgeuType = 6.U(3.W)
  val brBits = brXXX.getWidth

  // io.ASelect
  val AXXX = 0.U(2.W)
  val APC  = 1.U(2.W)
  val ARS1 = 2.U(2.W)
  val ASelectBits = AXXX.getWidth


  // io.BSelect
  val BXXX = 0.U(2.W)
  val BIMM = 1.U(2.W)
  val BRS2 = 2.U(2.W)
  val BSelectBits = BXXX.getWidth

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
  val aluADDW  = 13.U(5.W)
  val aluSUBW  = 14.U(5.W)
  val aluSLLW  = 15.U(5.W)
  val aluSRLW  = 16.U(5.W)
  val aluSRAW  = 17.U(5.W)
  val aluBits = aluXXX.getWidth

  // io.memType
  val memXXX    = 0.U(4.W)
  val memByte   = 1.U(4.W)
  val memHalf   = 2.U(4.W)
  val memWord   = 3.U(4.W)
  val memDouble = 4.U(4.W)
  val memByteU  = 5.U(4.W)
  val memHalfU  = 6.U(4.W)
  val memWordU  = 7.U(4.W)
  val memQuad   = 8.U(4.W)
  val memOcto   = 9.U(4.W)
  val memHex    = 10.U(4.W)
  val memBits   = memXXX.getWidth

  // io.wbEnable
  val wenXXX  = 0.U(3.W)
  val wenReg  = 1.U(3.W)
  val wenMem  = 2.U(3.W)
  val wenCSRW = 3.U(3.W)
  val wenCSRS = 4.U(3.W)
  val wenCSRC = 5.U(3.W)
  val wenBits = wenXXX.getWidth

  // io.wbSelect
  val wbXXX = 0.U(3.W)
  val wbALU = 1.U(3.W)
  val wbMEM = 2.U(3.W)
  val wbPC  = 3.U(3.W)
  val wbCSR = 4.U(3.W)
  val wbBits = wbXXX.getWidth

  // sign extend or unsigned extend
  val unsignedExt = 0.U(1.W)
  val signedExt = 1.U(1.W)
  val extBits = unsignedExt.getWidth

  // Following is From riscv-sodor:
  // The Bubble Instruction (Machine generated NOP)
  // Insert (XOR x0,x0,x0) which is different from software compiler
  // generated NOPs which are (ADDI x0, x0, 0).
  // Reasoning for this is to let visualizers and stat-trackers differentiate
  // between software NOPs and machine-generated Bubbles in the pipeline.
  val BUBBLE  = "b00000000000000000100000000110011".U(32.W)  // 0x000004033

  val TRMT    = "b00000000000100000000000001110011".U(32.W)  // ebreak
}

class ControlPathIO extends Bundle with phvntomParams {
  val inst      = Input(UInt(xlen.W))
  val instType  = Output(UInt(instBits.W))
  val pcSelect  = Output(UInt(pcSelectBits.W))
  val bubble    = Output(Bool())
  val brType    = Output(UInt(brBits.W))
  val ASelect   = Output(UInt(ASelectBits.W))
  val BSelect   = Output(UInt(BSelectBits.W))
  val aluType   = Output(UInt(aluBits.W))
  val memType   = Output(UInt(memBits.W))
  val wbSelect  = Output(UInt(wbBits.W))
  val wbEnable  = Output(UInt(wenBits.W))
}

class ControlPath extends Module with phvntomParams {
  val io = IO(new ControlPathIO)

  val controlSignal = ListLookup(io.inst,
                     List(Illegal, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX),
    Array(         /*      Inst  |   PC   |  Bubble | Branch |   A    |   B    |  alu   |  Mem  |     wb    |  wb      */
                   /*      Type  | Select |         |  Type  | Select | Select |  Type  | Type  |   Select  | Enable   */
        LUI       -> List(UType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbALU,   wenReg),
        AUIPC     -> List(UType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbALU,   wenReg),
        JAL       -> List(JType,   pcJump,   True,    brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbPC,    wenReg),
        JALR      -> List(IType,   pcJump,   True,    brXXX,    ARS1,    BIMM,   aluADD,  memXXX,    wbPC,    wenReg),
        BEQ       -> List(BType,   pcBranch, True,    beqType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX),
        BNE       -> List(BType,   pcBranch, True,    bneType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX),
        BLT       -> List(BType,   pcBranch, True,    bltType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX),
        BGE       -> List(BType,   pcBranch, True,    bgeType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX),
        BLTU      -> List(BType,   pcBranch, True,    bltuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX),
        BGEU      -> List(BType,   pcBranch, True,    bgeuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX),
        LB        -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memByte,   wbMEM,   wenReg),
        LH        -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memHalf,   wbMEM,   wenReg),
        LW        -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memWord,   wbMEM,   wenReg),
        LBU       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memByteU,  wbMEM,   wenReg),
        LHU       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memHalfU,  wbMEM,   wenReg),
        SB        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memByte,   wbXXX,   wenMem),
        SH        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memHalf,   wbXXX,   wenMem),
        SW        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memWord,   wbXXX,   wenMem),
        ADDI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memXXX,    wbALU,   wenReg),
        SLTI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLT,  memXXX,    wbALU,   wenReg),
        SLTIU     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLTU, memXXX,    wbALU,   wenReg),
        XORI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluXOR,  memXXX,    wbALU,   wenReg),
        ORI       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluOR,   memXXX,    wbALU,   wenReg),
        ANDI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluAND,  memXXX,    wbALU,   wenReg),
        SLLI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLL,  memXXX,    wbALU,   wenReg),
        SRLI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRL,  memXXX,    wbALU,   wenReg),
        SRAI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRA,  memXXX,    wbALU,   wenReg),
        ADD       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluADD,  memXXX,    wbALU,   wenReg),
        SUB       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSUB,  memXXX,    wbALU,   wenReg),
        SLL       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLL,  memXXX,    wbALU,   wenReg),
        SLT       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLT,  memXXX,    wbALU,   wenReg),
        SLTU      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLTU, memXXX,    wbALU,   wenReg),
        XOR       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXOR,  memXXX,    wbALU,   wenReg),
        SRL       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRL,  memXXX,    wbALU,   wenReg),
        SRA       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRA,  memXXX,    wbALU,   wenReg),
        OR        -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluOR,   memXXX,    wbALU,   wenReg),
        AND       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluAND,  memXXX,    wbALU,   wenReg),
        FENCE     -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX),
        ECALL     -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX),
        EBREAK    -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX),
        LWU       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memWordU,  wbMEM,   wenReg),
        LD        -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memDouble, wbMEM,   wenReg),
        SD        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memDouble, wbXXX,   wenMem),
        ADDIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADDW, memXXX,    wbALU,   wenReg),
        SLLIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLLW, memXXX,    wbALU,   wenReg),
        SRLIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRLW, memXXX,    wbALU,   wenReg),
        SRAIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRAW, memXXX,    wbALU,   wenReg),
        ADDW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluADDW, memXXX,    wbALU,   wenReg),
        SUBW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSUBW, memXXX,    wbALU,   wenReg),
        SLLW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLLW, memXXX,    wbALU,   wenReg),
        SRLW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRLW, memXXX,    wbALU,   wenReg),
        SRAW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRAW, memXXX,    wbALU,   wenReg),
        FENCE_I   -> List(IType,   pcBubble, True,    brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX),
        CSRRW     -> List(ZType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memXXX,    wbCSR,   wenCSRW),
        CSRRS     -> List(ZType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memXXX,    wbCSR,   wenCSRS),
        CSRRC     -> List(ZType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memXXX,    wbCSR,   wenCSRC),
        CSRRWI    -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRW),
        CSRRSI    -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRS),
        CSRRCI    -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRC),
        MRET      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX),
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

