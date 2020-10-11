package rv64_nstage.fu

import chisel3._
import chisel3.util._
import rv64_nstage.control.ControlConst._
import rv64_nstage.core.phvntomParams

class AMOArbiterIO extends Bundle with phvntomParams {

}

// TODO use FSM and communicate with D$
class AMOArbiter extends Module with phvntomParams {
  val io = IO(new AMOArbiterIO)
}
