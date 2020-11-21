package rv64_nstage.mmu

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams
import utils._
import rv64_nstage.register.SATP
import rv64_nstage.register.CSR
import mem._
import device._

// The privilege Mode should flow with the pipeline because
// CSR is in Mem1 but this DMMU might be in later Stage
//   [ITLB]      [DTLB]
//     |           |
//     |           | 
//     |           |
//   [PTW]       [PTW]    [ICACHE]  [DCACHE]
//     |           |         |          |
//     |           |         |          |
//     |           |         |          |
//    ||||||||||    L2 Cache    ||||||||||

case class MMUConfig(
    name: String = "mmu", // used for debug info
    isdmmu : Boolean = false,
    entries: Int = 2, // number of entries
)

trait MMUParameters extends phvntomParams {
  implicit val mmuConfig: MMUConfig
  val mmuName = mmuConfig.name
  val isdmmu = mmuConfig.isdmmu
  val nEntry = mmuConfig.entries
}

class MMUFrontIO(implicit val mmuConfig: MMUConfig)
    extends Bundle
    with MMUParameters {
  val valid = Input(Bool())
  val va = Input(UInt(xlen.W))
  val flush_all = Input(Bool())
  val satp_val = Input(UInt(xlen.W))
  val current_p = Input(UInt(2.W))
  val force_s_mode = Input(Bool())
  val sum = Input(UInt(1.W))
  val mxr = Input(UInt(1.W))
  val mpp_s = Input(Bool())
  // Protection
  val is_inst = Input(Bool())
  val is_load = Input(Bool())
  val is_store = Input(Bool())
  // Output
  val stall_req = Output(Bool())
  val pa = Output(UInt(xlen.W))
  val pf = Output(Bool())
  val af = Output(Bool()) // TODO PMA PMP to generate access fault
  val is_idle = Output(Bool())

  override def toPrintable: Printable = p"valid = ${valid}, va = 0x${Hexadecimal(va)}, flush_all = ${flush_all}\nsatp_val=0x${Hexadecimal(satp_val)}, current_p = ${current_p}, force_s_mode=${force_s_mode}, sum=${sum}\nis_inst=${is_inst}, is_load=${is_load}, is_store=${is_store}\nstall_req=${stall_req}, pa=0x${Hexadecimal(pa)}, pf=${pf}, af=${af}"
}

class MMUBackIO(implicit val mmuConfig: MMUConfig)
    extends Bundle
    with MMUParameters {
  // Memory Interface
  val mmu = Flipped(new MemIO(cachiLine * cachiBlock))

  override def toPrintable: Printable = p"${mmu}"
}

class MMU(implicit val mmuConfig: MMUConfig) extends Module with MMUParameters {
  val io = IO(new Bundle {
    val front = new MMUFrontIO
    val back = new MMUBackIO
  })

  val tlb = Module(new TLB)
  val ptw = Module(new PTWalker)
  io.front <> tlb.io.in
  ptw.io.out <> io.back
  tlb.io.out <> ptw.io.in
}
