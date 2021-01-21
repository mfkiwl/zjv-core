package common

import chisel3._
import chisel3.util._

trait RISCVConfig {
  var isa: String = "RV64MI"
  var vm: String = "None"
  var priv: String = "M"
}

trait projectConfig {
  val startAddr = 0x80000000L
  var debug: Boolean = true
  var fpga: Boolean = false
  var chiplink: Boolean = false
  var ila: Boolean = false
  var board: String = "None"
  var hasICache: Boolean = false
  var hasDCache: Boolean = false
  // TODO Delete redundant options
  val xlen          = 64
  val bitWidth      = log2Ceil(xlen)
  val regNum        = 32
  val regWidth      = log2Ceil(regNum)
  val diffTest      = true
  val pipeTrace     = false
  val prtHotSpot    = false
  val vscode        = false
  val rtThread      = true
  val only_M        = false
  val validVABits   = 39
  val hasL2Cache    = true
  val hasCache      = true
  val bpuEntryBits  = 8
  val historyBits   = 4 // TODO >= 4
  val predictorBits = 2 // TODO Do NOT Modify
  val cachiLine     = 4
  val cachiBlock    = 64
  val traceBPU      = false
}

object phvntomConfig extends RISCVConfig with projectConfig {
  def checkISA(set: Char): Boolean = {
    isa.substring(4).contains(set)
  }

  def checkPRIV(mode: Char): Boolean = {
    priv.contains(mode)
  }

  def checkVM(): Int = {
    if (vm == "None") 0
    else vm.substring(3).toInt
  }

  def getXlen(): Int = isa.substring(2,4).toInt
}
