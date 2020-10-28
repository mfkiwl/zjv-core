package utils

import chisel3._
import chisel3.util._

object MaskExpand {
 def apply(m: UInt) = Cat(m.asBools.map(Fill(8, _)).reverse)
}

object HoldUnless {
  def apply[T <: Data](x: T, en: Bool): T = Mux(en, x, RegEnable(x, 0.U.asTypeOf(x), en))
}