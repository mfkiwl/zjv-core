package mem

import chisel3._
import chisel3.util._
import tile._
import config.projectConfig

// On FPGA
// (0x10000000L, 0x02000000L)  // MMIO Out of Tile : SPI UART BRAM
//    (0x10000000L, 0x00001000L) // UART 13bit (because of Vivado's UART IP Design)
//    (0x10010000L, 0x00010000)  // BRAM for bootloader 16bit
//    (0x10020000L, 0x00010000)  // BRAM for official RINUX image 16bit
//    (0X10030000L, 0x00010000)  // SPI 16bit
// (0x02000000L, 0x00010000L)  // CLINT
// (0x0c000000L, 0x04000000L)  // PLIC
// (0x80000000L, undecidedL )  // SDRAM

object AddressSpace extends phvntomParams with projectConfig {
  def mmio =
    if (fpga || ila) {
      List( // (start, size)
        (0x10000000L, 0x02000000L), // MMIO Out of Tile : SPI UART BRAM (UART same as QEMU)
        (0x02000000L, 0x00010000L), // CLINT same as QEMU
        (0x0c000000L, 0x04000000L)  // PLIC same as QEMU
      )
    } else if (chiplink) {
      List( // (start, size)
        (0x40000000L, 0x40000000L), // external devices
        (0x38000000L, 0x00010000L), // CLINT
        (0x3c000000L, 0x04000000L) // PLIC
      )
    } else {
      List( // (start, size)
        (0x40000000L, 0x1000000L), // dummy flash
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
