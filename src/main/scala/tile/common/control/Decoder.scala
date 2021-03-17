package tile.common.control

import chisel3._
import chisel3.util._
import ControlConst._
import tile.phvntomParams
import tile.common.control.ISA._

object ControlConst {

  val True  = true.B
  val False = false.B

  // io.pcSelect
  val pcPlus4  = 0.U(3.W)
  val pcBranch = 1.U(3.W)
  val pcJump   = 2.U(3.W)
  val pcEPC    = 3.U(3.W)
  val pcPlus2  = 4.U(3.W)
  val pcSelectBits = pcPlus4.getWidth

  // ImmExt.io.instType
  val instXXX    = 0.U(5.W)  // Dont care
  val IType      = 1.U(5.W)
  val SType      = 2.U(5.W)
  val BType      = 3.U(5.W)
  val UType      = 4.U(5.W)
  val JType      = 5.U(5.W)
  val ZType      = 6.U(5.W) // Zicsr
  val CI4Type    = 7.U(5.W)
  val CI8Type    = 8.U(5.W)
  val CSS4Type   = 9.U(5.W)
  val CSS8Type   = 10.U(5.W)
  val CSL4Type   = 11.U(5.W)  // Both CS and CL use the same way to represent immediate
  val CSL8Type   = 12.U(5.W)
  val CJType     = 13.U(5.W)
  val CBType     = 14.U(5.W)
  val CIType     = 15.U(5.W)
  val CUIType    = 16.U(5.W)
  val CI16SPType = 17.U(5.W)
  val CIWType    = 18.U(5.W)
  val CBALUType  = 19.U(5.W)
  val Illegal    = 31.U(5.W)
  val instBits   = instXXX.getWidth

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
  val AXXX = 0.U(1.W)
  val APC  = 1.U(1.W)
  val ASelectBits = AXXX.getWidth


  // io.BSelect
  val BXXX = 0.U(1.W)
  val BIMM = 1.U(1.W)
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

  // io.meinstXXX
  val memXXX    = 0.U(3.W)
  val memByte   = 1.U(3.W)
  val memHalf   = 2.U(3.W)
  val memWord   = 3.U(3.W)
  val memDouble = 4.U(3.W)
  val memByteU  = 5.U(3.W)
  val memHalfU  = 6.U(3.W)
  val memWordU  = 7.U(3.W)
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
  val wbCPC = 6.U(3.W)
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

  val fwdXXX    = 4.U(3.W)
  val fwdDTLB   = 0.U(3.W)
  val fwdMem1   = 1.U(3.W)
  val fwdMem2   = 2.U(3.W)
  val fwdWb     = 3.U(3.W)
  val fwdBits   = fwdXXX.getWidth

  val flushXXX  = "b00".U(2.W)
  val flushI    = "b01".U(2.W)
  val flushTLB  = "b10".U(2.W)
  val flushAll  = "b11".U(2.W)
  val flushBits = flushXXX.getWidth

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
  val flushType = Output(UInt(flushBits.W))
  val modifyRd  = Output(Bool())
  val rs1Num    = Output(UInt(regWidth.W))
  val rs2Num    = Output(UInt(regWidth.W))
  val rdNum     = Output(UInt(regWidth.W))
  val pec       = if (enable_pec) Output(Bool()) else null
}

class ControlPathIO extends Bundle with phvntomParams {
  val inst      = Input(UInt(xlen.W))
  val inst_info_out = Flipped(Flipped(new InstInfo))
}

class ControlPath extends Module with phvntomParams {
  val io = IO(new ControlPathIO)

  val IRS1 = Cat(0.U(1.W), io.inst(19, 15))
  val IRS2 = Cat(0.U(1.W), io.inst(24, 20))
  val IRD = Cat(0.U(1.W), io.inst(11, 7))
  val FRS1 = Cat(1.U(1.W), io.inst(19, 15))
  val FRS2 = Cat(0.U(1.W), io.inst(24, 20))
  val FRD = Cat(1.U(1.W), io.inst(11, 7))
  val IC97 = Cat("b001".U(3.W), io.inst(9, 7))
  val IC42 = Cat("b001".U(3.W), io.inst(4, 2))
  val IC62 = Cat(0.U(1.W), io.inst(6, 2))
  val ICX2 = 2.U(6.W)
  val ICX0 = 0.U(6.W)
  val ICX1 = 1.U(6.W)

  val full_length_illegal_list = List(Illegal, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbXXX,   wenXXX,    amoXXX,    fwdXXX,    flushXXX,      False,    IRS1, IRS2, IRD, False)
  val basic_inst_array = Array(         
    /*                   Inst  |   PC   | use   |Branch    |   A    |   B    |  alu   |  Mem  |     wb    |  wb       |   amo   | forward  | flush  |    rd  | rs1 | rs2|  rd   |   pec */
    /*                   Type  | Select | mult  | Type     | use rs1| use rs2|  Type  | Type  |   Select  | Enable    | Select  |  stage   | what   |   wen  |     |    |       |       */
    AUIPC      -> List(UType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    JAL        -> List(JType,   pcJump,   False,   brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbPC,    wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    LUI        -> List(UType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluCPB,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    JALR       -> List(IType,   pcJump,   False,   brXXX,    AXXX,    BIMM,   aluADD,  memXXX,    wbPC,    wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    BEQ        -> List(BType,   pcBranch, False,   beqType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    BNE        -> List(BType,   pcBranch, False,   bneType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    BLT        -> List(BType,   pcBranch, False,   bltType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    BGE        -> List(BType,   pcBranch, False,   bgeType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    BLTU       -> List(BType,   pcBranch, False,   bltuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    BGEU       -> List(BType,   pcBranch, False,   bgeuType, APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    LB         -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memByte,   wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    LH         -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memHalf,   wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    LW         -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memWord,   wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    LBU        -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memByteU,  wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    LHU        -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memHalfU,  wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SB         -> List(SType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memByte,   wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    SH         -> List(SType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memHalf,   wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    SW         -> List(SType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memWord,   wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    ADDI       -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SLTI       -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluSLT,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SLTIU      -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluSLTU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    XORI       -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluXOR,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    ORI        -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluOR,   memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    ANDI       -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluAND,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SLLI       -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluSLL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SRLI       -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluSRL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SRAI       -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluSRA,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    ADD        -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SUB        -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSUB,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SLL        -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSLL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SLT        -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSLT,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SLTU       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSLTU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    XOR        -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXOR,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SRL        -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSRL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SRA        -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSRA,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    OR         -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluOR,   memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AND        -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluAND,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    FENCE      -> List(IType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    ECALL      -> List(instXXX, pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    EBREAK     -> List(instXXX, pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    LWU        -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memWordU,  wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    LD         -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memDouble, wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SD         -> List(SType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memDouble, wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    ADDIW      -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluADDW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SLLIW      -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluSLLW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SRLIW      -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluSRLW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SRAIW      -> List(IType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluSRAW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    ADDW       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluADDW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SUBW       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSUBW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SLLW       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSLLW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SRLW       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSRLW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SRAW       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluSRAW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    FENCE_I    -> List(IType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushI    , False, IRS1, IRS2, IRD   , False),
    CSRRW      -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memXXX,    wbCSR,   wenCSRW  ,  amoXXX,   fwdMem2 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    CSRRS      -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memXXX,    wbCSR,   wenCSRS  ,  amoXXX,   fwdMem2 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    CSRRC      -> List(ZType,   pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memXXX,    wbCSR,   wenCSRC  ,  amoXXX,   fwdMem2 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    CSRRWI     -> List(ZType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRW  ,  amoXXX,   fwdMem2 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    CSRRSI     -> List(ZType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRS  ,  amoXXX,   fwdMem2 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    CSRRCI     -> List(ZType,   pcPlus4,  False,   brXXX,    APC,     BIMM,   aluCPB,  memXXX,    wbCSR,   wenCSRC  ,  amoXXX,   fwdMem2 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    MRET       -> List(instXXX, pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    // M Extension
    MUL        -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,   aluMUL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    MULH       -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,  aluMULH,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    MULHSU     -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX, aluMULHSU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    MULHU      -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX, aluMULHU,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    DIV        -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,   aluDIV,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    DIVU       -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,   aluDIVU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    REM        -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,   aluREM,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    REMU       -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,   aluREMU, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    MULW       -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,   aluMULW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    DIVW       -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,   aluDIVW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    DIVUW      -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX, aluDIVUW,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    REMW       -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX,   aluREMW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    REMUW      -> List(instXXX, pcPlus4,  True,    brXXX,    AXXX,    BXXX, aluREMUW,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    // A Extension
    AMOADD_W   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoADD,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOXOR_W   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoXOR,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOOR_W    -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoOR,    fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOAND_W   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoAND,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOMIN_W   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoMIN,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOMAX_W   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoMAX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOMINU_W  -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoMINU,  fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOMAXU_W  -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoMAXU,  fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOSWAP_W  -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenReg   ,  amoSWAP,  fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    LR_W       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbMEM,   wenRes   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SC_W       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memWord,   wbCond,  wenMem   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOADD_D   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoADD,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOXOR_D   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoXOR,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOOR_D    -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoOR,    fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOAND_D   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoAND,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOMIN_D   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoMIN,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOMAX_D   -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoMAX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOMINU_D  -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoMINU,  fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOMAXU_D  -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoMAXU,  fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    AMOSWAP_D  -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenReg   ,  amoSWAP,  fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    LR_D       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbMEM,   wenRes   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    SC_D       -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BIMM,   aluCPA,  memDouble, wbCond,  wenMem   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    // Priv
    SRET       -> List(instXXX, pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    URET       -> List(instXXX, pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    WFI        -> List(instXXX, pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    SFENCE_VMA -> List(instXXX, pcPlus4,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushTLB  , False, IRS1, IRS2, IRD   , False),
    // C Ext
    C_ILLEGAL  -> List(Illegal, pcPlus2,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    C_ADDI4SPN -> List(CIWType, pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , ICX2, IRS2, IC42  , False),
    C_LW       -> List(CSL4Type,pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memWord,   wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IC97, IRS2, IC42  , False),
    C_LD       -> List(CSL8Type,pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memDouble, wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , IC97, IRS2, IC42  , False),
    C_SW       -> List(CSL4Type,pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memWord,   wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IC97, IC42, IRD   , False),
    C_SD       -> List(CSL8Type,pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memDouble, wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IC97, IC42, IRD   , False),
    C_NOP      -> List(instXXX, pcPlus2,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    C_ADDI     -> List(CIType,  pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRD,  IRS2, IRD   , False),
    C_ADDIW    -> List(CIType,  pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADDW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRD,  IRS2, IRD   , False),
    C_LI       -> List(CIType,  pcPlus2,  False,   brXXX,    APC,     BIMM,   aluCPB,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    C_ADDI16SP -> List(CI16SPType,pcPlus2,False,   brXXX,    AXXX,    BIMM,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRD,  IRS2, IRD   , False),
    C_LUI      -> List(CUIType, pcPlus2,  False,   brXXX,    APC,     BIMM,   aluCPB,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRS1, IRS2, IRD   , False),
    C_SRLI     -> List(CBALUType,pcPlus2, False,   brXXX,    AXXX,    BIMM,   aluSRL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IRS2, IC97  , False),
    C_SRAI     -> List(CBALUType,pcPlus2, False,   brXXX,    AXXX,    BIMM,   aluSRA,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IRS2, IC97  , False),
    C_ANDI     -> List(CBALUType,pcPlus2, False,   brXXX,    AXXX,    BIMM,   aluAND,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IRS2, IC97  , False),
    C_SUB      -> List(instXXX, pcPlus2,  False,   brXXX,    AXXX,    BXXX,   aluSUB,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IC42, IC97  , False),
    C_XOR      -> List(instXXX, pcPlus2,  False,   brXXX,    AXXX,    BXXX,   aluXOR,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IC42, IC97  , False),
    C_OR       -> List(instXXX, pcPlus2,  False,   brXXX,    AXXX,    BXXX,   aluOR,   memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IC42, IC97  , False),
    C_AND      -> List(instXXX, pcPlus2,  False,   brXXX,    AXXX,    BXXX,   aluAND,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IC42, IC97  , False),
    C_SUBW     -> List(instXXX, pcPlus2,  False,   brXXX,    AXXX,    BXXX,   aluSUBW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IC42, IC97  , False),
    C_ADDW     -> List(instXXX, pcPlus2,  False,   brXXX,    AXXX,    BXXX,   aluADDW, memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IC97, IC42, IC97  , False),
    C_J        -> List(CJType,  pcJump,   False,   brXXX,    APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    C_BEQZ     -> List(CBType,  pcBranch, False,   beqType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IC97, ICX0, IRD   , False),
    C_BNEZ     -> List(CBType,  pcBranch, False,   bneType,  APC,     BIMM,   aluADD,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IC97, ICX0, IRD   , False),
    C_SLLI     -> List(CBALUType,pcPlus2, False,   brXXX,    AXXX,    BIMM,   aluSLL,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRD,  IRS2, IRD   , False),
    C_LWSP     -> List(CI4Type, pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memWord,   wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , ICX2, IRS2, IRD   , False),
    C_LDSP     -> List(CI8Type, pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memDouble, wbMEM,   wenReg   ,  amoXXX,   fwdWb   ,  flushXXX  , True , ICX2, IRS2, IRD   , False),
    C_JR       -> List(instXXX, pcJump,   False,   brXXX,    AXXX,    BIMM,   aluCPA,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRD,  IC62, IRD   , False),
    C_MV       -> List(instXXX, pcPlus2,  False,   brXXX,    APC,     BXXX,   aluCPB,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRD,  IC62, IRD   , False),
    C_EBREAK   -> List(instXXX, pcPlus2,  False,   brXXX,    APC,     BIMM,   aluXXX,  memXXX,    wbXXX,   wenXXX   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, IRS1, IRS2, IRD   , False),
    C_JALR     -> List(instXXX, pcJump,   False,   brXXX,    AXXX,    BIMM,   aluCPA,  memXXX,    wbCPC,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRD,  IC62, ICX1  , False),
    C_ADD      -> List(instXXX, pcPlus2,  False,   brXXX,    AXXX,    BXXX,   aluADD,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdDTLB ,  flushXXX  , True , IRD,  IC62, IRD   , False),
    C_SWSP     -> List(CSS4Type,pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memWord,   wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, ICX2, IC62, IRD   , False),
    C_SDSP     -> List(CSS8Type,pcPlus2,  False,   brXXX,    AXXX,    BIMM,   aluADD,  memDouble, wbXXX,   wenMem   ,  amoXXX,   fwdXXX  ,  flushXXX  , False, ICX2, IC62, IRD   , False)
  )
  val pec_inst_array = Array(
    CRETK      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   ,  True),
    CRDTK      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   ,  True),
    CREMK      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   ,  True),
    CRDMK      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   ,  True),
    CREAK      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   ,  True),
    CRDAK      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   ,  True),
    CREBK      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   ,  True),
    CRDBK      -> List(instXXX, pcPlus4,  False,   brXXX,    AXXX,    BXXX,   aluXXX,  memXXX,    wbALU,   wenReg   ,  amoXXX,   fwdMem1 ,  flushXXX  , True , IRS1, IRS2, IRD   ,  True)
  )
  val array_to_decode = if (enable_pec) Array.concat(basic_inst_array, pec_inst_array) else basic_inst_array
  val controlSignal = ListLookup(io.inst,
    full_length_illegal_list,
    array_to_decode
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
  io.inst_info_out.flushType := controlSignal(12)
  io.inst_info_out.modifyRd  := controlSignal(13)

  io.inst_info_out.rs1Num    := controlSignal(14)
  io.inst_info_out.rs2Num    := controlSignal(15)
  io.inst_info_out.rdNum     := controlSignal(16)

  if (enable_pec) {
    io.inst_info_out.pec := controlSignal(17)
  }
}
