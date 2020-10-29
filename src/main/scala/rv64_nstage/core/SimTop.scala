package rv64_nstage.core

import chisel3._
import chisel3.stage._
import chisel3.util._

object generate {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName

    (new chisel3.stage.ChiselStage).execute(
      Array("-td", "build/verilog/" + packageName, "-X", "mverilog"),
      Seq(ChiselGeneratorAnnotation(() => new Tile))
    )
  }
}
