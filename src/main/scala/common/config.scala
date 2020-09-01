package common

import chisel3._
import chisel3.util
import jdk.internal.module.IllegalAccessLogger.Mode

trait RISCVConfig {
  var isa: String = "RV64MI"
  var vm: String = "None"
  var priv: String = "M"
}

trait projectConfig {
  var debug: Boolean = false
  var fpga: Boolean = false
  var board: String = "None"
  var hasICache: Boolean = false
  var hasDCache: Boolean = false
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

  def xlen(): Int = isa.substring(2,4).toInt
}
