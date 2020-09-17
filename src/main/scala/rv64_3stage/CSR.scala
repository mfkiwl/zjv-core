package rv64_3stage

import chisel3._
import chisel3.util._

// These registers are copied from RISCV-MINI
object CSR {
  // CSR operations
  val N = 0.U(3.W)
  val W = 1.U(3.W)
  val S = 2.U(3.W)
  val C = 3.U(3.W)
  val P = 4.U(3.W)

  // Supports machine & user modes
  val PRV_U = 0x0.U(2.W)
  val PRV_M = 0x3.U(2.W)

  // User-level CSR addrs
  val cycle = 0xc00.U(12.W)
  val time = 0xc01.U(12.W)
  val instret = 0xc02.U(12.W)
  val cycleh = 0xc80.U(12.W)
  val timeh = 0xc81.U(12.W)
  val instreth = 0xc82.U(12.W)

  // Supervisor-level CSR addrs
  val cyclew = 0x900.U(12.W)
  val timew = 0x901.U(12.W)
  val instretw = 0x902.U(12.W)
  val cyclehw = 0x980.U(12.W)
  val timehw = 0x981.U(12.W)
  val instrethw = 0x982.U(12.W)

  // Machine-level CSR addrs
  // Machine Information Registers
  val mcpuid = 0xf00.U(12.W)
  val mimpid = 0xf01.U(12.W)
  val mhartid = 0xf10.U(12.W)
  // Machine Trap Setup
  val mstatus = 0x300.U(12.W)
  val mtvec = 0x301.U(12.W)
  val mtdeleg = 0x302.U(12.W)
  val mie = 0x304.U(12.W)
  val mtimecmp = 0x321.U(12.W)
  // Machine Timers and Counters
  val mtime = 0x701.U(12.W)
  val mtimeh = 0x741.U(12.W)
  // Machine Trap Handling
  val mscratch = 0x340.U(12.W)
  val mepc = 0x341.U(12.W)
  val mcause = 0x342.U(12.W)
  val mbadaddr = 0x343.U(12.W)
  val mip = 0x344.U(12.W)
  // Machine HITF
  val mtohost = 0x780.U(12.W)
  val mfromhost = 0x781.U(12.W)

  val regs = List(
    cycle, time, instret, cycleh, timeh, instreth,
    cyclew, timew, instretw, cyclehw, timehw, instrethw,
    mcpuid, mimpid, mhartid, mtvec, mtdeleg, mie,
    mtimecmp, mtime, mtimeh, mscratch, mepc, mcause, mbadaddr, mip,
    mtohost, mfromhost, mstatus)
}

object Cause {
  val InstAddrMisaligned = 0x0.U
  val IllegalInst = 0x2.U
  val Breakpoint = 0x3.U
  val LoadAddrMisaligned = 0x4.U
  val StoreAddrMisaligned = 0x6.U
  val Ecall = 0x8.U
}

class CSRIO extends Bundle with phvntomParams {
  // CSRXX
  // Stall signal, this signal freeze all
  val stall = Input(Bool())
  // Command type, eg CSRRW is W
  val cmd = Input(UInt(3.W))
  // The value write to csr_addr
  val in = Input(UInt(xlen.W))
  // The value of the register of csr_addr
  val out = Output(UInt(xlen.W))
  // Exception
  // Excpetion pc
  val pc = Input(UInt(xlen.W))
  // The address of icache or dcache
  val addr = Input(UInt(xlen.W))
  // The instruction itself
  val inst = Input(UInt(xlen.W))
  // Is the instruction illegal
  val illegal = Input(Bool())
  val st_type = Input(UInt(2.W))
  val ld_type = Input(UInt(3.W))
  val pc_check = Input(Bool())
  // Combinational output, jump to EVEC
  val expt = Output(Bool())
  // ISR base address
  val evec = Output(UInt(xlen.W))
  // The pc with
  val epc = Output(UInt(xlen.W))
  // HTIF
  // val host = new HostIO
}

class CSR extends Module with phvntomParams {
  val io = IO(new CSRIO)

  // Instantiate the registers defined in CSR
  val mepcr = RegInit(0.U(xlen.W))
  val mcauser = RegInit(0.U(xlen.W))
  val mbadaddrr = RegInit(0.U(xlen.W))
  val mstatusr = RegInit(0.U(xlen.W))

  // Constant
  val mevecr = RegInit(0.U(xlen.W))

  // Mapper
  val csr_mapper = Seq(
    BitPat(CSR.mepc) -> mepcr,
    BitPat(CSR.mcause) -> mcauser,
    BitPat(CSR.mbadaddr) -> mbadaddrr,
    BitPat(CSR.mstatus) -> mstatusr
  )

  // Mini Instruction Decoder for CSR instructions
  // These functions are similar to MTC0 and MFC0 in MIPS
  val csr_addr = io.inst(31, 20)
  val rs1_addr = io.inst(19, 15)
  val uimm = io.inst(19, 15)
  val funct3 = io.inst(14, 12)
  val rd_addr = io.inst(11, 7)

  // Write, Clear, Set signals
  val wen = (io.cmd === CSR.W || io.cmd === CSR.C || io.cmd === CSR.S)
  val wdata = MuxLookup(io.cmd, 0.U, Seq(
    CSR.W -> io.in,
    CSR.S -> (io.out | io.in),
    CSR.C -> (io.out & ~io.in)
  ))

  // CSR read and write by CSRXX instructions
  // In fact, I am not sure if Chisel will
  // interpret this to non-blocking assignment or not
  // Hopefully, it is non-blocking, but it will still work fine
  // even if it will turn out to be in blocking mode
  when(wen) {
    // Do mapping
    when(csr_addr === CSR.mepc) {
      mepcr := wdata
    }.elsewhen(csr_addr === CSR.mcause) {
      mcauser := wdata
    }.elsewhen(csr_addr === CSR.mbadaddr) {
      mbadaddrr := wdata
    }.elsewhen(csr_addr === CSR.mstatus) {
      mstatusr := wdata
    }
  }

  // Output Configuration
  io.out := Lookup(csr_addr, 0.U, csr_mapper).asUInt

  // Exception and Interrupt output
  val invalid_ia = io.pc_check & io.addr(1, 0).orR
  val invalid_la = MuxLookup(io.ld_type, false.B, Seq(

  ))
  val invalid_sa = MuxLookup(io.st_type, false.B, Seq(

  ))
  // This might lead to complex combinational logic
  // Just ignore it now. Who cares?
  val invalid_csr_op = false.B

  io.expt := io.illegal | invalid_ia | invalid_la | invalid_sa | invalid_csr_op
  io.epc := mepcr
  io.evec := mevecr

  // Change some registers
  // Change EPC now
  // The EPC will change in next cycle just in time
  when(io.expt) {
    mepcr := io.pc
  }
}
