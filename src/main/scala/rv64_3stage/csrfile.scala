package rv64_3stage

import chisel3._
import chisel3.util._

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
}

object Exception {
  val InstAddrMisaligned = 0x0.U
  val InstAccessFault = 0x1.U
  val IllegalInst = 0x2.U
  val Breakpoint = 0x3.U
  val LoadAddrMisaligned = 0x4.U
  val LoadAccessFault = 0x5.U
  val StoreAddrMisaligned = 0x6.U
  val StoreAccessFault = 0x7.U
  val EcallU = 0x8.U
  val EcallS = 0x9.U
  val EcallM = 0xb.U
  val InstPageFault = 0xc.U
  val LoadPageFault = 0xd.U
  val StorePageFault = 0xf.U
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
  val has_int = Input(Bool())
  val int_type = Input(UInt(4.W))
  val has_except = Input(Bool())
  val except_type = Input(UInt(4.W))
  val stall = Input(Bool())
  val is_eret = Input(Bool())
  val rdata = Output(UInt(xlen.W))
  val evec_out = Output(UInt(xlen.W))
  val epc_out = Output(UInt(xlen.W))
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
  val mstatusr_mpp = RegInit(0.U(2.W)) // When a trap is taken from privilege mode y into privilege mode x, x PIE is set to the value of x IE;
  val mstatusr_spp = RegInit(false.B) //  x IE is set to 0; and x PP is set to y.
  val mstatusr_mpie = RegInit(false.B)
  val mstatusr_ube = RegInit(false.B)
  val mstatusr_spie = RegInit(false.B)
  val mstatusr_mie = RegInit(true.B)
  val mstatusr_sie = RegInit(false.B)
  // MIE
  val mier_meie = RegInit(true.B)
  val mier_seie = RegInit(false.B)
  val mier_mtie = RegInit(true.B)
  val mier_stie = RegInit(false.B)
  val mier_msie = RegInit(true.B)
  val mier_ssie = RegInit(false.B)
  // MIP
  val mipr_meip = RegInit(false.B) // MEIP is read-only in mip, and is set and cleared by a platform-specific interrupt controller.
  val mipr_seip = RegInit(false.B)
  val mipr_mtip = RegInit(false.B) // MTIP is read-only in mip, and is cleared by writing to the memory-mapped machine-mode timer compare register.
  val mipr_stip = RegInit(false.B)
  val mipr_msip = RegInit(false.B) // read-only
  val mipr_ssip = RegInit(false.B)

  // normal registers in CSR
  val mepcr = RegInit(0.U(xlen.W))
  val mcauser = Cat(mcauser_int, 0.U((xlen - 5).W), mcauser_cause)
  val mtvecr = RegInit(0.U(xlen.W))
  val mhartidr = 0.U(xlen.W)
  val mipr = Cat(0.U((xlen - 12).W), mipr_meip, false.B, mipr_seip, false.B,
    mipr_mtip, false.B, mipr_stip, false.B, mipr_msip, false.B,
    mipr_msip, false.B, mipr_ssip, false.B) // contains information on pending interrupts
  val mier = Cat(0.U((xlen - 12).W), mier_meie, false.B, mier_seie, false.B,
    mier_mtie, false.B, mier_stie, false.B, mier_msie, false.B,
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
  val marchidr = 0.U(xlen.W)

  // SOME IMPORTANT INFORMATION
  // By default, M-mode interrupts are globally enabled if the hart’s current privilege mode is less than
  // M, or if the current privilege mode is M and the MIE bit in the mstatus register is set.
  // If bit i
  // in mideleg is set, however, interrupts are considered to be globally enabled if the hart’s current
  // privilege mode equals the delegated privilege mode and that mode’s interrupt enable bit (x IE in
  // mstatus for mode x ) is set, or if the current privilege mode is less than the delegated privilege
  // mode.

  // combo-logic for int control, machine mode only now
  val current_prv = CSR.PRV_M // support Machine mode only
  val machine_int_global_enable = current_prv != CSR.PRV_M || mstatusr_mie
  val machine_int_enable = mier(io.int_type) & mipr(io.int_type) & machine_int_global_enable

  // combo-logic for output
  when(io.which_reg === CSR.mepc) {
    io.rdata := mepcr
  }.elsewhen(io.which_reg === CSR.mcause) {
    io.rdata := mcauser
  }.elsewhen(io.which_reg === CSR.mtvec) {
    io.rdata := mtvecr
  }.elsewhen(io.which_reg === CSR.mip) {
    io.rdata := mipr
  }.elsewhen(io.which_reg === CSR.mie) {
    io.rdata := mier
  }.elsewhen(io.which_reg === CSR.mstatus) {
    io.rdata := mstatusr
  }.elsewhen(io.which_reg === CSR.medeleg) {
    io.rdata := medelegr
  }.elsewhen(io.which_reg === CSR.mideleg) {
    io.rdata := midelegr
  }.elsewhen(io.which_reg === CSR.misa) {
    io.rdata := misar
  }.elsewhen(io.which_reg === CSR.mvendorid) {
    io.rdata := mvendoridr
  }.elsewhen(io.which_reg === CSR.marchid) {
    io.rdata := marchidr
  }.otherwise {
    io.rdata := mhartidr
  }
  io.epc_out := mepcr
  io.evec_out := mtvecr

  // seq-logic to write csr file
  when(!io.stall) {
    when((io.has_int & machine_int_enable) | io.has_except) { // handle interrupt and exception
      mepcr := io.current_pc
      when(io.has_int) {
        mcauser_int := 1.U(1.W)
        mcauser_cause := io.int_type
      }.otherwise {
        mcauser_int := 0.U(1.W)
        mcauser_cause := io.except_type
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
      }.elsewhen(io.which_reg === CSR.mip) {
        when(io.wen) {
          mipr_meip := io.wdata(11)
          mipr_seip := io.wdata(9)
          mipr_mtip := io.wdata(7)
          mipr_stip := io.wdata(5)
          mipr_msip := io.wdata(3)
          mipr_ssip := io.wdata(1)
        }.elsewhen(io.sen) {
          mipr_meip := mipr_meip | io.wdata(11)
          mipr_seip := mipr_seip | io.wdata(9)
          mipr_mtip := mipr_mtip | io.wdata(7)
          mipr_stip := mipr_stip | io.wdata(5)
          mipr_mtip := mipr_mtip | io.wdata(3)
          mipr_stip := mipr_stip | io.wdata(1)
        }.elsewhen(io.cen) {
          mipr_meip := mipr_meip & ~io.wdata(11)
          mipr_seip := mipr_seip & ~io.wdata(9)
          mipr_mtip := mipr_mtip & ~io.wdata(7)
          mipr_stip := mipr_stip & ~io.wdata(5)
          mipr_mtip := mipr_mtip & ~io.wdata(3)
          mipr_stip := mipr_stip & ~io.wdata(1)
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
          // mstatusr := io.wdata
        }.elsewhen(io.sen) {
          // mstatusr := mstatusr | io.wdata
        }.elsewhen(io.cen) {
          // mstatusr := mstatusr & (~io.wdata)
        }
      }
    }
  }
}

class ExceptionJudgerIO extends Bundle with phvntomParams {
  val if_inst_addr = Input(UInt(xlen.W))
  val if_pc_check = Input(Bool())
  val if_pf = Input(Bool())
  val decode_illegal_inst = Input(Bool())
  val mem_is_ld = Input(Bool())
  val mem_is_st = Input(Bool())
  val mem_ls_addr = Input(UInt(xlen.W))
  val mem_pf = Input(Bool())
  val mem_type = Input(UInt(ControlConst.memBits.W))
  val wb_cmd = Input(Bool())
  val wb_csr_addr = Input(UInt(12.W))
  val has_except = Output(Bool())
  val except_out = Output(UInt(4.W))
}

class ExceptionJudger extends Module with phvntomParams {
  val io = IO(new ExceptionJudgerIO)

  val illegal_mem_addr = MuxLookup(io.mem_type, false.B, Seq(
    ControlConst.memByte -> false.B, ControlConst.memByteU -> false.B,
    ControlConst.memHalf -> io.mem_ls_addr(0), ControlConst.memHalfU -> io.mem_ls_addr(0),
    ControlConst.memWord -> io.mem_ls_addr(1, 0).orR, ControlConst.memWordU -> io.mem_ls_addr(1, 0).orR,
    ControlConst.memDouble -> io.mem_ls_addr(2, 0).orR
  ))

  when(io.if_pc_check & io.if_inst_addr(1, 0).orR) {
    io.has_except := true.B
    io.except_out := Exception.InstAddrMisaligned
  }.elsewhen(io.if_pf) {
    io.has_except := true.B
    io.except_out := Exception.InstPageFault
  }.elsewhen(io.decode_illegal_inst) {
    io.has_except := true.B
    io.except_out := Exception.IllegalInst
  }.elsewhen(io.mem_is_ld) {
    when(illegal_mem_addr) {
      io.has_except := true.B
      io.except_out := Exception.LoadAddrMisaligned
    }.elsewhen(io.mem_pf) {
      io.has_except := true.B
      io.except_out := Exception.LoadPageFault
    }.otherwise {
      io.has_except := false.B
      io.except_out := Exception.InstAddrMisaligned
    }
  }.elsewhen(io.mem_is_st) {
    when(illegal_mem_addr) {
      io.has_except := true.B
      io.except_out := Exception.StoreAddrMisaligned
    }.elsewhen(io.mem_pf) {
      io.has_except := true.B
      io.except_out := Exception.StorePageFault
    }.otherwise {
      io.has_except := false.B
      io.except_out := Exception.InstAddrMisaligned
    }
  }.elsewhen(io.wb_cmd === CSR.P && !io.wb_csr_addr(0) && !io.wb_csr_addr(8)) { // only supports Machine Mode
    io.has_except := true.B
    io.except_out := Exception.EcallM
  }.elsewhen(io.wb_cmd === CSR.P && io.wb_csr_addr(0) && !io.wb_csr_addr(8)) {
    io.has_except := true.B
    io.except_out := Exception.Breakpoint
  }.otherwise {
    io.has_except := false.B
    io.except_out := Exception.InstAddrMisaligned
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
  // Exception
  val pc = Input(UInt(xlen.W))
  val addr = Input(UInt(xlen.W))
  val inst = Input(UInt(xlen.W))
  val illegal = Input(Bool())
  val is_load = Input(Bool())
  val is_store = Input(Bool())
  val mem_type = Input(UInt(ControlConst.memBits.W))
  val pc_check = Input(Bool())
  val expt = Output(Bool())
  val evec = Output(UInt(xlen.W))
  val epc = Output(UInt(xlen.W))
  // Interrupt
  val tim_int = Input(Bool())
  val soft_int = Input(Bool())
  val external_int = Input(Bool())
  // MMIO registers read write
  // val mmio_req = Valid(new MemReq)
  // val mmio_resp = Valid(new MemResp)
  // val stall_req = Output(Bool())
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
  exception_judger.io.mem_ls_addr := io.addr
  exception_judger.io.mem_pf := false.B
  exception_judger.io.mem_type := io.mem_type
  exception_judger.io.wb_cmd := io.cmd
  exception_judger.io.wb_csr_addr := csr_addr

  csr_regfile.io.which_reg := csr_addr
  csr_regfile.io.wen := io.cmd === CSR.W
  csr_regfile.io.cen := io.cmd === CSR.C
  csr_regfile.io.sen := io.cmd === CSR.S
  csr_regfile.io.wdata := io.in
  csr_regfile.io.stall := io.stall
  csr_regfile.io.has_except := exception_judger.io.has_except
  csr_regfile.io.except_type := exception_judger.io.except_out
  csr_regfile.io.has_int := interrupt_judger.io.has_int
  csr_regfile.io.int_type := interrupt_judger.io.int_out
  csr_regfile.io.current_pc := Cat(io.pc(31, 2), 0.U(2.W))
  csr_regfile.io.is_eret := io.cmd === CSR.P && !csr_addr(0) && csr_addr(8)

  io.out := csr_regfile.io.rdata
  io.expt := exception_judger.io.has_except // here we temporarily do not consider if the CSR file is valid
  io.evec := csr_regfile.io.evec_out
  io.epc := csr_regfile.io.epc_out
}
