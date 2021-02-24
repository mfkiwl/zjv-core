package device

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import tile._
import tile.common.control.ControlConst._

class MemReq(val dataWidth: Int = 64) extends Bundle with phvntomParams { // write
  val addr = Output(UInt(xlen.W))
  val data = Output(UInt(dataWidth.W))
  val wen = Output(Bool())
  val memtype = Output(UInt(memBits.W))

  override def toPrintable: Printable = p"addr = 0x${Hexadecimal(addr)}, wen = ${wen}, memtype = ${memtype}\n\tdata = 0x${Hexadecimal(data)}"
}

class MemResp(val dataWidth: Int = 64) extends Bundle with phvntomParams { // read
  val data = Output(UInt(dataWidth.W))

  override def toPrintable: Printable = p"data = 0x${Hexadecimal(data)}"
}

class MemIO(val dataWidth: Int = 64) extends Bundle with phvntomParams {
  val req = Flipped(Decoupled(new MemReq(dataWidth)))
  val resp = Decoupled(new MemResp(dataWidth))
  val stall = Input(Bool())
  val flush = Input(Bool())
  val flush_ready = Output(Bool())

  override def toPrintable: Printable = p"stall=${stall}, flush=${flush}, flush_ready=${flush_ready}, req: valid=${req.valid}, ready=${req.ready}, ${req.bits}\nresp: valid=${resp.valid}, ready=${resp.ready}, ${resp.bits}\n"
}

class SimMemIO extends Bundle with phvntomParams {
  val clk = Input(Clock())
  val raddr = Input(UInt(xlen.W))
  val rdata = Output(UInt(xlen.W))
  val waddr = Input(UInt(xlen.W))
  val wdata = Input(UInt(xlen.W))
  val wmask = Input(UInt(xlen.W))
  val wen = Input(Bool())
}

class SimMem extends BlackBox {
  val io = IO(new SimMemIO)
}

class FPGAMem extends Module with phvntomParams {
  val io = IO(new SimMemIO)
  // TODO initialize memory
  // refer to https://github.com/freechipsproject/chisel3/wiki/Chisel-Memories
  val mem = Mem(4096, UInt(xlen.W))
  //  IPORT
  io.rdata := mem(io.raddr)

  //  DPORT
  when (io.wen) {
    val data = mem(io.waddr)
    mem(io.waddr) := (io.wdata & io.wmask) | (data & ~io.wmask)
  }  
}
