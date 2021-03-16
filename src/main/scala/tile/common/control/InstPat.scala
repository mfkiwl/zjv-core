package tile.common.control

import chisel3.util._
import tile.phvntomParams

object ISA extends phvntomParams {
  // RV32I
  def LUI           = BitPat("b?????????????????????????0110111")
  def AUIPC         = BitPat("b?????????????????????????0010111")
  def JAL           = BitPat("b?????????????????????????1101111")
  def JALR          = BitPat("b?????????????????000?????1100111")
  def BEQ           = BitPat("b?????????????????000?????1100011")
  def BNE           = BitPat("b?????????????????001?????1100011")
  def BLT           = BitPat("b?????????????????100?????1100011")
  def BGE           = BitPat("b?????????????????101?????1100011")
  def BLTU          = BitPat("b?????????????????110?????1100011")
  def BGEU          = BitPat("b?????????????????111?????1100011")
  def LB            = BitPat("b?????????????????000?????0000011")
  def LH            = BitPat("b?????????????????001?????0000011")
  def LW            = BitPat("b?????????????????010?????0000011")
  def LBU           = BitPat("b?????????????????100?????0000011")
  def LHU           = BitPat("b?????????????????101?????0000011")
  def SB            = BitPat("b?????????????????000?????0100011")
  def SH            = BitPat("b?????????????????001?????0100011")
  def SW            = BitPat("b?????????????????010?????0100011")
  def ADDI          = BitPat("b?????????????????000?????0010011")
  def SLTI          = BitPat("b?????????????????010?????0010011")
  def SLTIU         = BitPat("b?????????????????011?????0010011")
  def XORI          = BitPat("b?????????????????100?????0010011")
  def ORI           = BitPat("b?????????????????110?????0010011")
  def ANDI          = BitPat("b?????????????????111?????0010011")
  def SLLI          = if (xlen == 32) BitPat("b0000000??????????001?????0010011")
                      else BitPat("b000000???????????001?????0010011")
  def SRLI          = if (xlen == 32) BitPat("b0000000??????????101?????0010011")
                      else BitPat("b000000???????????101?????0010011")
  def SRAI          = if (xlen == 32) BitPat("b0100000??????????101?????0010011")
                      else BitPat("b010000???????????101?????0010011")
  def ADD           = BitPat("b0000000??????????000?????0110011")
  def SUB           = BitPat("b0100000??????????000?????0110011")
  def SLL           = BitPat("b0000000??????????001?????0110011")
  def SLT           = BitPat("b0000000??????????010?????0110011")
  def SLTU          = BitPat("b0000000??????????011?????0110011")
  def XOR           = BitPat("b0000000??????????100?????0110011")
  def SRL           = BitPat("b0000000??????????101?????0110011")
  def SRA           = BitPat("b0100000??????????101?????0110011")
  def OR            = BitPat("b0000000??????????110?????0110011")
  def AND           = BitPat("b0000000??????????111?????0110011")
  def FENCE         = BitPat("b?????????????????000?????0001111")
  def ECALL         = BitPat("b00000000000000000000000001110011")
  def EBREAK        = BitPat("b00000000000100000000000001110011")


  // RV64I
  def LWU           = BitPat("b?????????????????110?????0000011")
  def LD            = BitPat("b?????????????????011?????0000011")
  def SD            = BitPat("b?????????????????011?????0100011")
  def ADDIW         = BitPat("b?????????????????000?????0011011")
  def SLLIW         = BitPat("b0000000??????????001?????0011011")
  def SRLIW         = BitPat("b0000000??????????101?????0011011")
  def SRAIW         = BitPat("b0100000??????????101?????0011011")
  def ADDW          = BitPat("b0000000??????????000?????0111011")
  def SUBW          = BitPat("b0100000??????????000?????0111011")
  def SLLW          = BitPat("b0000000??????????001?????0111011")
  def SRLW          = BitPat("b0000000??????????101?????0111011")
  def SRAW          = BitPat("b0100000??????????101?????0111011")


  // RV32/RV64 Zifencei
  def FENCE_I       = BitPat("b?????????????????001?????0001111")


  // RV32/64 Zicsr
  def CSRRW         = BitPat("b?????????????????001?????1110011")
  def CSRRS         = BitPat("b?????????????????010?????1110011")
  def CSRRC         = BitPat("b?????????????????011?????1110011")
  def CSRRWI        = BitPat("b?????????????????101?????1110011")
  def CSRRSI        = BitPat("b?????????????????110?????1110011")
  def CSRRCI        = BitPat("b?????????????????111?????1110011")


  // RV32/64 Privilage
  def WFI           = BitPat("b00010000010100000000000001110011")
  def MRET          = BitPat("b00110000001000000000000001110011")
  def SRET          = BitPat("b00010000001000000000000001110011")
  def URET          = BitPat("b00000000001000000000000001110011")
  def SFENCE_VMA    = BitPat("b0001001??????????000000001110011")


  // RV32M
  def MUL                = BitPat("b0000001??????????000?????0110011")
  def MULH               = BitPat("b0000001??????????001?????0110011")
  def MULHSU             = BitPat("b0000001??????????010?????0110011")
  def MULHU              = BitPat("b0000001??????????011?????0110011")
  def DIV                = BitPat("b0000001??????????100?????0110011")
  def DIVU               = BitPat("b0000001??????????101?????0110011")
  def REM                = BitPat("b0000001??????????110?????0110011")
  def REMU               = BitPat("b0000001??????????111?????0110011")


  // RV64M
  def MULW               = BitPat("b0000001??????????000?????0111011")
  def DIVW               = BitPat("b0000001??????????100?????0111011")
  def DIVUW              = BitPat("b0000001??????????101?????0111011")
  def REMW               = BitPat("b0000001??????????110?????0111011")
  def REMUW              = BitPat("b0000001??????????111?????0111011")


  // RV32/64 A
  def AMOADD_W           = BitPat("b00000????????????010?????0101111")
  def AMOXOR_W           = BitPat("b00100????????????010?????0101111")
  def AMOOR_W            = BitPat("b01000????????????010?????0101111")
  def AMOAND_W           = BitPat("b01100????????????010?????0101111")
  def AMOMIN_W           = BitPat("b10000????????????010?????0101111")
  def AMOMAX_W           = BitPat("b10100????????????010?????0101111")
  def AMOMINU_W          = BitPat("b11000????????????010?????0101111")
  def AMOMAXU_W          = BitPat("b11100????????????010?????0101111")
  def AMOSWAP_W          = BitPat("b00001????????????010?????0101111")
  def LR_W               = BitPat("b00010??00000?????010?????0101111")
  def SC_W               = BitPat("b00011????????????010?????0101111")
  def AMOADD_D           = BitPat("b00000????????????011?????0101111")
  def AMOXOR_D           = BitPat("b00100????????????011?????0101111")
  def AMOOR_D            = BitPat("b01000????????????011?????0101111")
  def AMOAND_D           = BitPat("b01100????????????011?????0101111")
  def AMOMIN_D           = BitPat("b10000????????????011?????0101111")
  def AMOMAX_D           = BitPat("b10100????????????011?????0101111")
  def AMOMINU_D          = BitPat("b11000????????????011?????0101111")
  def AMOMAXU_D          = BitPat("b11100????????????011?????0101111")
  def AMOSWAP_D          = BitPat("b00001????????????011?????0101111")
  def LR_D               = BitPat("b00010??00000?????011?????0101111")
  def SC_D               = BitPat("b00011????????????011?????0101111")


  // RV32/64 C
  // RVC 00
  def C_ILLEGAL          = BitPat("b0000000000000000_000_0_00_000_00_000_00")
  def C_ADDI4SPN         = BitPat("b????????????????_000_?_??_???_??_???_00")
  def C_FLD              = BitPat("b????????????????_001_?_??_???_??_???_00")
  def C_LW               = BitPat("b????????????????_010_?_??_???_??_???_00")
  def C_LD               = BitPat("b????????????????_011_?_??_???_??_???_00")
  def C_FSD              = BitPat("b????????????????_101_?_??_???_??_???_00")
  def C_SW               = BitPat("b????????????????_110_?_??_???_??_???_00")
  def C_SD               = BitPat("b????????????????_111_?_??_???_??_???_00")
  // RVC 01
  def C_NOP              = BitPat("b????????????????_000_?_00_000_??_???_01")
  def C_ADDI             = BitPat("b????????????????_000_?_??_???_??_???_01")
  def C_JAL              = BitPat("b????????????????_001_?_??_???_??_???_01")
  def C_ADDIW            = BitPat("b????????????????_001_?_??_???_??_???_01")
  def C_LI               = BitPat("b????????????????_010_?_??_???_??_???_01")
  def C_ADDI16SP         = BitPat("b????????????????_011_?_00_010_??_???_01")
  def C_LUI              = BitPat("b????????????????_011_?_??_???_??_???_01")
  def C_SRLI             = BitPat("b????????????????_100_?_00_???_??_???_01")
  def C_SRAI             = BitPat("b????????????????_100_?_01_???_??_???_01")
  def C_ANDI             = BitPat("b????????????????_100_?_10_???_??_???_01")
  def C_SUB              = BitPat("b????????????????_100_0_11_???_00_???_01")
  def C_XOR              = BitPat("b????????????????_100_0_11_???_01_???_01")
  def C_OR               = BitPat("b????????????????_100_0_11_???_10_???_01")
  def C_AND              = BitPat("b????????????????_100_0_11_???_11_???_01")
  def C_SUBW             = BitPat("b????????????????_100_1_11_???_00_???_01")
  def C_ADDW             = BitPat("b????????????????_100_1_11_???_01_???_01")
  def C_J                = BitPat("b????????????????_101_?_??_???_??_???_01")
  def C_BEQZ             = BitPat("b????????????????_110_?_??_???_??_???_01")
  def C_BNEZ             = BitPat("b????????????????_111_?_??_???_??_???_01")
  //RVC 10
  def C_SLLI             = BitPat("b????????????????_000_?_??_???_??_???_10")
  def C_FLDSP            = BitPat("b????????????????_001_?_??_???_??_???_10")
  def C_LWSP             = BitPat("b????????????????_010_?_??_???_??_???_10")
  def C_FLWSP            = BitPat("b????????????????_011_?_??_???_??_???_10")
  def C_LDSP             = BitPat("b????????????????_011_?_??_???_??_???_10")
  def C_JR               = BitPat("b????????????????_100_0_??_???_00_000_10")
  def C_MV               = BitPat("b????????????????_100_0_??_???_??_???_10")
  def C_EBREAK           = BitPat("b????????????????_100_1_00_000_00_000_10")
  def C_JALR             = BitPat("b????????????????_100_1_??_???_00_000_10")
  def C_ADD              = BitPat("b????????????????_100_1_??_???_??_???_10")
  def C_FSDSP            = BitPat("b????????????????_101_?_??_???_??_???_10")
  def C_SWSP             = BitPat("b????????????????_110_?_??_???_??_???_10")
  def C_FSWSP            = BitPat("b????????????????_111_?_??_???_??_???_10")
  def C_SDSP             = BitPat("b????????????????_111_?_??_???_??_???_10")

  // RV32/64 FD
  def FADD_S             = BitPat("b0000000??????????????????1010011")
  def FSUB_S             = BitPat("b0000100??????????????????1010011")
  def FMUL_S             = BitPat("b0001000??????????????????1010011")
  def FDIV_S             = BitPat("b0001100??????????????????1010011")
  def FSGNJ_S            = BitPat("b0010000??????????000?????1010011")
  def FSGNJN_S           = BitPat("b0010000??????????001?????1010011")
  def FSGNJX_S           = BitPat("b0010000??????????010?????1010011")
  def FMIN_S             = BitPat("b0010100??????????000?????1010011")
  def FMAX_S             = BitPat("b0010100??????????001?????1010011")
  def FSQRT_S            = BitPat("b010110000000?????????????1010011")
  def FADD_D             = BitPat("b0000001??????????????????1010011")
  def FSUB_D             = BitPat("b0000101??????????????????1010011")
  def FMUL_D             = BitPat("b0001001??????????????????1010011")
  def FDIV_D             = BitPat("b0001101??????????????????1010011")
  def FSGNJ_D            = BitPat("b0010001??????????000?????1010011")
  def FSGNJN_D           = BitPat("b0010001??????????001?????1010011")
  def FSGNJX_D           = BitPat("b0010001??????????010?????1010011")
  def FMIN_D             = BitPat("b0010101??????????000?????1010011")
  def FMAX_D             = BitPat("b0010101??????????001?????1010011")
  def FCVT_S_D           = BitPat("b010000000001?????????????1010011")
  def FCVT_D_S           = BitPat("b010000100000?????????????1010011")
  def FSQRT_D            = BitPat("b010110100000?????????????1010011")
  def FLE_S              = BitPat("b1010000??????????000?????1010011")
  def FLT_S              = BitPat("b1010000??????????001?????1010011")
  def FEQ_S              = BitPat("b1010000??????????010?????1010011")
  def FLE_D              = BitPat("b1010001??????????000?????1010011")
  def FLT_D              = BitPat("b1010001??????????001?????1010011")
  def FEQ_D              = BitPat("b1010001??????????010?????1010011")
  def FCVT_W_S           = BitPat("b110000000000?????????????1010011")
  def FCVT_WU_S          = BitPat("b110000000001?????????????1010011")
  def FCVT_L_S           = BitPat("b110000000010?????????????1010011")
  def FCVT_LU_S          = BitPat("b110000000011?????????????1010011")
  def FMV_X_W            = BitPat("b111000000000?????000?????1010011")
  def FCLASS_S           = BitPat("b111000000000?????001?????1010011")
  def FCVT_W_D           = BitPat("b110000100000?????????????1010011")
  def FCVT_WU_D          = BitPat("b110000100001?????????????1010011")
  def FCVT_L_D           = BitPat("b110000100010?????????????1010011")
  def FCVT_LU_D          = BitPat("b110000100011?????????????1010011")
  def FMV_X_D            = BitPat("b111000100000?????000?????1010011")
  def FCLASS_D           = BitPat("b111000100000?????001?????1010011")
  def FCVT_S_W           = BitPat("b110100000000?????????????1010011")
  def FCVT_S_WU          = BitPat("b110100000001?????????????1010011")
  def FCVT_S_L           = BitPat("b110100000010?????????????1010011")
  def FCVT_S_LU          = BitPat("b110100000011?????????????1010011")
  def FMV_W_X            = BitPat("b111100000000?????000?????1010011")
  def FCVT_D_W           = BitPat("b110100100000?????????????1010011")
  def FCVT_D_WU          = BitPat("b110100100001?????????????1010011")
  def FCVT_D_L           = BitPat("b110100100010?????????????1010011")
  def FCVT_D_LU          = BitPat("b110100100011?????????????1010011")
  def FMV_D_X            = BitPat("b111100100000?????000?????1010011")
  def FLW                = BitPat("b?????????????????010?????0000111")
  def FLD                = BitPat("b?????????????????011?????0000111")
  def FLQ                = BitPat("b?????????????????100?????0000111")
  def FSW                = BitPat("b?????????????????010?????0100111")
  def FSD                = BitPat("b?????????????????011?????0100111")
  def FSQ                = BitPat("b?????????????????100?????0100111")
  def FMADD_S            = BitPat("b?????00??????????????????1000011")
  def FMSUB_S            = BitPat("b?????00??????????????????1000111")
  def FNMSUB_S           = BitPat("b?????00??????????????????1001011")
  def FNMADD_S           = BitPat("b?????00??????????????????1001111")
  def FMADD_D            = BitPat("b?????01??????????????????1000011")
  def FMSUB_D            = BitPat("b?????01??????????????????1000111")
  def FNMSUB_D           = BitPat("b?????01??????????????????1001011")
  def FNMADD_D           = BitPat("b?????01??????????????????1001111")
  def FADD_H             = BitPat("b0000010??????????????????1010011")
  def FSUB_H             = BitPat("b0000110??????????????????1010011")
  def FMUL_H             = BitPat("b0001010??????????????????1010011")
  def FDIV_H             = BitPat("b0001110??????????????????1010011")
  def FSGNJ_H            = BitPat("b0010010??????????000?????1010011")
  def FSGNJN_H           = BitPat("b0010010??????????001?????1010011")
  def FSGNJX_H           = BitPat("b0010010??????????010?????1010011")
  def FMIN_H             = BitPat("b0010110??????????000?????1010011")
  def FMAX_H             = BitPat("b0010110??????????001?????1010011")
  def FCVT_H_S           = BitPat("b010001000000?????????????1010011")
  def FCVT_S_H           = BitPat("b010000000010?????????????1010011")
  def FSQRT_H            = BitPat("b010111000000?????????????1010011")
  def FLE_H              = BitPat("b1010010??????????000?????1010011")
  def FLT_H              = BitPat("b1010010??????????001?????1010011")
  def FEQ_H              = BitPat("b1010010??????????010?????1010011")
  def FCVT_W_H           = BitPat("b110001000000?????????????1010011")
  def FCVT_WU_H          = BitPat("b110001000001?????????????1010011")
  def FMV_X_H            = BitPat("b111001000000?????000?????1010011")
  def FCLASS_H           = BitPat("b111001000000?????001?????1010011")
  def FCVT_H_W           = BitPat("b110101000000?????????????1010011")
  def FCVT_H_WU          = BitPat("b110101000001?????????????1010011")
  def FMV_H_X            = BitPat("b111101000000?????000?????1010011")
  def FCVT_H_D           = BitPat("b010001000001?????????????1010011")
  def FCVT_D_H           = BitPat("b010000100010?????????????1010011")
  def FCVT_L_H           = BitPat("b110001000010?????????????1010011")
  def FCVT_LU_H          = BitPat("b110001000011?????????????1010011")
  def FCVT_H_L           = BitPat("b110101000010?????????????1010011")
  def FCVT_H_LU          = BitPat("b110101000011?????????????1010011")
  def FLH                = BitPat("b?????????????????001?????0000111")
  def FSH                = BitPat("b?????????????????001?????0100111")
  def FMADD_H            = BitPat("b?????10??????????????????1000011")
  def FMSUB_H            = BitPat("b?????10??????????????????1000111")
  def FNMSUB_H           = BitPat("b?????10??????????????????1001011")
  def FNMADD_H           = BitPat("b?????10??????????????????1001111")

  // PEC Extension
  def CRETK              = BitPat("b0000000??????????000?????1101011")
  def CRDTK              = BitPat("b0000001??????????000?????1101011")
  def CREMK              = BitPat("b0000000??????????001?????1101011")
  def CRDMK              = BitPat("b0000001??????????001?????1101011")
  def CREAK              = BitPat("b0000000??????????010?????1101011")
  def CRDAK              = BitPat("b0000001??????????010?????1101011")
  def CREBK              = BitPat("b0000000??????????011?????1101011")
  def CRDBK              = BitPat("b0000001??????????011?????1101011")
}
