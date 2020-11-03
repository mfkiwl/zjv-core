package mem

import chisel3._
import chisel3.util._
import rv64_3stage._
import common.projectConfig

object AddressSpace extends phvntomParams with projectConfig{
  def mmio =
    if (chiplink) {
      List( // (start, size)
        (0x40000000L, 0x7fffffffL) // MMIO
      )
    } else if (fpga) {
      List( // (start, size)
        (0x2000000L, 0x10000L), // CLINT
        (0xc000000L, 0x4000000L), // PLIC
        (0x10000000L, 0x100L) // uart
      )
    } else {
      List( // (start, size)
        (0x000100L, 0x10L), // POWEROFFF
        (0x2000000L, 0x10000L), // CLINT
        (0xc000000L, 0x4000000L), // PLIC
        (0x10000000L, 0x100L) // uart
      )
    }

  def isMMIO(addr: UInt) =
    mmio
      .map(range => addr >= range._1.U && addr < (range._1 + range._2).U)
      .reduce(_ || _)
}
