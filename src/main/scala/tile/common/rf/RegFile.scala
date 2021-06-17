package tile.common.rf

import chisel3._
import chisel3.util.experimental._
import tile.phvntomParams

class RegFileIO extends Bundle with phvntomParams {
  val rs1_addr = Input(UInt(regWidth.W))
  val rs1_data = Output(UInt(xlen.W))
  val rs2_addr = Input(UInt(regWidth.W))
  val rs2_data = Output(UInt(xlen.W))
  val wen      = Input(Bool())
  val rd_addr  = Input(UInt(regWidth.W))
  val rd_data  = Input(UInt(xlen.W))
}

class RegFile extends Module with phvntomParams {
  val io = IO(new RegFileIO)

  val regs = RegInit(VecInit(Seq.fill(regNum)(0.U(xlen.W))))
  io.rs1_data := Mux(io.rs1_addr.orR, regs(io.rs1_addr), 0.U)
  io.rs2_data := Mux(io.rs2_addr.orR, regs(io.rs2_addr), 0.U)
  when(io.wen & io.rd_addr.orR) {
    regs(io.rd_addr) := io.rd_data
  }

  if (diffTest) {
    BoringUtils.addSource(VecInit((0 to regNum/2-1).map(i => regs(i))), "difftestRegs")
  }
}
