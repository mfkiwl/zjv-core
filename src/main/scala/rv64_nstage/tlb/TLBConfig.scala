package rv64_nstage.tlb

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams
import utils._

class TLBConfig extends phvntomParams {
  val Level = 3
  val offLen  = 12
  val ppn0Len = 9
  val ppn1Len = 9
  val ppn2Len = 64 - offLen - ppn0Len - ppn1Len // 2
  val ppnLen = ppn2Len + ppn1Len + ppn0Len
  val vpn2Len = 9
  val vpn1Len = 9
  val vpn0Len = 9
  val vpnLen = vpn2Len + vpn1Len + vpn0Len

  //val paddrLen = PAddrBits
  //val vaddrLen = VAddrBits
  val satpLen = xlen
  val satpModeLen = 4
  val asidLen = 16
  val flagLen = 8

  val ptEntryLen = xlen
  val satpResLen = xlen - ppnLen - satpModeLen - asidLen
  //val vaResLen = 25 // unused
  //val paResLen = 25 // unused
  val pteResLen = xlen - ppnLen - 2 - flagLen

  def vaBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
    val off  = UInt( offLen.W)
  }

  def vaBundle2 = new Bundle {
    val vpn  = UInt(vpnLen.W)
    val off  = UInt(offLen.W)
  }

  def vaBundle3 = new Bundle {
    val vpn = UInt(vpnLen.W)
    val off = UInt(offLen.W)
  }

  def vpnBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
  }

  def paBundle = new Bundle {
    val ppn2 = UInt(ppn2Len.W)
    val ppn1 = UInt(ppn1Len.W)
    val ppn0 = UInt(ppn0Len.W)
    val off  = UInt( offLen.W)
  }

  def paBundle2 = new Bundle {
    val ppn  = UInt(ppnLen.W)
    val off  = UInt(offLen.W)
  }

  def paddrApply(ppn: UInt, vpnn: UInt):UInt = {
    Cat(Cat(ppn, vpnn), 0.U(3.W))
  }

  def pteBundle = new Bundle {
    val reserved  = UInt(pteResLen.W)
    val ppn  = UInt(ppnLen.W)
    val rsw  = UInt(2.W)
    val flag = new Bundle {
      val d    = UInt(1.W)
      val a    = UInt(1.W)
      val g    = UInt(1.W)
      val u    = UInt(1.W)
      val x    = UInt(1.W)
      val w    = UInt(1.W)
      val r    = UInt(1.W)
      val v    = UInt(1.W)
    }
  }

  def satpBundle = new Bundle {
    val mode = UInt(satpModeLen.W)
    val asid = UInt(asidLen.W)
    val res = UInt(satpResLen.W)
    val ppn  = UInt(ppnLen.W)
  }

  def flagBundle = new Bundle {
    val d    = Bool()//UInt(1.W)
    val a    = Bool()//UInt(1.W)
    val g    = Bool()//UInt(1.W)
    val u    = Bool()//UInt(1.W)
    val x    = Bool()//UInt(1.W)
    val w    = Bool()//UInt(1.W)
    val r    = Bool()//UInt(1.W)
    val v    = Bool()//UInt(1.W)
  }

  def maskPaddr(ppn:UInt, vaddr:UInt, mask:UInt) = {
    MaskData(vaddr, Cat(ppn, 0.U(offLen.W)), Cat(Fill(ppn2Len, 1.U(1.W)), mask, 0.U(offLen.W)))
  }

  def MaskEQ(mask: UInt, pattern: UInt, vpn: UInt) = {
    (Cat("h1ff".U(vpn2Len.W), mask) & pattern) === (Cat("h1ff".U(vpn2Len.W), mask) & vpn)
  }

}
