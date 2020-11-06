package bus

import chisel3._
import chisel3.util
import common._
import rv64_3stage.phvntomParams

trait AXI4Parameters extends phvntomParams{
  // These are all fixed by the AXI4 standard:
  val lenBits = 8
  val sizeBits = 3
  val burstBits = 2
  val lockBits = 1
  val cacheBits = 4
  val protBits = 3
  val qosBits = 4
  val respBits = 2

  // These are not fixed:
  val idBits = 1
  val addrBits = 32 // PAddrBits
  val dataBits = xlen // DataBits
  val userBits = 1

  def CACHE_RALLOCATE = 8.U(cacheBits.W)
  def CACHE_WALLOCATE = 4.U(cacheBits.W)
  def CACHE_MODIFIABLE = 2.U(cacheBits.W)
  def CACHE_BUFFERABLE = 1.U(cacheBits.W)

  def PROT_PRIVILEDGED = 1.U(protBits.W)
  def PROT_INSECURE = 2.U(protBits.W)
  def PROT_INSTRUCTION = 4.U(protBits.W)

  def BURST_FIXED = 0.U(burstBits.W)
  def BURST_INCR = 1.U(burstBits.W)
  def BURST_WRAP = 2.U(burstBits.W)

  def RESP_OKAY = 0.U(respBits.W)
  def RESP_EXOKAY = 1.U(respBits.W)
  def RESP_SLVERR = 2.U(respBits.W)
  def RESP_DECERR = 3.U(respBits.W)
}
