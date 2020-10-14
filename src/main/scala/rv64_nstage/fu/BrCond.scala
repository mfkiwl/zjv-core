package rv64_nstage.fu

import chisel3._
import rv64_nstage.core.phvntomParams
import rv64_nstage.control.ControlConst._

class BrCondIO extends Bundle with phvntomParams {
  val rs1 = Input(UInt(xlen.W))
  val rs2 = Input(UInt(xlen.W))
  val brType = Input(UInt(brBits.W))
  val branch = Output(Bool())
}

class BrCond extends Module with phvntomParams {
  val io = IO(new BrCondIO)

  val eq = io.rs1 === io.rs2
  val neq = !eq
  val lt = io.rs1.asSInt < io.rs2.asSInt
  val ge = !lt
  val ltu = io.rs1 < io.rs2
  val geu = !ltu
  io.branch :=
    ((io.brType === beqType) && eq) ||
      ((io.brType === bneType) && neq) ||
      ((io.brType === bltType) && lt) ||
      ((io.brType === bgeType) && ge) ||
      ((io.brType === bltuType) && ltu) ||
      ((io.brType === bgeuType) && geu)
}
