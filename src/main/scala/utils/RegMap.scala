package utils

import chisel3._
import chisel3.util._

object LookupTree {
  def apply[T <: Data](key: UInt, mapping: Iterable[(UInt, T)]): T =
    Mux1H(mapping.map(p => (p._1 === key, p._2)))
}

object MaskData {
  def apply(oldData: UInt, newData: UInt, fullmask: UInt) = {
    (newData & fullmask) | (oldData & ~fullmask)
  }
}

object RegMap {
  def Unwritable = null
  def apply(addr: Int, reg: UInt, wfn: UInt => UInt = (x => x)) =
    (addr, (reg, wfn))
  def generate(
      mapping: Map[Int, (UInt, UInt => UInt)],
      raddr: UInt,
      rdata: UInt,
      waddr: UInt,
      wen: Bool,
      wdata: UInt,
      wmask: UInt
  ): Unit = {
    val chiselMapping = mapping.map { case (a, (r, w)) => (a.U, r, w) }
    rdata := LookupTree(raddr, chiselMapping.map { case (a, r, w) => (a, r) })
    chiselMapping.map {
      case (a, r, w) =>
        if (w != null) when(wen && waddr === a) {
          r := w(MaskData(r, wdata, wmask))
        }
    }
  }
  def generate(
      mapping: Map[Int, (UInt, UInt => UInt)],
      addr: UInt,
      rdata: UInt,
      wen: Bool,
      wdata: UInt,
      wmask: UInt
  ): Unit = generate(mapping, addr, rdata, addr, wen, wdata, wmask)
}
