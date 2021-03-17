package qarma64

import chisel3._
import chisel3.util._

class QarmaEngine(ppl: Boolean, max_round: Int = 7) extends QarmaParamsIO {

  val engine = if (ppl) Module(new QarmaEnginePP(max_round = max_round)) else
    Module(new QarmaEngineOneStage(max_round = max_round))

  engine.input.valid := input.valid
  input.ready := engine.input.ready
  engine.input.bits := input.bits
  engine.output.ready := output.ready
  output.valid := engine.output.valid
  output.bits := engine.output.bits
}
