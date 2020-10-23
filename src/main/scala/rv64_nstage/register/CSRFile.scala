package rv64_nstage.register

import chisel3._
import chisel3.util._
import rv64_nstage.control._
import rv64_nstage.core._

import chisel3.util.experimental.BoringUtils

class InterruptIO extends Bundle with phvntomParams {
  val mtip = Input(Bool())
  val msip = Input(Bool())
  val meip = Input(Bool())
  val seip = Input(Bool())
}

object CSR {
  val N =           0.U(3.W)
  val W =           1.U(3.W)
  val S =           2.U(3.W)
  val C =           3.U(3.W)
  val P =           4.U(3.W)

  val PRV_U =       0x0.U(2.W)
  val PRV_S =       0x1.U(2.W)
  val PRV_H =       0x2.U(2.W)
  val PRV_M =       0x3.U(2.W)

  val cycle =       0xc00.U(12.W)
  val time =        0xc01.U(12.W)
  val instret =     0xc02.U(12.W)
  val cycleh =      0xc80.U(12.W)
  val timeh =       0xc81.U(12.W)
  val instreth =    0xc82.U(12.W)

  val cyclew =      0x900.U(12.W)
  val timew =       0x901.U(12.W)
  val instretw =    0x902.U(12.W)
  val cyclehw =     0x980.U(12.W)
  val timehw =      0x981.U(12.W)
  val instrethw =   0x982.U(12.W)

  // MACHINE MODE
  val mvendorid =   0xf11.U(12.W)
  val marchid =     0xf12.U(12.W)
  val mimpid =      0xf13.U(12.W)
  val mhartid =     0xf14.U(12.W)
  val mstatus =     0x300.U(12.W)
  val misa =        0x301.U(12.W)
  val medeleg =     0x302.U(12.W)
  val mideleg =     0x303.U(12.W)
  val mie =         0x304.U(12.W)
  val mtvec =       0x305.U(12.W)
  val mtcounteren = 0x306.U(12.W)
  val mstatush =    0x310.U(12.W)
  val mtime =       0x701.U(12.W)
  val mtimeh =      0x741.U(12.W)
  val mscratch =    0x340.U(12.W)
  val mepc =        0x341.U(12.W)
  val mcause =      0x342.U(12.W)
  val mtval =       0x343.U(12.W)
  val mip =         0x344.U(12.W)
  val mtinst =      0x34a.U(12.W)
  val mtval2 =      0x34b.U(12.W)

  // SUPERVISOR MODE
  val sstatus =     0x100.U(12.W)
  val sedeleg =     0x102.U(12.W)
  val sideleg =     0x103.U(12.W)
  val sie =         0x104.U(12.W)
  val stvec =       0x105.U(12.W)
  val scounteren =  0x106.U(12.W)
  val sscratch =    0x140.U(12.W)
  val sepc =        0x141.U(12.W)
  val scause =      0x142.U(12.W)
  val stval =       0x143.U(12.W)
  val sip =         0x144.U(12.W)
  val satp =        0x180.U(12.W)

  // PHYSICAL MEM PROTECTION
  val pmpcfg0 =     0x3a0.U(12.W)
  val pmpcfg1 =     0x3a1.U(12.W)
  val pmpcfg2 =     0x3a2.U(12.W)
  val pmpcfg3 =     0x3a3.U(12.W)
  val pmpaddr0 =    0x3b0.U(12.W)
  val pmpaddr1 =    0x3b1.U(12.W)
  val pmpaddr2 =    0x3b2.U(12.W)
  val pmpaddr3 =    0x3b3.U(12.W)

  // DEBUG
  val tselect =     0x7a0.U(12.W)
  val tdata1 =      0x7a1.U(12.W)
  val tdata2 =      0x7a2.U(12.W)
  val tdata3 =      0x7a3.U(12.W)

  // PERFORMANCE
  val mcycle =      0xb00.U(12.W)
  val minstret =    0xb02.U(12.W)
}

object SATP {
  val Bare    = 0.U(4.W)
  val Sv39    = 8.U(4.W)
}

object Exception {
  val InstAddrMisaligned = 0x0.U(4.W)
  val InstAccessFault = 0x1.U(4.W)
  val IllegalInst = 0x2.U(4.W)
  val Breakpoint = 0x3.U(4.W)
  val LoadAddrMisaligned = 0x4.U(4.W)
  val LoadAccessFault = 0x5.U(4.W)
  val StoreAddrMisaligned = 0x6.U(4.W)
  val StoreAccessFault = 0x7.U(4.W)
  val EcallU = 0x8.U(4.W)
  val EcallS = 0x9.U(4.W)
  val EcallM = 0xb.U(4.W)
  val InstPageFault = 0xc.U(4.W)
  val LoadPageFault = 0xd.U(4.W)
  val StorePageFault = 0xf.U(4.W)
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


class InterruptJudgerIO extends Bundle with phvntomParams {
  val int_vec = Input(UInt(12.W))
  val int_out = Output(UInt(4.W))
  val has_int = Output(Bool())
}

class InterruptJudger extends Module with phvntomParams {
  val io = IO(new InterruptJudgerIO)

  when(io.int_vec(11)) {
    io.int_out := 11.U
    io.has_int := true.B
  }.elsewhen(io.int_vec(9)) {
    io.int_out := 9.U
    io.has_int := true.B
  }.elsewhen(io.int_vec(7)) {
    io.int_out := 7.U
    io.has_int := true.B
  }.elsewhen(io.int_vec(5)) {
    io.int_out := 5.U
    io.has_int := true.B
  }.elsewhen(io.int_vec(3)) {
    io.int_out := 3.U
    io.has_int := true.B
  }.elsewhen(io.int_vec(1)) {
    io.int_out := 1.U
    io.has_int := true.B
  }.otherwise {
    io.int_out := 0.U
    io.has_int := false.B
  }
}


class ExceptionJudgerIO extends Bundle with phvntomParams {
  val breakpoint = Input(Bool())
  val inst_pf = Input(Bool())
  val inst_af = Input(Bool())
  val illegal_inst = Input(Bool())
  val inst_ma = Input(Bool())
  val ecall_m = Input(Bool())
  val ecall_s = Input(Bool())
  val ecall_u = Input(Bool())
  val store_ma = Input(Bool())
  val load_ma = Input(Bool())
  val store_pf = Input(Bool())
  val load_pf = Input(Bool())
  val store_af = Input(Bool())
  val load_af = Input(Bool())
  val has_except = Output(Bool())
  val except_out = Output(UInt(4.W))
}

class ExceptionJudger extends Module with phvntomParams {
  val io = IO(new ExceptionJudgerIO)

  when(io.breakpoint) {
    io.has_except := true.B
    io.except_out := Exception.Breakpoint
  }.elsewhen(io.inst_pf) {
    io.has_except := true.B
    io.except_out := Exception.InstPageFault
  }.elsewhen(io.inst_af) {
    io.has_except := true.B
    io.except_out := Exception.InstAccessFault
  }.elsewhen(io.illegal_inst) {
    io.has_except := true.B
    io.except_out := Exception.IllegalInst
  }.elsewhen(io.inst_ma) {
    io.has_except := true.B
    io.except_out := Exception.InstAddrMisaligned
  }.elsewhen(io.ecall_m) {
    io.has_except := true.B
    io.except_out := Exception.EcallM
  }.elsewhen(io.ecall_s) {
    io.has_except := true.B
    io.except_out := Exception.EcallS
  }.elsewhen(io.ecall_u) {
    io.has_except := true.B
    io.except_out := Exception.EcallU
  }.elsewhen(io.store_ma) {
    io.has_except := true.B
    io.except_out := Exception.StoreAddrMisaligned
  }.elsewhen(io.load_ma) {
    io.has_except := true.B
    io.except_out := Exception.LoadAddrMisaligned
  }.elsewhen(io.store_pf) {
    io.has_except := true.B
    io.except_out := Exception.StorePageFault
  }.elsewhen(io.load_pf) {
    io.has_except := true.B
    io.except_out := Exception.LoadPageFault
  }.elsewhen(io.store_af) {
    io.has_except := true.B
    io.except_out := Exception.StoreAccessFault
  }.elsewhen(io.load_af) {
    io.has_except := true.B
    io.except_out := Exception.LoadAccessFault
  }.otherwise {
    io.has_except := false.B
    io.except_out := Exception.IllegalInst
  }
}

class CSRFileIO extends Bundle with phvntomParams {
  // CSRRX
  val which_reg = Input(UInt(12.W))
  val wen = Input(Bool())
  val sen = Input(Bool())
  val cen = Input(Bool())
  val wdata = Input(UInt(xlen.W))
  val rdata = Output(UInt(xlen.W))
  // Golden Control Signals
  val stall = Input(Bool())
  val current_pc = Input(UInt(xlen.W))
  val bubble = Input(Bool())
  // Exceptions in RegExeMem1
  val inst_af = Input(Bool())
  val inst_pf = Input(Bool())
  val inst_ma = Input(Bool())
  val illegal_inst = Input(Bool())
  val mem_af = Input(Bool())
  val mem_ma = Input(Bool())
  val mem_pf = Input(Bool())
  val is_load = Input(Bool())
  val is_store = Input(Bool())
  val is_ecall = Input(Bool())
  val is_bpoint = Input(Bool())
  val bad_addr = Input(UInt(xlen.W))
  // Exception Return
  val is_mret = Input(Bool())
  val is_sret = Input(Bool())
  val is_uret = Input(Bool())
  // Interrupt in RegExeMem1
  val int_pend = Flipped(Flipped(new InterruptIO))
  // Some Trap-Ret Registers
  val expt_or_int_out = Output(Bool())
  val interrupt_out = Output(Bool())
  val is_ret_out = Output(Bool())
  val tvec_out = Output(UInt(xlen.W))
  val epc_out = Output(UInt(xlen.W))
  val write_satp = Output(Bool())
  val satp_val = Output(UInt(xlen.W))
  val current_p = Output(UInt(2.W))
  val force_s_mode_mem = Output(Bool())
  val mstatus_sum = Output(UInt(1.W))
}

class CSRFile extends Module with phvntomParams {
  val io = IO(new CSRFileIO)

  // Special CSR Register Bit
  // MCAUSE and SCAUSE
  val mcauser_int = RegInit(0.U(1.W))
  val mcauser_cause = RegInit(0.U(4.W))
  val scauser_int = RegInit(0.U(1.W))
  val scauser_cause = RegInit(0.U(4.W))
  // MSTATUS
  val mstatusr_sd = RegInit(false.B)
  val mstatusr_mbe = RegInit(false.B)
  val mstatusr_sbe = RegInit(false.B)
  val mstatusr_sxl = Wire(UInt(2.W))
  val mstatusr_uxl = Wire(UInt(2.W))
  val mstatusr_tsr = RegInit(false.B)
  val mstatusr_tw = RegInit(false.B)
  val mstatusr_tvm = RegInit(false.B)
  val mstatusr_mxr = RegInit(false.B)
  val mstatusr_sum = RegInit(false.B)
  val mstatusr_mprv = RegInit(false.B)
  val mstatusr_xs = RegInit(0.U(2.W))
  val mstatusr_fs = RegInit(0.U(2.W))
  val mstatusr_mpp = RegInit(0.U(2.W))
  val mstatusr_spp = RegInit(false.B)
  val mstatusr_mpie = RegInit(false.B)
  val mstatusr_ube = RegInit(false.B)
  val mstatusr_spie = RegInit(false.B)
  val mstatusr_mie = RegInit(false.B)
  val mstatusr_sie = RegInit(false.B)
  if(only_M) {
    mstatusr_sxl := 0.U
    mstatusr_uxl := 0.U
  } else {
    mstatusr_sxl := 2.U
    mstatusr_uxl := 2.U
  }
  // MIE
  val mier_meie = RegInit(false.B)
  val mier_seie = RegInit(false.B)
  val mier_mtie = RegInit(false.B)
  val mier_stie = RegInit(false.B)
  val mier_msie = RegInit(false.B)
  val mier_ssie = RegInit(false.B)
  // MIP
  val mipr_seip = RegInit(false.B)
  val mipr_stip = RegInit(false.B)
  val mipr_ssip = RegInit(false.B)
  // SATP
  val satpr_mode = RegInit(UInt(4.W), SATP.Bare)
  val satpr_asid = RegInit(UInt(16.W), 0.U)
  val satpr_ppn = RegInit(UInt(44.W), 0.U)

  // [--------- Machine Mode Registers in CSR --------]
  val mepcr = RegInit(0.U(xlen.W))
  val mcauser = Cat(mcauser_int, 0.U((xlen - 5).W), mcauser_cause)
  val mtvecr = RegInit(0.U(xlen.W))
  val mhartidr = 0.U(xlen.W)
  val mier = Cat(0.U((xlen - 12).W), mier_meie, false.B, mier_seie, false.B,
    mier_mtie, false.B, mier_stie, false.B,
    mier_msie, false.B, mier_ssie, false.B
  )
  val mipr = Cat(0.U((xlen - 12).W), io.int_pend.meip, false.B,
    mipr_seip, false.B, io.int_pend.mtip, false.B, mipr_stip, false.B,
    io.int_pend.msip, false.B, mipr_ssip, false.B
  )
  val mstatusr = Cat(mstatusr_sd, 0.U((xlen - 39).W), mstatusr_mbe, mstatusr_sbe, mstatusr_sxl, mstatusr_uxl,
    "b000000000".U(9.W), mstatusr_tsr, mstatusr_tw, mstatusr_tvm, mstatusr_mxr, mstatusr_sum,
    mstatusr_mprv, mstatusr_xs, mstatusr_fs, mstatusr_mpp, false.B, false.B,
    mstatusr_spp, mstatusr_mpie, mstatusr_ube, mstatusr_spie, false.B,
    mstatusr_mie, false.B, mstatusr_sie, false.B
  )
  val medelegr = RegInit(0.U(xlen.W))
  val midelegr = RegInit(0.U(xlen.W))
  val misar = Wire(UInt(xlen.W))
  if(only_M) {
    misar := "h8000000000001101".U
  } else {
    misar := "h8000000000141101".U
  }
  val mvendoridr = 0.U(xlen.W)
  val marchidr = 5.U(xlen.W)
  val mscratchr = RegInit(0.U(xlen.W))
  val mtvalr = RegInit(0.U(xlen.W))
  val mimpidr = RegInit(0.U(xlen.W))
  val mcycler = RegInit(UInt(64.W), 0.U)
  val minstretr = RegInit(0.U(64.W))

  // [--------- Physical Memory Protection Registers in CSR --------]
  val pmpcfg0r = RegInit(0.U(xlen.W))
  val pmpcfg2r = RegInit(0.U(xlen.W))
  val pmpaddr0r = RegInit(0.U(xlen.W))
  val pmpaddr2r = RegInit(0.U(xlen.W))

  // [--------- Supervisor Mode Registers in CSR --------]
  val sstatusr = Cat(mstatusr_sd, Fill(xlen - 2 - 33, 0.U), mstatusr_uxl, Fill(12, 0.U),
    mstatusr_mxr, mstatusr_sum, Fill(1, 0.U), mstatusr_xs, mstatusr_fs, Fill(4, 0.U),
    mstatusr_spp, Fill(1, 0.U), mstatusr_ube, mstatusr_spie, Fill(3, 0.U), mstatusr_sie, Fill(1, 0.U)
  )
  val sier = Cat(0.U((xlen - 10).W), mier_seie,
    Fill(3, 0.U), mier_stie,
    Fill(3, 0.U), mier_ssie, false.B
  )
  val sipr = Cat(0.U((xlen - 10).W), mipr_seip, Fill(3, 0.U),
    mipr_stip, Fill(3, 0.U), mipr_ssip, false.B
  )
  val stvecr = RegInit(0.U(xlen.W))
  val satpr = Cat(satpr_mode, satpr_asid, satpr_ppn)
  val sepcr = RegInit(0.U(xlen.W))
  val scauser = Cat(scauser_int, 0.U((xlen - 5).W), scauser_cause)
  val stvalr = RegInit(0.U(xlen.W))
  val sscratchr = RegInit(0.U(xlen.W))
  val scounterenr = RegInit(0.U(xlen.W))

  //  [--------- User Mode Registers in CSR --------]
  val uepcr = RegInit(0.U(xlen.W))

  // [--------- Debug Registers in CSR --------]
  val tselectr = RegInit(0.U(xlen.W))
  val tdata1r = RegInit(0.U(xlen.W))
  val tdata2r = RegInit(0.U(xlen.W))
  val tdata3r = RegInit(0.U(xlen.W))

  // Interrupt Pending For Read Signals
  val seip_for_read = io.int_pend.seip || mipr_seip

  // Current Privilege Mode and Delegation Information
  val current_p = RegInit(UInt(2.W), CSR.PRV_M)
  val ideleg = midelegr & mipr

  // Combinational Judger for Interrupt
  val int_judger = Module(new InterruptJudger)
  def int_global_enable(ideleg_bit: Bool): Bool = Mux(ideleg_bit,
    current_p < CSR.PRV_S || (current_p === CSR.PRV_S && mstatusr_sie),
    current_p < CSR.PRV_M || (current_p === CSR.PRV_M && mstatusr_mie)
  )
  val int_enable_vec = Cat(int_global_enable(ideleg(11)), int_global_enable(ideleg(10)), int_global_enable(ideleg(9)),
    int_global_enable(ideleg(8)), int_global_enable(ideleg(7)), int_global_enable(ideleg(6)),
    int_global_enable(ideleg(5)), int_global_enable(ideleg(4)), int_global_enable(ideleg(3)),
    int_global_enable(ideleg(2)), int_global_enable(ideleg(1)), int_global_enable(ideleg(0))
  )
  val int_vec = int_enable_vec & mier(11, 0) & Cat(mipr(11, 10), seip_for_read, mipr(8, 0))
  int_judger.io.int_vec := int_vec
  val has_int_comb = int_judger.io.has_int
  val int_num_comb = int_judger.io.int_out
//  printf("has_int %x, int_num %x, int_out %x\n", has_int_comb, int_num_comb, io.interrupt_out)

  // Combinational Judger for Exceptions
  val expt_judger = Module(new ExceptionJudger)
  val csr_not_exists = WireInit(false.B)
  val bad_csr_access = WireInit(false.B)
  val bad_csr_m = current_p < CSR.PRV_M
  val bad_csr_s = current_p < CSR.PRV_S
  val expt_vec = Wire(Vec(16, Bool()))
  expt_vec.map(_ := false.B)
  expt_vec(Exception.Breakpoint) := io.is_bpoint
  expt_vec(Exception.EcallM) := current_p === CSR.PRV_M && io.is_ecall
  expt_vec(Exception.EcallS) := current_p === CSR.PRV_S && io.is_ecall
  expt_vec(Exception.EcallU) := current_p === CSR.PRV_U && io.is_ecall
  expt_vec(Exception.IllegalInst) := (io.illegal_inst || ((csr_not_exists || bad_csr_access) &&
    (io.wen || io.cen || io.sen)))
  expt_vec(Exception.InstAccessFault) := io.inst_af
  expt_vec(Exception.InstAddrMisaligned) := io.inst_ma
  expt_vec(Exception.InstPageFault) := io.inst_pf
  expt_vec(Exception.LoadAccessFault) := io.mem_af && io.is_load
  expt_vec(Exception.LoadAddrMisaligned) := io.mem_ma && io.is_load
  expt_vec(Exception.LoadPageFault) := io.mem_pf && io.is_load
  expt_vec(Exception.StoreAccessFault) := io.mem_af && io.is_store
  expt_vec(Exception.StoreAddrMisaligned) := io.mem_ma && io.is_store
  expt_vec(Exception.StorePageFault) := io.mem_pf && io.is_store
  expt_judger.io.breakpoint := expt_vec(Exception.Breakpoint)
  expt_judger.io.inst_pf := expt_vec(Exception.InstPageFault)
  expt_judger.io.inst_af := expt_vec(Exception.InstAccessFault)
  expt_judger.io.illegal_inst := expt_vec(Exception.IllegalInst)
  expt_judger.io.inst_ma := expt_vec(Exception.InstAddrMisaligned)
  expt_judger.io.ecall_m := expt_vec(Exception.EcallM)
  expt_judger.io.ecall_s := expt_vec(Exception.EcallS)
  expt_judger.io.ecall_u := expt_vec(Exception.EcallU)
  expt_judger.io.store_ma := expt_vec(Exception.StoreAddrMisaligned)
  expt_judger.io.load_ma := expt_vec(Exception.LoadAddrMisaligned)
  expt_judger.io.store_pf := expt_vec(Exception.StorePageFault)
  expt_judger.io.load_pf := expt_vec(Exception.LoadPageFault)
  expt_judger.io.store_af := expt_vec(Exception.StoreAccessFault)
  expt_judger.io.load_af := expt_vec(Exception.LoadAccessFault)
  val has_expt_comb = expt_judger.io.has_except
  val expt_num_comb = expt_judger.io.except_out

  // Combinational Logic for Trap-Ret Delegations and Addresses
  val trap_addr = WireInit(0.U(xlen.W))
  val eret_addr = WireInit(0.U(xlen.W))
  val deleg = Mux(has_int_comb, midelegr, medelegr)
  val deleg_2_s = Mux(has_int_comb, deleg(int_num_comb), deleg(expt_num_comb)) && current_p < CSR.PRV_M
  val eret = io.is_mret || io.is_sret || io.is_uret
  trap_addr := Mux(deleg_2_s, Cat(stvecr(xlen - 1, 2), Fill(2, 0.U)), Cat(mtvecr(xlen - 1, 2), Fill(2, 0.U)))
  eret_addr := Mux(io.is_mret, mepcr, Mux(io.is_sret, sepcr, uepcr))

  // Output Comb Logic
  io.tvec_out := Mux(mtvecr(0) && has_int_comb, trap_addr + (int_num_comb << 2.U), trap_addr)
  io.epc_out := eret_addr
  io.expt_or_int_out := !io.stall && !io.bubble && (has_expt_comb || has_int_comb)
  io.interrupt_out := !io.stall && !io.bubble && has_int_comb
  io.is_ret_out := !io.stall && !io.bubble && eret

  // Write Signal for MTVAL or STVAL
  val write_tval = io.mem_af || io.mem_pf || io.mem_ma || io.inst_af || io.inst_pf || io.inst_ma
  val tval_value = Mux(expt_num_comb === Exception.InstAddrMisaligned, Cat(io.bad_addr(xlen - 1, 1),
    Fill(1, 0.U)), io.bad_addr
  )

  // MCYCLE and MINSTRET
  // TODO Restore + 3.U and + 1.U
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

//  printf("mtvec %x, priv %x, has_expt %x, expt_num %x\n", mtvecr, current_p, has_expt_comb, expt_num_comb)

  // CSR Read
  when(io.which_reg === CSR.mepc) {
    io.rdata := mepcr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mip) {
    io.rdata := Cat(mipr(xlen - 1, 10), seip_for_read, mipr(7, 0))
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mcause) {
    io.rdata := mcauser
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mtvec) {
    io.rdata := mtvecr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mie) {
    io.rdata := mier
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mstatus) {
    io.rdata := mstatusr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.medeleg) {
    io.rdata := medelegr
    if(only_M) {
      csr_not_exists := true.B
    } else {
      csr_not_exists := false.B
    }
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mideleg) {
    io.rdata := midelegr
    if(only_M) {
      csr_not_exists := true.B
    } else {
      csr_not_exists := false.B
    }
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.misa) {
    io.rdata := misar
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mvendorid) {
    io.rdata := mvendoridr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.marchid) {
    io.rdata := marchidr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mscratch) {
    io.rdata := mscratchr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.pmpaddr0) {
    io.rdata := pmpaddr0r
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.pmpaddr2) {
    io.rdata := pmpaddr2r
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.pmpcfg0) {
    io.rdata := pmpcfg0r
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.pmpcfg2) {
    io.rdata := pmpcfg2r
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mtval) {
    io.rdata := mtvalr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mhartid) {
    io.rdata := mhartidr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.mimpid) {
    io.rdata := mimpidr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.sstatus) {
    io.rdata := sstatusr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.stvec) {
    io.rdata := stvecr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.sie) {
    io.rdata := sier
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.sip) {
    io.rdata := sipr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.scounteren) {
    io.rdata := scounterenr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.sscratch) {
    io.rdata := sscratchr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.sepc) {
    io.rdata := sepcr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.scause) {
    io.rdata := scauser
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.stval) {
    io.rdata := stvalr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.satp) {
    io.rdata := satpr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_s
  }.elsewhen(io.which_reg === CSR.tselect) {
    io.rdata := tselectr
    csr_not_exists := false.B
    bad_csr_access := false.B
  }.elsewhen(io.which_reg === CSR.tdata1) {
    io.rdata := tdata1r
    csr_not_exists := false.B
    bad_csr_access := false.B
  }.elsewhen(io.which_reg === CSR.tdata2) {
    io.rdata := tdata2r
    csr_not_exists := false.B
    bad_csr_access := false.B
  }.elsewhen(io.which_reg === CSR.tdata3) {
    io.rdata := tdata3r
    csr_not_exists := false.B
    bad_csr_access := false.B
  }.elsewhen(io.which_reg === CSR.mcycle) {
    io.rdata := mcycler + 3.U(2.W)
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.elsewhen(io.which_reg === CSR.minstret) {
    io.rdata := minstretr
    csr_not_exists := false.B
    bad_csr_access := bad_csr_m
  }.otherwise {
    io.rdata := "hdeadbeef".U
    csr_not_exists := true.B
    bad_csr_access := bad_csr_m
  }

//  printf("mip1 %x, mie1 %x, globalen1 %x\n", mipr_ssip, mier_ssie, int_enable_vec(1))
//  printf("IN CSR sum %x, mprv %x, mpp %x, mxr %x, privlege %x\n", mstatusr_sum, mstatusr_mprv, mstatusr_mpp, mstatusr_mxr, current_p)

  // Write CSR File
  when(!io.stall && !io.bubble) {
    when(has_int_comb || has_expt_comb) {
      when(deleg_2_s) {
        when(has_int_comb) {
          scauser_int := 1.U(1.W)
          scauser_cause := int_num_comb
        }.otherwise {
          scauser_int := 0.U(1.W)
          scauser_cause := expt_num_comb
        }
        sepcr := io.current_pc
        mstatusr_spp := current_p
        mstatusr_spie := mstatusr_sie
        mstatusr_sie := false.B
        current_p := CSR.PRV_S
        when(write_tval) {
          stvalr := tval_value
        }
      }.otherwise {
        when(has_int_comb) {
          mcauser_int := 1.U(1.W)
          mcauser_cause := int_num_comb
        }.otherwise {
          mcauser_int := 0.U(1.W)
          mcauser_cause := expt_num_comb
        }
        mepcr := io.current_pc
        mstatusr_mpp := current_p
        mstatusr_mpie := mstatusr_mie
        mstatusr_mie := false.B
        current_p := CSR.PRV_M
        when(write_tval) {
          mtvalr := tval_value
        }
      }
    }.elsewhen(io.is_mret) {
      mstatusr_mie := mstatusr_mpie
      current_p := mstatusr_mpp
      mstatusr_mpie := true.B
      if (only_M) {
        mstatusr_mpp := CSR.PRV_M
      } else {
        mstatusr_mpp := CSR.PRV_U
      }
    }.elsewhen(io.is_sret) {
      mstatusr_sie := mstatusr_spie
      current_p := Cat(0.U(1.W), mstatusr_spp)
      mstatusr_spie := true.B
      mstatusr_spp := CSR.PRV_U
    }.elsewhen(io.is_uret) {
      // TODO add N Extension and U Mode
    }.otherwise {
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
      }.elsewhen(io.which_reg === CSR.mideleg) {
        when(io.wen) {
          midelegr := io.wdata
        }.elsewhen(io.sen) {
          midelegr := midelegr | io.wdata
        }.elsewhen(io.cen) {
          midelegr := midelegr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.medeleg) {
        when(io.wen) {
          medelegr := io.wdata
        }.elsewhen(io.sen) {
          medelegr := medelegr | io.wdata
        }.elsewhen(io.cen) {
          medelegr := medelegr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.mie || io.which_reg === CSR.sie) {
        when(io.wen) {
          when(io.which_reg === CSR.mie) {
            mier_meie := io.wdata(11)
            mier_mtie := io.wdata(7)
            mier_msie := io.wdata(3)
          }
          mier_seie := io.wdata(9)
          mier_stie := io.wdata(5)
          mier_ssie := io.wdata(1)
        }.elsewhen(io.sen) {
          when(io.which_reg === CSR.mie) {
            mier_meie := mier_meie | io.wdata(11)
            mier_mtie := mier_mtie | io.wdata(7)
            mier_msie := mier_msie | io.wdata(3)
          }
          mier_seie := mier_seie | io.wdata(9)
          mier_stie := mier_stie | io.wdata(5)
          mier_ssie := mier_ssie | io.wdata(1)
        }.elsewhen(io.cen) {
          when(io.which_reg === CSR.mie) {
            mier_meie := mier_meie & ~io.wdata(11)
            mier_mtie := mier_mtie & ~io.wdata(7)
            mier_msie := mier_msie & ~io.wdata(3)
          }
          mier_seie := mier_seie & ~io.wdata(9)
          mier_stie := mier_stie & ~io.wdata(5)
          mier_ssie := mier_ssie & ~io.wdata(1)
        }
      }.elsewhen(io.which_reg === CSR.mip || io.which_reg === CSR.sip) {
        when(io.wen) {
          mipr_seip := io.wdata(9)
          mipr_stip := io.wdata(5)
          mipr_ssip := io.wdata(1)
        }.elsewhen(io.sen) {
          mipr_seip := mipr_seip | io.wdata(9)
          mipr_stip := mipr_stip | io.wdata(5)
          mipr_ssip := mipr_ssip | io.wdata(1)
        }.elsewhen(io.cen) {
          mipr_seip := mipr_seip & ~io.wdata(9)
          mipr_stip := mipr_stip & ~io.wdata(5)
          mipr_ssip := mipr_ssip & ~io.wdata(1)
        }
      }.elsewhen(io.which_reg === CSR.mstatus || io.which_reg === CSR.sstatus) {
        when(io.wen) {
          when(io.which_reg === CSR.mstatus) {
            mstatusr_sd := io.wdata(xlen - 1)
            mstatusr_mbe := io.wdata(37)
            mstatusr_sbe := io.wdata(36)
            mstatusr_tsr := io.wdata(22)
            mstatusr_tw := io.wdata(21)
            mstatusr_tvm := io.wdata(20)
            mstatusr_mprv := io.wdata(17)
            if(!only_M) {
              mstatusr_mpp := io.wdata(12, 11)
            }
            mstatusr_mpie := io.wdata(7)
            mstatusr_mie := io.wdata(3)
          }
          mstatusr_mxr := io.wdata(19)
          mstatusr_sum := io.wdata(18)
          mstatusr_xs := io.wdata(16, 15)
          mstatusr_spp := io.wdata(8)
          mstatusr_ube := io.wdata(6)
          mstatusr_spie := io.wdata(5)
          mstatusr_sie := io.wdata(1)
        }.elsewhen(io.sen) {
          when(io.which_reg === CSR.mstatus) {
            mstatusr_sd := mstatusr(xlen - 1) | io.wdata(xlen - 1)
            mstatusr_mbe := mstatusr(37) | io.wdata(37)
            mstatusr_sbe := mstatusr(36) | io.wdata(36)
            mstatusr_tsr := mstatusr(22) | io.wdata(22)
            mstatusr_tw := mstatusr(21) | io.wdata(21)
            mstatusr_tvm := mstatusr(20) | io.wdata(20)
            mstatusr_mprv := mstatusr(17) | io.wdata(17)
            if(!only_M) {
              mstatusr_mpp := mstatusr(12, 11) | io.wdata(12, 11)
            }
            mstatusr_mpie := mstatusr(7) | io.wdata(7)
            mstatusr_mie := mstatusr(3) | io.wdata(3)
          }
          mstatusr_mxr := mstatusr(19) | io.wdata(19)
          mstatusr_sum := mstatusr(18) | io.wdata(18)
          mstatusr_xs := mstatusr(16, 15) | io.wdata(16, 15)
          mstatusr_spp := mstatusr(8) | io.wdata(8)
          mstatusr_ube := mstatusr(6) | io.wdata(6)
          mstatusr_spie := mstatusr(5) | io.wdata(5)
          mstatusr_sie := mstatusr(1) | io.wdata(1)
        }.elsewhen(io.cen) {
          when(io.which_reg === CSR.mstatus) {
            mstatusr_sd := mstatusr(xlen - 1) & ~io.wdata(xlen - 1)
            mstatusr_mbe := mstatusr(37) & ~io.wdata(37)
            mstatusr_sbe := mstatusr(36) & ~io.wdata(36)
            mstatusr_tsr := mstatusr(22) & ~io.wdata(22)
            mstatusr_tw := mstatusr(21) & ~io.wdata(21)
            mstatusr_tvm := mstatusr(20) & ~io.wdata(20)
            mstatusr_mprv := mstatusr(17) & ~io.wdata(17)
            if (!only_M) {
              mstatusr_mpp := mstatusr(12, 11) & ~io.wdata(12, 11)
            }
            mstatusr_mpie := mstatusr(7) & ~io.wdata(7)
            mstatusr_mie := mstatusr(3) & ~io.wdata(3)
          }
          mstatusr_mxr := mstatusr(19) & ~io.wdata(19)
          mstatusr_sum := mstatusr(18) & ~io.wdata(18)
          mstatusr_xs := mstatusr(16, 15) & ~io.wdata(16, 15)
          mstatusr_spp := mstatusr(8) & ~io.wdata(8)
          mstatusr_ube := mstatusr(6) & ~io.wdata(6)
          mstatusr_spie := mstatusr(5) & ~io.wdata(5)
          mstatusr_sie := mstatusr(1) & ~io.wdata(1)
        }
      }.elsewhen(io.which_reg === CSR.stvec) {
        when(io.wen) {
          stvecr := io.wdata
        }.elsewhen(io.sen) {
          stvecr := stvecr | io.wdata
        }.elsewhen(io.cen) {
          stvecr := stvecr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.scause) {
        when(io.wen) {
          scauser_cause := io.wdata(3, 0)
        }.elsewhen(io.sen) {
          scauser_cause := scauser_cause | io.wdata(3, 0)
        }.elsewhen(io.cen) {
          scauser_cause := scauser_cause & (~io.wdata(3, 0))
        }
      }.elsewhen(io.which_reg === CSR.sepc) {
        when(io.wen) {
          sepcr := io.wdata
        }.elsewhen(io.sen) {
          sepcr := sepcr | io.wdata
        }.elsewhen(io.cen) {
          sepcr := sepcr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.stval) {
        when(io.wen) {
          stvalr := io.wdata
        }.elsewhen(io.sen) {
          stvalr := stvalr | io.wdata
        }.elsewhen(io.cen) {
          stvalr := stvalr & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.satp) {
        when(io.wen) {
          satpr_mode := io.wdata(63, 60)
          satpr_asid := io.wdata(59, 44)
          satpr_ppn := io.wdata(43, 0)
        }.elsewhen(io.sen) {
          satpr_mode := satpr_mode | io.wdata(63, 60)
          satpr_asid := satpr_asid | io.wdata(59, 44)
          satpr_ppn := satpr_ppn | io.wdata(43, 0)
        }.elsewhen(io.cen) {
          satpr_mode := satpr_mode & (~io.wdata(63, 60))
          satpr_asid := satpr_asid & (~io.wdata(59, 44))
          satpr_ppn := satpr_ppn & (~io.wdata(43, 0))
        }
      }.elsewhen(io.which_reg === CSR.sscratch) {
        when(io.wen) {
          sscratchr := io.wdata
        }.elsewhen(io.sen) {
          sscratchr := sscratchr | io.wdata
        }.elsewhen(io.cen) {
          sscratchr := sscratchr & (~io.wdata)
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
      }.elsewhen(io.which_reg === CSR.pmpaddr0) {
        when(io.wen) {
          pmpaddr0r := io.wdata
        }.elsewhen(io.sen) {
          pmpaddr0r := pmpaddr0r | io.wdata
        }.elsewhen(io.cen) {
          pmpaddr0r := pmpaddr0r & (~io.wdata)
        }
      }.elsewhen(io.which_reg === CSR.pmpcfg0) {
        when(io.wen) {
          pmpcfg0r := io.wdata
        }.elsewhen(io.sen) {
          pmpcfg0r := pmpcfg0r | io.wdata
        }.elsewhen(io.cen) {
          pmpcfg0r := pmpcfg0r & (~io.wdata)
        }
      }
    }
  }

  io.write_satp := io.which_reg === CSR.satp && (io.wen || io.cen || io.sen)
  io.satp_val := satpr
  io.current_p := current_p
  io.force_s_mode_mem := mstatusr_mpp === CSR.PRV_S && mstatusr_mprv
  io.mstatus_sum := mstatusr_sum

  if(diffTest) {
    BoringUtils.addSource(mstatusr, "difftestmstatusr")
    BoringUtils.addSource(mcycler, "difftestmcycler")
    BoringUtils.addSource(current_p, "difftestprivilege")
  }
}

class CSRIO extends Bundle with phvntomParams {
  // CSRXX
  val stall = Input(Bool())
  val bubble = Input(Bool())
  val cmd = Input(UInt(ControlConst.wenBits.W))
  val in = Input(UInt(xlen.W))
  val out = Output(UInt(xlen.W))
  // Stall Request
  val stall_req = Output(Bool())
  // Exception
  val pc = Input(UInt(xlen.W))
  val illegal_mem_addr = Input(Bool())
  val illegal_inst_addr = Input(Bool())
  val inst = Input(UInt(32.W))
  val illegal = Input(Bool())
  val is_load = Input(Bool())
  val is_store = Input(Bool())
  val inst_access_fault = Input(Bool())
  val mem_access_fault = Input(Bool())
  val inst_page_fault = Input(Bool())
  val mem_page_fault = Input(Bool())
  // Output
  val expt = Output(Bool())
  val int = Output(Bool())
  val ret = Output(Bool())
  val write_satp = Output(Bool())
  val evec = Output(UInt(xlen.W))
  val epc = Output(UInt(xlen.W))
  val pc_plus = Output(UInt(xlen.W))
  val satp_val = Output(UInt(xlen.W))
  val current_p = Output(UInt(2.W))
  val force_s_mode_mem = Output(Bool())
  val mstatus_sum = Output(UInt(1.W))
  // Interrupt
  val tim_int = Input(Bool())
  val soft_int = Input(Bool())
  val external_int = Input(Bool())
}

class CSR extends Module with phvntomParams {
  val io = IO(new CSRIO)
  val csr_regfile = Module(new CSRFile)

  val csr_addr = io.inst(31, 20)
  val read_only_csr = csr_addr(11) & csr_addr(10)

  csr_regfile.io.which_reg := csr_addr
  csr_regfile.io.wen := io.cmd === ControlConst.wenCSRW
  csr_regfile.io.cen := io.cmd === ControlConst.wenCSRC
  csr_regfile.io.sen := io.cmd === ControlConst.wenCSRS
  csr_regfile.io.wdata := io.in
  csr_regfile.io.stall := io.stall
  csr_regfile.io.current_pc := Cat(io.pc(31, 2), 0.U(2.W))
  csr_regfile.io.is_mret := io.inst === "b00110000001000000000000001110011".U
  csr_regfile.io.is_sret := io.inst === "b00010000001000000000000001110011".U
  csr_regfile.io.is_uret := io.inst === "b00000000001000000000000001110011".U
  csr_regfile.io.bad_addr := io.in
  csr_regfile.io.bubble := io.bubble
  csr_regfile.io.inst_af := io.inst_access_fault
  csr_regfile.io.inst_pf := io.inst_page_fault
  csr_regfile.io.inst_ma := io.illegal_inst_addr
  csr_regfile.io.illegal_inst := io.illegal
  csr_regfile.io.mem_af := io.mem_access_fault
  csr_regfile.io.mem_ma := io.illegal_mem_addr
  csr_regfile.io.mem_pf := io.mem_page_fault
  csr_regfile.io.is_load := io.is_load
  csr_regfile.io.is_store := io.is_store
  csr_regfile.io.is_ecall := io.inst === "b00000000000000000000000001110011".U
  csr_regfile.io.is_bpoint := io.inst === "b00000000000100000000000001110011".U
  csr_regfile.io.int_pend.msip := io.soft_int
  csr_regfile.io.int_pend.meip := io.external_int
  csr_regfile.io.int_pend.mtip := io.tim_int
  csr_regfile.io.int_pend.seip := false.B

  io.out := csr_regfile.io.rdata
  io.int := csr_regfile.io.interrupt_out
  io.expt := csr_regfile.io.expt_or_int_out
  io.evec := csr_regfile.io.tvec_out
  io.epc := csr_regfile.io.epc_out
  io.ret := csr_regfile.io.is_ret_out
  io.stall_req := false.B
  io.write_satp := csr_regfile.io.write_satp
  io.pc_plus := io.pc + 4.U(3.W)
  io.satp_val := csr_regfile.io.satp_val
  io.current_p := csr_regfile.io.current_p
  io.force_s_mode_mem := csr_regfile.io.force_s_mode_mem
  io.mstatus_sum := csr_regfile.io.mstatus_sum
}
