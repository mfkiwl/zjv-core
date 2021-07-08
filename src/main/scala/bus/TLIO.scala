package bus

import chisel3._
import chisel3.util._
import tile._

// ref: https://github.com/merledu/TileLink/blob/0b18d248e98abbd226db2de9b558493c5e592294/src/main/scala/merl/uit/tilelink/Config.scala
trait TLParameters extends phvntomParams {
  val TL_AW = xlen                // AW -> the default width of address bus
  val TL_DW = xlen                // DW -> the default width of data bus
  val TL_AIW = 4                  // AIW -> Address source identifier bus width
  val TL_DIW = 1                  // DIW -> Sink bits width
  val TL_DBW = (TL_DW >> 3)       // Number of data bytes generated (DW/8)
  val TL_SZW = log2Ceil(TL_DBW)   // The size width of operation in power of 2 represented in bytes
}

trait TLOps {
  val TL_PUT_FULL_DATA = 0.U(3.W)
  val TL_PUT_PARTIAL_DATA = 1.U(3.W)
  val TL_GET = 4.U(3.W)
  val AccessAck = 0.U(2.W)
  val AccessAckData = 1.U(2.W)
}

class TLBundleA extends Bundle with TLParameters {
  val valid = Output(Bool())
  val opcode = Output(UInt(3.W))
  val param = Output(UInt(3.W))
  val size = Output(UInt(TL_SZW.W))
  val source = Output(UInt(TL_AIW.W))
  val address = Output(UInt(TL_AW.W))
  val mask = Output(UInt(TL_DBW.W))
  val data = Output(UInt(TL_DW.W))
  val corrupt = Output(Bool())
  val ready = Input(Bool())
}

class TLBundleB extends Bundle with TLParameters {
  val valid = Input(Bool())
  val opcode = Input(UInt(3.W))
  val param = Input(UInt(3.W))
  val size = Input(UInt(TL_SZW.W))
  val source = Input(UInt(TL_AIW.W))
  val address = Input(UInt(TL_AW.W))
  val mask = Input(UInt(TL_DBW.W))
  val data = Input(UInt(TL_DW.W))
  val corrupt = Input(Bool())
  val ready = Output(Bool())
}

class TLBundleC extends Bundle with TLParameters {
  val valid = Output(Bool())
  val opcode = Output(UInt(3.W))
  val param = Output(UInt(3.W))
  val size = Output(UInt(TL_SZW.W))
  val source = Output(UInt(TL_AIW.W))
  val address = Output(UInt(TL_AW.W))
  val data = Output(UInt(TL_DW.W))
  val corrupt = Output(Bool())
  val ready = Input(Bool())
}

class TLBundleD extends Bundle with TLParameters {
  val valid = Input(Bool())
  val opcode = Input(UInt(3.W))
  val param = Input(UInt(2.W))
  val size = Input(UInt(TL_SZW.W))
  val source = Input(UInt(TL_AIW.W))
  val sink = Input(UInt(TL_DIW.W))
  val denied = Input(Bool())
  val data = Input(UInt(TL_DW.W))
  val corrupt = Input(Bool())
  val ready = Output(Bool())
}

class TLBundleE extends Bundle with TLParameters {
  val valid = Output(Bool())
  val sink = Output(Bool())
  val ready = Input(Bool())
}

class TLBundle extends Bundle {
  val a = new TLBundleA
  val b = new TLBundleB
  val c = new TLBundleC
  val d = new TLBundleD
  val e = new TLBundleE
}