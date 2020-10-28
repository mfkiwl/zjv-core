package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import device._
import utils._

case class WBConfig(
    wb_name: String = "write buffer", // used for debug info
    dataWidth: Int = 64,
    entries: Int = 4
)

trait WBParameters extends phvntomParams {
  implicit val wbConfig: WBConfig

  val wb_name = wbConfig.wb_name
  val dataWidth = wbConfig.dataWidth
  val nEntry = wbConfig.entries
}

class WBEntry(implicit val wbConfig: WBConfig)
    extends Bundle
    with WBParameters {
  val valid = Bool()
  val need_write = Bool()
  val addr = UInt(xlen.W)
  val data = UInt(dataWidth.W)
  val memtype = UInt(xlen.W)

  override def toPrintable: Printable =
    p"WBEntry(valid=${valid}, addr = 0x${Hexadecimal(addr)},memtype = ${memtype}\n\tdata = 0x${Hexadecimal(data)}"
}

class WriteBuffer(implicit val wbConfig: WBConfig)
    extends Module
    with WBParameters {
  val io = IO(new Bundle {
    val in = new MemIO(dataWidth)
    val readChannel = new MemIO(dataWidth)
    val writeChannel = new MemIO(dataWidth)
  })

  val entryArray =
    RegInit(Vec(nEntry, new WBEntry), 0.U.asTypeOf(Vec(nEntry, new WBEntry)))

  val occupy_vec = VecInit(entryArray.map(m => m.valid)).asUInt
  val current_index = PriorityEncoder(occupy_vec)
  val write_vec = VecInit(entryArray.map(m => m.valid)).asUInt

  val s_idle :: s_req :: s_resp :: s_flush :: Nil = Enum(4)
  val state = RegInit(s_idle)


  io.in.req.ready := !occupy_vec.andR

}
