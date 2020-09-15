package rv64_3stage

import chisel3._
import chisel3.util._

import ControlConst._

class MemReq extends Bundle() with phvntomParams {  // write
  val addr  = Output(UInt(xlen.W))
  val data  = Output(UInt(xlen.W))
  val wen   = Output(Bool())
  val wtype = Output(UInt(xlen.W))
}

class MemResp extends Bundle() with phvntomParams {  // read
  val data = UInt(xlen.W)
}

class MemIO extends Bundle with phvntomParams {
  val req  = Flipped(Valid(new MemReq))
  val resp = Valid(new MemResp)
}

class simpleMem extends Module with phvntomParams {
  val io = IO(new Bundle() {
    val iport = new MemIO
    val dport = new MemIO
  })

  val mem = Module(new SimMem)

  mem.io.clk := clock

  //  IPORT
  io.iport.resp.valid := io.iport.req.valid

  val iport_addr = io.iport.req.bits.addr
  mem.io.iaddr := Cat(iport_addr(63,3),0.U(3.W))

  val itype = io.iport.req.bits.wtype
  val imask = MuxCase("hffffffffffffffff".U(64.W), Array(
    (itype === memByte)  -> "h00000000000000ff".U(64.W),
    (itype === memHalf)  -> "h000000000000ffff".U(64.W),
    (itype === memWord)  -> "h00000000ffffffff".U(64.W),
    (itype === memByteU) -> "h00000000000000ff".U(64.W),
    (itype === memHalfU) -> "h000000000000ffff".U(64.W),
    (itype === memWordU) -> "h00000000ffffffff".U(64.W)
  ))

  val iport_offset = Wire(UInt())
  iport_offset := iport_addr(2, 0) << 3
  mem.io.imask := imask << iport_offset

  val inst = mem.io.idata >> iport_offset
  io.iport.resp.bits.data := MuxCase(inst, Array(
    (itype === memByte) -> Cat(Fill(56, inst(7)), inst(7,0)),
    (itype === memHalf) -> Cat(Fill(48, inst(15)), inst(15,0)),
    (itype === memWord) -> Cat(Fill(32, inst(15)), inst(31,0)),
    (itype === memByteU) -> Cat(Fill(56, 0.U), inst(7,0)),
    (itype === memHalfU) -> Cat(Fill(48, 0.U), inst(15,0)),
    (itype === memWordU) -> Cat(Fill(32, 0.U), inst(31,0))
  ))

  //  DPORT
  io.dport.resp.valid := io.dport.req.valid

  val dport_addr = io.dport.req.bits.addr
  mem.io.daddr := Cat(dport_addr(63,3),0.U(3.W))

  val dtype = io.dport.req.bits.wtype
  val dmask = MuxCase("hffffffffffffffff".U(64.W), Array(
    (dtype === memByte)  -> "h00000000000000ff".U(64.W),
    (dtype === memHalf)  -> "h000000000000ffff".U(64.W),
    (dtype === memWord)  -> "h00000000ffffffff".U(64.W),
    (dtype === memByteU) -> "h00000000000000ff".U(64.W),
    (dtype === memHalfU) -> "h000000000000ffff".U(64.W),
    (dtype === memWordU) -> "h00000000ffffffff".U(64.W)
  ))

  val dport_offset = Wire(UInt())
  dport_offset := dport_addr(2, 0) << 3
  mem.io.dmask := dmask << dport_offset

  mem.io.dwen   := io.dport.req.bits.wen
  mem.io.dwdata := io.dport.req.bits.data << dport_offset

  val data = mem.io.drdata >> dport_offset
  io.dport.resp.bits.data := MuxCase(data, Array(
    (dmask === memByte) -> Cat(Fill(56, data(7)), data(7,0)),
    (dmask === memHalf) -> Cat(Fill(48, data(15)), data(15,0)),
    (dmask === memWord) -> Cat(Fill(32, data(15)), data(31,0)),
    (dmask === memByteU) -> Cat(Fill(56, 0.U), data(7,0)),
    (dmask === memHalfU) -> Cat(Fill(48, 0.U), data(15,0)),
    (dmask === memWordU) -> Cat(Fill(32, 0.U), data(31,0))
  ))
}

class SimMemIO extends Bundle with phvntomParams {
  val clk    = Input(Clock())
  val iaddr  = Input(UInt(xlen.W))
  val idata  = Output(UInt(xlen.W))
  val imask  = Input(UInt(xlen.W))
  val daddr  = Input(UInt(xlen.W))
  val drdata = Output(UInt(xlen.W))
  val dwdata = Input(UInt(xlen.W))
  val dmask  = Input(UInt(xlen.W))
  val dwen   = Input(Bool())
}

class SimMem extends BlackBox{
   val io = IO(new SimMemIO)
}