package qarma64

import chisel3.stage._

object Elaborate {
  val pipeline: Boolean = true

  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName

    if (args.isEmpty)
      (new chisel3.stage.ChiselStage).execute(
        Array("-td", "build/verilog/" + packageName, "-X", "verilog"),
        Seq(ChiselGeneratorAnnotation(() => new QarmaEngine(ppl = pipeline))))
    else
      (new chisel3.stage.ChiselStage).execute(args,
        Seq(ChiselGeneratorAnnotation(() => new QarmaEngine(ppl = pipeline))))
  }
}
