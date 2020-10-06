package rv64_3stage

import chisel3._
import chisel3.util._

class InterruptIO extends Bundle with phvntomParams {
  val mtip = Input(Bool())
  val msip = Input(Bool())
}

object CSR {
  val N = 0.U(3.W)
  val W = 1.U(3.W)
  val S = 2.U(3.W)
  val C = 3.U(3.W)
  val P = 4.U(3.W)

  val PRV_U = 0x0.U(2.W)
  val PRV_S = 0x1.U(2.W)
  val PRV_H = 0x2.U(2.W)
  val PRV_M = 0x3.U(2.W)

  val cycle = 0xc00.U(12.W)
  val time = 0xc01.U(12.W)
  val instret = 0xc02.U(12.W)
  val cycleh = 0xc80.U(12.W)
  val timeh = 0xc81.U(12.W)
  val instreth = 0xc82.U(12.W)

  val cyclew = 0x900.U(12.W)
  val timew = 0x901.U(12.W)
  val instretw = 0x902.U(12.W)
  val cyclehw = 0x980.U(12.W)
  val timehw = 0x981.U(12.W)
  val instrethw = 0x982.U(12.W)

  val mvendorid = 0xf11.U(12.W)
  val marchid = 0xf12.U(12.W)
  val mimpid = 0xf13.U(12.W)
  val mhartid = 0xf14.U(12.W)
  val mstatus = 0x300.U(12.W)
  val misa = 0x301.U(12.W)
  val medeleg = 0x302.U(12.W)
  val mideleg = 0x303.U(12.W)
  val mie = 0x304.U(12.W)
  val mtvec = 0x305.U(12.W)
  val mtcounteren = 0x306.U(12.W)
  val mstatush = 0x310.U(12.W)
  val mtime = 0x701.U(12.W)
  val mtimeh = 0x741.U(12.W)
  val mscratch = 0x340.U(12.W)
  val mepc = 0x341.U(12.W)
  val mcause = 0x342.U(12.W)
  val mtval = 0x343.U(12.W)
  val mip = 0x344.U(12.W)
  val mtinst = 0x34a.U(12.W)
  val mtval2 = 0x34b.U(12.W)
  val pmpcfg0 = 0x3a0.U(12.W)
  val pmpaddr0 = 0x3b0.U(12.W)
  // DEBUG
  val tselect = 0x7a0.U(12.W)
  val tdata1 = 0x7a1.U(12.W)
  val tdata2 = 0x7a2.U(12.W)
  val tdata3 = 0x7a3.U(12.W)
  // PERFORMANCE
  val mcycle = 0xb00.U(12.W)
  val minstret = 0xb02.U(12.W)
}

object Exception {
  val InstAddrMisaligned = 0x0.U(4.W)
  val InstAccessFault = 0x1.U(4.W) // TODO
  val IllegalInst = 0x2.U(4.W) // TODO
  val Breakpoint = 0x3.U(4.W) // TODO
  val LoadAddrMisaligned = 0x4.U(4.W)
  val LoadAccessFault = 0x5.U(4.W) // TODO
  val StoreAddrMisaligned = 0x6.U(4.W)
  val StoreAccessFault = 0x7.U(4.W) // TODO
  val EcallU = 0x8.U(4.W) // TODO
  val EcallS = 0x9.U(4.W) // TODO
  val EcallM = 0xb.U(4.W) // TODO
  val InstPageFault = 0xc.U(4.W) // TODO
  val LoadPageFault = 0xd.U(4.W) // TODO
  val StorePageFault = 0xf.U(4.W) // TODO
  val exceptionBits = InstAddrMisaligned.getWidth
}

object Interrupt {
  val ReservedInterrupt = 0x0.U
  val SSoftwareInterrupt = 0x1.U
  val MSoftwareInterrupt = 0x3.U
  val STimerInterrupt = 0x5.U
  val MTimerInterrupt = 0x7.U
  val SExternalInterrupt = 0x9.U
  val MExternalInterrupt = 0xb.U
}

class CSRFileIO extends Bundle with phvntomParams {
  val which_reg = Input(UInt(12.W))
  val current_pc = Input(UInt(xlen.W))
  val wen = Input(Bool())
  val sen = Input(Bool())
  val cen = Input(Bool())
  val wdata = Input(UInt(xlen.W))
  val illegal_addr = Input(UInt(xlen.W))
  val has_int = Input(Bool())
  val int_type = Input(UInt(4.W))
  val has_except = Input(Bool())
  val except_type = Input(UInt(4.W))
  val stall = Input(Bool())
  val bubble = Input(Bool())
  val is_eret = Input(Bool())
  val rdata = Output(UInt(xlen.W))
  val evec_out = Output(UInt(xlen.W))
  val epc_out = Output(UInt(xlen.W))
  val global_int_enable = Output(Bool())
  val illegal_csr = Output(Bool())
}

class CSRFile extends Module with phvntomParams {
  val io = IO(new CSRFileIO)

  // Special CSR Register Bit
  // MCAUSE
  val mcauser_int = RegInit(0.U(1.W))
  val mcauser_cause = RegInit(0.U(4.W))
  // MSTATUS
  val mstatusr_sd = RegInit(false.B)
  val mstatusr_mbe = RegInit(false.B)
  val mstatusr_sbe = RegInit(false.B)
  val mstatusr_sxl = RegInit(0.U(2.W))
  val mstatusr_uxl = RegInit(0.U(2.W))
  val mstatusr_tsr = RegInit(false.B)
  val mstatusr_tw = RegInit(false.B)
  val mstatusr_tvm = RegInit(false.B)
  val mstatusr_mxr = RegInit(false.B)
  val mstatusr_sum = RegInit(false.B)
  val mstatusr_mprv = RegInit(false.B)
  val mstatusr_xs = RegInit(0.U(2.W))
  val mstatusr_fs = RegInit(0.U(2.W))
  val mstatusr_mpp = RegInit(3.U(2.W)) // When a trap is taken from privilege mode y into privilege mode x, x PIE is set to the value of x IE;
  val mstatusr_spp = RegInit(false.B) //  x IE is set to 0; and x PP is set to y. MPP is always 3, because M-Mode only
  val mstatusr_mpie = RegInit(false.B)
  val mstatusr_ube = RegInit(false.B)
  val mstatusr_spie = RegInit(false.B)
  val mstatusr_mie = RegInit(false.B)
  val mstatusr_sie = RegInit(false.B)
  // MIE
  val mier_meie = RegInit(false.B)
  val mier_seie = RegInit(false.B)
  val mier_mtie = RegInit(false.B)
  val mier_stie = RegInit(false.B)
  val mier_msie = RegInit(false.B)
  val mier_ssie = RegInit(false.B)

  // normal registers in CSR
  val mepcr = RegInit(0.U(xlen.W))
  val mcauser = Cat(mcauser_int, 0.U((xlen - 5).W), mcauser_cause)
  val mtvecr = RegInit(0.U(xlen.W))
  val mhartidr = 0.U(xlen.W)
  val mier = Cat(0.U((xlen - 12).W), mier_meie, false.B, mier_seie, false.B,
    mier_mtie, false.B, mier_stie, false.B,
    mier_msie, false.B, mier_ssie, false.B) // interrupt[i] => mi(e/p)r[i], no s-mode, hardwired to zero
  val mstatusr = Cat(mstatusr_sd, 0.U((xlen - 39).W), mstatusr_mbe, mstatusr_sbe, mstatusr_sxl, mstatusr_uxl,
    "b000000000".U(9.W), mstatusr_tsr, mstatusr_tw, mstatusr_tvm, mstatusr_mxr, mstatusr_sum,
    mstatusr_mprv, mstatusr_xs, mstatusr_fs, mstatusr_mpp, false.B, false.B,
    mstatusr_spp, mstatusr_mpie, mstatusr_ube, mstatusr_spie, false.B,
    mstatusr_mie, false.B, mstatusr_sie, false.B)
  val medelegr = RegInit(0.U(xlen.W)) // never delegate the handler to other lower modes currently
  val midelegr = RegInit(0.U(xlen.W)) // so their values are 0
  val misar = Cat(2.U(2.W), 0.U((xlen - 2 - 13).W), true.B, 0.U(3.W), true.B, 0.U(8.W)) // rv64+im
  val mvendoridr = 0.U(xlen.W)
  val marchidr = 5.U(xlen.W)
  val mscratchr = RegInit(0.U(xlen.W))
  val mtvalr = RegInit(0.U(xlen.W))
  val mimpidr = RegInit(0.U(xlen.W))
  val mcycler = RegInit(UInt(64.W), 0.U)
  val minstretr = RegInit(0.U(64.W))

  // debug registers
  val tselectr = RegInit(0.U(xlen.W))
  val tdata1r = RegInit(0.U(xlen.W))
  val tdata2r = RegInit(0.U(xlen.W))
  val tdata3r = RegInit(0.U(xlen.W))

  // combo-logic for int control, machine mode only now
  val current_prv = CSR.PRV_M // support Machine mode only
  val machine_int_global_enable = current_prv != CSR.PRV_M || mstatusr_mie
  val machine_int_enable = mier(io.int_type) & machine_int_global_enable
  val csr_not_exists = WireInit(false.B)
  io.global_int_enable := machine_int_enable

  // mcycle and minstret increment
  when(!io.stall && io.which_reg === CSR.mcycle) {
    when(io.wen) {
      mcycler := io.wdata
    }.elsewhen(io.sen) {
      when(io.wdata.orR) {
        mcycler := mcycler | io.wdata
      }.otherwise {
        mcycler := mcycler + 1.U(1.W)
      }
    }.elsewhen(io.cen) {
      mcycler := mcycler & (~io.wdata)
    }
  }.otherwise {
    mcycler := mcycler + 1.U(1.W)
  }
  when(!io.stall && io.which_reg === CSR.minstret) {
    when(io.wen) {
      minstretr := io.wdata
    }.elsewhen(io.sen) {
      minstretr := minstretr | io.wdata
    }.elsewhen(io.cen) {
      minstretr := minstretr & (~io.wdata)
    }
  }.elsewhen(!io.stall && !io.bubble) {
    minstretr := minstretr + 1.U(1.W)
  }

  // combo-logic for output
  when(io.which_reg === CSR.mepc) {
    io.rdata := mepcr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mcause) {
    io.rdata := mcauser
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mtvec) {
    io.rdata := mtvecr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mie) {
    io.rdata := mier
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mstatus) {
    io.rdata := mstatusr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.medeleg) {
    io.rdata := medelegr
    csr_not_exists := true.B // TODO no register in M
  }.elsewhen(io.which_reg === CSR.mideleg) {
    io.rdata := midelegr
    csr_not_exists := true.B // TODO no register in M
  }.elsewhen(io.which_reg === CSR.misa) {
    io.rdata := misar
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mvendorid) {
    io.rdata := mvendoridr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.marchid) {
    io.rdata := marchidr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mscratch) {
    io.rdata := mscratchr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.pmpaddr0 || io.which_reg === CSR.pmpcfg0) { // TODO implementation
    io.rdata := mhartidr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mtval) {
    io.rdata := mtvalr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mhartid) {
    io.rdata := mhartidr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mimpid) {
    io.rdata := mimpidr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.tselect) {
    io.rdata := tselectr
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.tdata1) {
    io.rdata := tdata1r
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.tdata2) {
    io.rdata := tdata2r
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.tdata3) {
    io.rdata := tdata3r
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.mcycle) {
    io.rdata := mcycler + 1.U(1.W)
    csr_not_exists := false.B
  }.elsewhen(io.which_reg === CSR.minstret) {
    io.rdata := minstretr
    csr_not_exists := false.B
  }.otherwise {
    io.rdata := mhartidr
    csr_not_exists := true.B
  }
  io.epc_out := mepcr
  io.evec_out := mtvecr

  // seq-logic to write csr file
  when(!io.stall) {
    when((io.has_int & machine_int_enable) | io.has_except | io.illegal_csr) { // handle interrupt and exception
      when(io.has_int) {
        mepcr := io.current_pc + 4.U
        mcauser_int := 1.U(1.W)
        mcauser_cause := io.int_type
      }.otherwise {
        mepcr := io.current_pc
        mcauser_int := 0.U(1.W)
        mcauser_cause := io.except_type
        when(io.except_type === Exception.LoadAddrMisaligned || io.except_type === Exception.StoreAddrMisaligned) {
          mtvalr := io.illegal_addr
        }.elsewhen(io.except_type === Exception.InstAddrMisaligned) {
          mtvalr := Cat(io.illegal_addr(xlen - 1, 1), Fill(1, 0.U))
        }
      }
      // disable int enable, machine mode only for now
      mstatusr_mpie := mstatusr_mie
      mstatusr_mie := false.B
      mstatusr_mpp := CSR.PRV_M
    }.elsewhen(io.is_eret) { // enable interrupt signal again
      mstatusr_mie := mstatusr_mpie
      mstatusr_mpie := true.B
      mstatusr_mpp := CSR.PRV_M
    }.otherwise { // handle CSRXX instructions
      when(io.which_reg === CSR.mepc) {
        when(io.wen) {
          mepcr := io.wdata
        }.elsewhen(io.sen) {
          mepcr := mepcr | io.wdata
        }.elsewhen(io.cen) {
          mepcr := mepcr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.mcause) {
        when(io.wen) {
          mcauser_cause := io.wdata(3, 0)
        }.elsewhen(io.sen) {
          mcauser_cause := mcauser_cause | io.wdata(3, 0)
        }.elsewhen(io.cen) {
          mcauser_cause := mcauser_cause & (~io.wdata(3, 0))
        }
      }.elsewhen(io.which_reg === CSR.mtvec) {
        when(io.wen) {
          mtvecr := io.wdata
        }.elsewhen(io.sen) {
          mtvecr := mtvecr | io.wdata
        }.elsewhen(io.cen) {
          mtvecr := mtvecr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.mscratch) {
        when(io.wen) {
          mscratchr := io.wdata
        }.elsewhen(io.sen) {
          mscratchr := mscratchr | io.wdata
        }.elsewhen(io.cen) {
          mscratchr := mscratchr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.mtval) {
        when(io.wen) {
          mtvalr := io.wdata
        }.elsewhen(io.sen) {
          mtvalr := mtvalr | io.wdata
        }.elsewhen(io.cen) {
          mtvalr := mtvalr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.mimpid) {
        when(io.wen) {
          mimpidr := io.wdata
        }.elsewhen(io.sen) {
          mimpidr := mimpidr | io.wdata
        }.elsewhen(io.cen) {
          mimpidr := mimpidr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.mie) {
        when(io.wen) {
          mier_meie := io.wdata(11)
          mier_seie := io.wdata(9)
          mier_mtie := io.wdata(7)
          mier_stie := io.wdata(5)
          mier_msie := io.wdata(3)
          mier_ssie := io.wdata(1)
        }.elsewhen(io.sen) {
          mier_meie := mier_meie | io.wdata(11)
          mier_seie := mier_seie | io.wdata(9)
          mier_mtie := mier_mtie | io.wdata(7)
          mier_stie := mier_stie | io.wdata(5)
          mier_mtie := mier_mtie | io.wdata(3)
          mier_stie := mier_stie | io.wdata(1)
        }.elsewhen(io.cen) {
          mier_meie := mier_meie & ~io.wdata(11)
          mier_seie := mier_seie & ~io.wdata(9)
          mier_mtie := mier_mtie & ~io.wdata(7)
          mier_stie := mier_stie & ~io.wdata(5)
          mier_mtie := mier_mtie & ~io.wdata(3)
          mier_stie := mier_stie & ~io.wdata(1)
        }
      }.elsewhen(io.which_reg === CSR.mstatus) {
        when(io.wen) {
          mstatusr_sd := io.wdata(xlen - 1)
          mstatusr_mbe := io.wdata(37)
          mstatusr_sbe := io.wdata(36)
          mstatusr_sxl := io.wdata(35, 34)
          mstatusr_uxl := io.wdata(33, 32)
          mstatusr_tsr := io.wdata(22)
          mstatusr_tw := io.wdata(21)
          mstatusr_tvm := io.wdata(20)
          mstatusr_mxr := io.wdata(19)
          mstatusr_sum := io.wdata(18)
          mstatusr_mprv := io.wdata(17)
          mstatusr_xs := io.wdata(16, 15)
          mstatusr_fs := io.wdata(14, 13)
          // mstatusr_mpp := io.wdata(12, 11) TODO M-Mode, so always 3
          mstatusr_spp := io.wdata(8)
          mstatusr_mpie := io.wdata(7)
          mstatusr_ube := io.wdata(6)
          mstatusr_spie := io.wdata(5)
          mstatusr_mie := io.wdata(3)
          mstatusr_sie := io.wdata(1)
        }.elsewhen(io.sen) {
          mstatusr_sd := mstatusr(xlen - 1) | io.wdata(xlen - 1)
          mstatusr_mbe := mstatusr(37) | io.wdata(37)
          mstatusr_sbe := mstatusr(36) | io.wdata(36)
          mstatusr_sxl := mstatusr(35, 34) | io.wdata(35, 34)
          mstatusr_uxl := mstatusr(33, 32) | io.wdata(33, 32)
          mstatusr_tsr := mstatusr(22) | io.wdata(22)
          mstatusr_tw := mstatusr(21) | io.wdata(21)
          mstatusr_tvm := mstatusr(20) | io.wdata(20)
          mstatusr_mxr := mstatusr(19) | io.wdata(19)
          mstatusr_sum := mstatusr(18) | io.wdata(18)
          mstatusr_mprv := mstatusr(17) | io.wdata(17)
          mstatusr_xs := mstatusr(16, 15) | io.wdata(16, 15)
          mstatusr_fs := mstatusr(14, 13) | io.wdata(14, 13)
          // mstatusr_mpp := mstatusr(12, 11) | io.wdata(12, 11) TODO M-Mode, so always 3
          mstatusr_spp := mstatusr(8) | io.wdata(8)
          mstatusr_mpie := mstatusr(7) | io.wdata(7)
          mstatusr_ube := mstatusr(6) | io.wdata(6)
          mstatusr_spie := mstatusr(5) | io.wdata(5)
          mstatusr_mie := mstatusr(3) | io.wdata(3)
          mstatusr_sie := mstatusr(1) | io.wdata(1)
        }.elsewhen(io.cen) {
          mstatusr_sd := mstatusr(xlen - 1) & ~io.wdata(xlen - 1)
          mstatusr_mbe := mstatusr(37) & ~io.wdata(37)
          mstatusr_sbe := mstatusr(36) & ~io.wdata(36)
          mstatusr_sxl := mstatusr(35, 34) & ~io.wdata(35, 34)
          mstatusr_uxl := mstatusr(33, 32) & ~io.wdata(33, 32)
          mstatusr_tsr := mstatusr(22) & ~io.wdata(22)
          mstatusr_tw := mstatusr(21) & ~io.wdata(21)
          mstatusr_tvm := mstatusr(20) & ~io.wdata(20)
          mstatusr_mxr := mstatusr(19) & ~io.wdata(19)
          mstatusr_sum := mstatusr(18) & ~io.wdata(18)
          mstatusr_mprv := mstatusr(17) & ~io.wdata(17)
          mstatusr_xs := mstatusr(16, 15) & ~io.wdata(16, 15)
          mstatusr_fs := mstatusr(14, 13) & ~io.wdata(14, 13)
          // mstatusr_mpp := mstatusr(12, 11) & ~io.wdata(12, 11) TODO M-Mode, so always 3
          mstatusr_spp := mstatusr(8) & ~io.wdata(8)
          mstatusr_mpie := mstatusr(7) & ~io.wdata(7)
          mstatusr_ube := mstatusr(6) & ~io.wdata(6)
          mstatusr_spie := mstatusr(5) & ~io.wdata(5)
          mstatusr_mie := mstatusr(3) & ~io.wdata(3)
          mstatusr_sie := mstatusr(1) & ~io.wdata(1)
        }
      }.elsewhen(io.which_reg === CSR.tselect) {
        when(io.wen) {
          tselectr := io.wdata
        }.elsewhen(io.sen) {
          tselectr := tselectr | io.wdata
        }.elsewhen(io.cen) {
          tselectr := tselectr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.tdata1) {
        when(io.wen) {
          tdata1r := io.wdata
        }.elsewhen(io.sen) {
          tdata1r := tdata1r | io.wdata
        }.elsewhen(io.cen) {
          tdata1r := tdata1r & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.tdata2) {
        when(io.wen) {
          tdata2r := io.wdata
        }.elsewhen(io.sen) {
          tdata2r := tdata2r | io.wdata
        }.elsewhen(io.cen) {
          tdata2r := tdata2r & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.tdata3) {
        when(io.wen) {
          tdata3r := io.wdata
        }.elsewhen(io.sen) {
          tdata3r := tdata3r | io.wdata
        }.elsewhen(io.cen) {
          tdata3r := tdata3r & (~io.wdata)
        }
      }
    }
  }

  io.illegal_csr := csr_not_exists & (io.cen | io.wen | io.sen)
}

class ExceptionJudgerIO extends Bundle with phvntomParams {
  val if_inst_addr = Input(UInt(xlen.W))
  val if_pc_check = Input(Bool())
  val if_pf = Input(Bool())
  val decode_illegal_inst = Input(Bool())
  val mem_is_ld = Input(Bool())
  val mem_is_st = Input(Bool())
  val illegal_mem_addr = Input(Bool())
  val illegal_inst_addr = Input(Bool())
  val inst_access_fault = Input(Bool())
  val mem_access_fault = Input(Bool())
  val mem_pf = Input(Bool())
  val mem_type = Input(UInt(ControlConst.memBits.W))
  val wb_inst = Input(UInt(32.W))
  val has_except = Output(Bool())
  val except_out = Output(UInt(4.W))
}

class ExceptionJudger extends Module with phvntomParams {
  val io = IO(new ExceptionJudgerIO)

  when(io.inst_access_fault) {
    io.has_except := true.B
    io.except_out := Exception.InstAccessFault
  }.elsewhen(io.if_pf) {
    io.has_except := true.B
    io.except_out := Exception.InstPageFault
  }.elsewhen(io.decode_illegal_inst) {
    io.has_except := true.B
    io.except_out := Exception.IllegalInst
  }.elsewhen(io.if_pc_check & io.illegal_inst_addr) {
    io.has_except := true.B
    io.except_out := Exception.InstAddrMisaligned
  }.elsewhen(io.mem_is_ld) {
    when(io.illegal_mem_addr) {
      io.has_except := true.B
      io.except_out := Exception.LoadAddrMisaligned
    }.elsewhen(io.mem_access_fault) {
      io.has_except := true.B
      io.except_out := Exception.LoadAccessFault
    }.elsewhen(io.mem_pf) {
      io.has_except := true.B
      io.except_out := Exception.LoadPageFault
    }.otherwise {
      io.has_except := false.B
      io.except_out := Exception.InstAddrMisaligned
    }
  }.elsewhen(io.mem_is_st) {
    when(io.illegal_mem_addr) {
      io.has_except := true.B
      io.except_out := Exception.StoreAddrMisaligned
    }.elsewhen(io.mem_access_fault) {
      io.has_except := true.B
      io.except_out := Exception.StoreAccessFault
    }.elsewhen(io.mem_pf) {
      io.has_except := true.B
      io.except_out := Exception.StorePageFault
    }.otherwise {
      io.has_except := false.B
      io.except_out := Exception.InstAddrMisaligned
    }
  }.elsewhen(io.wb_inst === "b00000000000000000000000001110011".U) { // only supports Machine Mode
    io.has_except := true.B
    io.except_out := Exception.EcallM
  }.elsewhen(io.wb_inst === "b00000000000100000000000001110011".U) {
    io.has_except := true.B
    io.except_out := Exception.Breakpoint
  }.otherwise {
    io.has_except := false.B
    io.except_out := Exception.IllegalInst
  }
}

class InterruptJudgerIO extends Bundle with phvntomParams {
  val software_int = Input(Bool())
  val timer_int = Input(Bool())
  val external_int = Input(Bool())
  val int_mode = Input(UInt(1.W)) // 0 for machine mode, 1 for supervisor mode
  val int_out = Output(UInt(4.W))
  val has_int = Output(Bool())
}

class InterruptJudger extends Module with phvntomParams {
  val io = IO(new InterruptJudgerIO)

  when(io.external_int) {
    io.has_int := true.B
    when(io.int_mode === 0.U(1.W)) {
      io.int_out := Interrupt.MExternalInterrupt
    }.otherwise {
      io.int_out := Interrupt.SExternalInterrupt
    }
  }.elsewhen(io.software_int) {
    io.has_int := true.B
    when(io.int_mode === 0.U(1.W)) {
      io.int_out := Interrupt.MSoftwareInterrupt
    }.otherwise {
      io.int_out := Interrupt.SSoftwareInterrupt
    }
  }.elsewhen(io.timer_int) {
    io.has_int := true.B
    when(io.int_mode === 0.U(1.W)) {
      io.int_out := Interrupt.MTimerInterrupt
    }.otherwise {
      io.int_out := Interrupt.STimerInterrupt
    }
  }.otherwise {
    io.has_int := false.B
    io.int_out := Interrupt.ReservedInterrupt
  }
}

class CSRIO extends Bundle with phvntomParams {
  // CSRXX
  val stall = Input(Bool())
  val cmd = Input(UInt(ControlConst.wenBits.W))
  val in = Input(UInt(xlen.W))
  val out = Output(UInt(xlen.W))
  // Minstret
  val bubble = Input(Bool())
  // Exception
  val pc = Input(UInt(xlen.W))
  val illegal_mem_addr = Input(Bool())
  val illegal_inst_addr = Input(Bool())
  val inst = Input(UInt(32.W))
  val illegal = Input(Bool())
  val is_load = Input(Bool())
  val is_store = Input(Bool())
  val mem_type = Input(UInt(ControlConst.memBits.W))
  val pc_check = Input(Bool())
  val inst_access_fault = Input(Bool())
  val mem_access_fault = Input(Bool())
  val expt = Output(Bool())
  val ret = Output(Bool())
  val evec = Output(UInt(xlen.W))
  val epc = Output(UInt(xlen.W))
  // Interrupt
  val tim_int = Input(Bool())
  val soft_int = Input(Bool())
  val external_int = Input(Bool())
}

class CSR extends Module with phvntomParams {
  val io = IO(new CSRIO)
  val csr_regfile = Module(new CSRFile)
  val interrupt_judger = Module(new InterruptJudger)
  val exception_judger = Module(new ExceptionJudger)

  val csr_addr = io.inst(31, 20)
  val read_only_csr = csr_addr(11) & csr_addr(10)

  interrupt_judger.io.timer_int := io.tim_int
  interrupt_judger.io.software_int := io.soft_int
  interrupt_judger.io.external_int := io.external_int
  interrupt_judger.io.int_mode := 0.U(1.W) // machine mode only TODO

  exception_judger.io.if_inst_addr := io.pc
  exception_judger.io.if_pc_check := io.pc_check
  exception_judger.io.if_pf := false.B
  exception_judger.io.decode_illegal_inst := io.illegal
  exception_judger.io.mem_is_ld := io.is_load
  exception_judger.io.mem_is_st := io.is_store
  exception_judger.io.illegal_mem_addr := io.illegal_mem_addr
  exception_judger.io.illegal_inst_addr := io.illegal_inst_addr
  exception_judger.io.inst_access_fault := io.inst_access_fault
  exception_judger.io.mem_access_fault := io.mem_access_fault
  exception_judger.io.mem_pf := false.B
  exception_judger.io.mem_type := io.mem_type
  exception_judger.io.wb_inst := io.inst

  csr_regfile.io.which_reg := csr_addr
  csr_regfile.io.wen := io.cmd === ControlConst.wenCSRW
  csr_regfile.io.cen := io.cmd === ControlConst.wenCSRC
  csr_regfile.io.sen := io.cmd === ControlConst.wenCSRS
  csr_regfile.io.wdata := io.in
  csr_regfile.io.stall := io.stall
  csr_regfile.io.has_except := exception_judger.io.has_except
  csr_regfile.io.except_type := exception_judger.io.except_out
  csr_regfile.io.has_int := interrupt_judger.io.has_int
  csr_regfile.io.int_type := interrupt_judger.io.int_out
  csr_regfile.io.current_pc := Cat(io.pc(31, 2), 0.U(2.W))
  csr_regfile.io.is_eret := io.inst === "b00110000001000000000000001110011".U
  csr_regfile.io.illegal_addr := io.in
  csr_regfile.io.bubble := io.bubble

  io.out := csr_regfile.io.rdata
  io.expt := ((interrupt_judger.io.has_int & csr_regfile.io.global_int_enable) |
    exception_judger.io.has_except | csr_regfile.io.illegal_csr) // here we temporarily do not consider if the CSR file is valid
  io.evec := csr_regfile.io.evec_out
  io.epc := csr_regfile.io.epc_out
  io.ret := csr_regfile.io.is_eret
}
