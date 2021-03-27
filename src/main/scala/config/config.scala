package config
import chisel3.util._

trait RISCVConfig {
  var isa: String = "RV64MI"
  var vm: String = "None"
  var priv: String = "M"
}

trait projectConfig {
  // TODO hot values can be modified in makefile
  var fpga: Boolean = false
  // Pointer Encryption Config
  var enable_pec: Boolean = true
  var pec_enable_ppl: Boolean = false
  var pec_round: Int = 7
  // Cold Values
  var chiplink: Boolean = false
  var ila: Boolean = fpga
  val startAddr = if (fpga || ila) 0x10010000L else 0x80000000L
  var board: String = "None"
  var hasICache: Boolean = false
  var hasDCache: Boolean = false
  // TODO Delete redundant options
  // Basic
  val xlen          = 64
  val flen          = 32
  val bitWidth      = log2Ceil(xlen)
  val regNum        = 64
  val regWidth      = log2Ceil(regNum)
  val diffTest      = !fpga
  val pipeTrace     = true
  val prtHotSpot    = false
  val vscode        = false
  // Mode and VA
  val withCExt      = true     // with C Extension, no Inst-Misaligned Exception
  val only_M        = false
  val validVABits   = 39
  // Cache Config
  val hasL2Cache    = true
  val hasCache      = true
  val cachiLine     = 4
  val cachiBlock    = 64
  // Branch Predictor
  val bpuEntryBits  = 8
  val historyBits   = 4 // TODO >= 4
  val predictorBits = 2 // TODO Do NOT Modify
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
