package mem

import chisel3._
import chisel3.util._
import rv64_3stage._

object AddressSpace extends phvntomParams {
  def mmio =
    List( // (start, size)
      (0x000100L, 0x10L), // POWEROFFF
      (0x2000000L, 0x10000L), // CLINT
      // (0xc000000L, 0x4000000L)  // PLIC
      (0x10000000L, 0x100L) // uart
    )

  def isMMIO(addr: UInt) =
    mmio
      .map(range => addr >= range._1.U && addr < (range._1 + range._2).U)
      .reduce(_ || _)
}
