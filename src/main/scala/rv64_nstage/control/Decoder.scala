package rv64_nstage.control

import chisel3._
import chisel3.util._
import ControlConst._
import rv64_nstage.core.phvntomParams
import rv64_nstage.control.ISA._

object ControlConst {

  val True  = true.B
  val False = false.B

  val startAddr = "h80000000".U

  // io.pcSelect
  val pcPlus4  = 0.U(2.W)
  val pcBranch = 1.U(2.W)
  val pcJump   = 2.U(2.W)
  val pcEPC    = 3.U(2.W)
  val pcSelectBits = pcPlus4.getWidth

  // ImmExt.io.instType
  val instXXX = 0.U(4.W)
  val IType   = 1.U(4.W)
  val SType   = 2.U(4.W)
  val BType   = 3.U(4.W)
  val UType   = 4.U(4.W)
  val JType   = 5.U(4.W)
  val ZType   = 6.U(4.W) // Zicsr
  val MType   = 7.U(4.W)
  val AType   = 8.U(4.W)
  val Illegal = 9.U(4.W)
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
  val aluXXX      =  0.U(5.W)
  val aluADD      =  1.U(5.W)
  val aluSUB      =  2.U(5.W)
  val aluSLL      =  3.U(5.W)
  val aluSLT      =  4.U(5.W)
  val aluSLTU     =  5.U(5.W)
  val aluXOR      =  6.U(5.W)
  val aluSRL      =  7.U(5.W)
  val aluSRA      =  8.U(5.W)
  val aluOR       =  9.U(5.W)
  val aluAND      = 10.U(5.W)
  val aluCPA      = 11.U(5.W)
  val aluCPB      = 12.U(5.W)
  val aluADDW     = 13.U(5.W)
  val aluSUBW     = 14.U(5.W)
  val aluSLLW     = 15.U(5.W)
  val aluSRLW     = 16.U(5.W)
  val aluSRAW     = 17.U(5.W)
  val aluMUL      = 18.U(5.W)
  val aluMULH     = 19.U(5.W)
  val aluMULHSU   = 20.U(5.W)
  val aluMULHU    = 21.U(5.W)
  val aluDIV      = 22.U(5.W)
  val aluDIVU     = 23.U(5.W)
  val aluREM      = 24.U(5.W)
  val aluREMU     = 25.U(5.W)
  val aluMULW     = 26.U(5.W)
  val aluDIVW     = 27.U(5.W)
  val aluDIVUW    = 28.U(5.W)
  val aluREMW     = 29.U(5.W)
  val aluREMUW    = 30.U(5.W)
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
  val wenRes  = 6.U(3.W)
  val wenBits = wenXXX.getWidth

  // io.wbSelect
  val wbXXX = 0.U(3.W)
  val wbALU = 1.U(3.W)
  val wbMEM = 2.U(3.W)
  val wbPC  = 3.U(3.W)
  val wbCSR = 4.U(3.W)
  val wbCond = 5.U(3.W)
  val wbBits = wbXXX.getWidth

  // io.amoSelect
  val amoXXX  = 0.U(4.W)
  val amoSWAP = 1.U(4.W)
  val amoADD  = 2.U(4.W)
  val amoAND  = 3.U(4.W)
  val amoOR   = 4.U(4.W)
  val amoXOR  = 6.U(4.W)
  val amoMAX  = 7.U(4.W)
  val amoMAXU = 8.U(4.W)
  val amoMIN  = 9.U(4.W)
  val amoMINU = 10.U(4.W)
  val amoBits = amoXXX.getWidth

  val fwdXXX    = 3.U(2.W)
  val fwdMem1   = 0.U(2.W)
  val fwdMem2   = 1.U(2.W)
  val fwdWb     = 2.U(2.W)
  val fwdBits   = fwdXXX.getWidth

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

class InstInfo extends Bundle with phvntomParams {
  val instType  = Output(UInt(instBits.W))
  val pcSelect  = Output(UInt(pcSelectBits.W))
  val mult      = Output(Bool())
  val brType    = Output(UInt(brBits.W))
  val ASelect   = Output(UInt(ASelectBits.W))
  val BSelect   = Output(UInt(BSelectBits.W))
  val aluType   = Output(UInt(aluBits.W))
  val memType   = Output(UInt(memBits.W))
  val wbSelect  = Output(UInt(wbBits.W))
  val wbEnable  = Output(UInt(wenBits.W))
  val amoSelect = Output(UInt(amoBits.W))
  val fwd_stage = Output(UInt(fwdBits.W))
}

class ControlPathIO extends Bundle with phvntomParams {
  val inst      = Input(UInt(xlen.W))
  val inst_info_out = Flipped(Flipped(new InstInfo))
}

class ControlPath extends Module with phvntomParams {
  val io = IO(new ControlPathIO)

  val controlSignal = ListLookup(io.inst,
    List(Illegal, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX,    amoXXX,    fwdXXX),
    Array(         /*      Inst  |   PC   | alu0 or |Branch|   A    |   B    |  alu   |  Mem  |     wb    |  wb       |   amo  */
                   /*      Type  | Select |  multi1 | Type | Select | Select |  Type  | Type  |   Select  | Enable    | Select */
      LUI       -> List(UType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      AUIPC     -> List(UType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      JAL       -> List(JType,   pcJump,   False,   brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbPC,    wenReg   ,  amoXXX,   fwdMem1 ),
      JALR      -> List(IType,   pcJump,   False,   brXXX,    ARS1,    BIMM,   aluADD,  memXXX,    wbPC,    wenReg   ,  amoXXX,   fwdMem1 ),
      BEQ       -> List(BType,   pcBranch, False,   beqType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      BNE       -> List(BType,   pcBranch, False,   bneType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      BLT       -> List(BType,   pcBranch, False,   bltType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      BGE       -> List(BType,   pcBranch, False,   bgeType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      BLTU      -> List(BType,   pcBranch, False,   bltuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      BGEU      -> List(BType,   pcBranch, False,   bgeuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      LB        -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memByte,   wbMEM,   wenReg   ,  amoXXX,   fwdWb   ),
      LH        -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memHalf,   wbMEM,   wenReg   ,  amoXXX,   fwdWb   ),
      LW        -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memWord,   wbMEM,   wenReg   ,  amoXXX,   fwdWb   ),
      LBU       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memByteU,  wbMEM,   wenReg   ,  amoXXX,   fwdWb   ),
      LHU       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memHalfU,  wbMEM,   wenReg   ,  amoXXX,   fwdWb   ),
      SB        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memByte,   wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ),
      SH        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memHalf,   wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ),
      SW        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memWord,   wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ),
      ADDI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SLTI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLT,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SLTIU     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLTU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      XORI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluXOR,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      ORI       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluOR,   memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      ANDI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluAND,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SLLI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SRLI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SRAI      -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRA,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      ADD       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SUB       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSUB,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SLL       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SLT       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLT,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SLTU      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLTU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      XOR       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluXOR,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SRL       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SRA       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRA,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      OR        -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluOR,   memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      AND       -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluAND,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      FENCE     -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      ECALL     -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      EBREAK    -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      LWU       -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memWordU,  wbMEM,   wenReg   ,  amoXXX,   fwdWb   ),
      LD        -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memDouble, wbMEM,   wenReg   ,  amoXXX,   fwdWb   ),
      SD        -> List(SType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADD,  memDouble, wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ),
      ADDIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluADDW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SLLIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSLLW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SRLIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRLW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SRAIW     -> List(IType,   pcPlus4,  False,   brXXX,    ARS1,    BIMM,   aluSRAW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      ADDW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluADDW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SUBW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSUBW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SLLW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSLLW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SRLW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRLW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      SRAW      -> List(instXXX, pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluSRAW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      FENCE_I   -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      CSRRW     -> List(ZType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memXXX,    wbCSR,   wenCSRW  ,  amoXXX,   fwdMem2 ),
      CSRRS     -> List(ZType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memXXX,    wbCSR,   wenCSRS  ,  amoXXX,   fwdMem2 ),
      CSRRC     -> List(ZType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memXXX,    wbCSR,   wenCSRC  ,  amoXXX,   fwdMem2 ),
      CSRRWI    -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRW  ,  amoXXX,   fwdMem2 ),
      CSRRSI    -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRS  ,  amoXXX,   fwdMem2 ),
      CSRRCI    -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRC  ,  amoXXX,   fwdMem2 ),
      MRET      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ),
      MUL       -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,   aluMUL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      MULH      -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,  aluMULH,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      MULHSU    -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX, aluMULHSU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      MULHU     -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX, aluMULHU,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      DIV       -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,   aluDIV,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      DIVU      -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,   aluDIVU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      REM       -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,   aluREM,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      REMU      -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,   aluREMU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      MULW      -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,   aluMULW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      DIVW      -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,   aluDIVW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      DIVUW     -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX, aluDIVUW,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      REMW      -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX,   aluREMW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      REMUW     -> List(MType,   pcPlus4,  True,    brXXX,    ARS1,    BXXX, aluREMUW,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ),
      AMOADD_W  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoADD,   fwdWb   ),
      AMOXOR_W  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoXOR,   fwdWb   ),
      AMOOR_W   -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoOR,    fwdWb   ),
      AMOAND_W  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoAND,   fwdWb   ),
      AMOMIN_W  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoMIN,   fwdWb   ),
      AMOMAX_W  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoMAX,   fwdWb   ),
      AMOMINU_W -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoMINU,  fwdWb   ),
      AMOMAXU_W -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoMAXU,  fwdWb   ),
      AMOSWAP_W -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoSWAP,  fwdWb   ),
      LR_W      -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbMEM,   wenRes   ,  amoXXX,   fwdWb   ),
      SC_W      -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memWord,   wbCond,  wenMem   ,  amoXXX,   fwdWb   ),
      AMOADD_D  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoADD,   fwdWb   ),
      AMOXOR_D  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoXOR,   fwdWb   ),
      AMOOR_D   -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoOR,    fwdWb   ),
      AMOAND_D  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoAND,   fwdWb   ),
      AMOMIN_D  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoMIN,   fwdWb   ),
      AMOMAX_D  -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoMAX,   fwdWb   ),
      AMOMINU_D -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoMINU,  fwdWb   ),
      AMOMAXU_D -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoMAXU,  fwdWb   ),
      AMOSWAP_D -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoSWAP,  fwdWb   ),
      LR_D      -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbMEM,   wenRes   ,  amoXXX,   fwdWb   ),
      SC_D      -> List(AType,   pcPlus4,  False,   brXXX,    ARS1,    BXXX,   aluCPA,  memDouble, wbCond,  wenMem   ,  amoXXX,   fwdWb   ),
    )
  )

  io.inst_info_out.instType  := controlSignal(0)
  io.inst_info_out.pcSelect  := controlSignal(1)
  io.inst_info_out.mult      := controlSignal(2)
  io.inst_info_out.brType    := controlSignal(3)

  io.inst_info_out.ASelect   := controlSignal(4)
  io.inst_info_out.BSelect   := controlSignal(5)
  io.inst_info_out.aluType   := controlSignal(6)

  io.inst_info_out.memType   := controlSignal(7)
  io.inst_info_out.wbSelect  := controlSignal(8)
  io.inst_info_out.wbEnable  := controlSignal(9)
  io.inst_info_out.amoSelect := controlSignal(10)

  io.inst_info_out.fwd_stage := controlSignal(11)
}