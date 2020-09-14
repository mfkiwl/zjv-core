package rv64_3stage

import chisel3._
import chisel3.internal.naming.chiselName
import chisel3.util._
import chisel3.util.experimental.BoringUtils

class RegFileIO extends Bundle with phvntomParams {
  val rs1_addr = Input(UInt(5.W))
  val rs1_data = Output(UInt(xlen.W))
  val rs2_addr = Input(UInt(5.W))
  val rs2_data = Output(UInt(xlen.W))
  val wen      = Input(Bool())
  val rd_addr  = Input(UInt(5.W))
  val rd_data  = Input(UInt(xlen.W))
}

class RegFile extends Module with phvntomParams {
  val io = IO(new RegFileIO)

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(xlen.W))))
  io.rs1_data := Mux(io.rs1_addr.orR, regs(io.rs1_addr), 0.U)
  io.rs2_data := Mux(io.rs2_addr.orR, regs(io.rs2_addr), 0.U)
  when(io.wen & io.rd_addr.orR) {
    regs(io.rd_addr) := io.rd_data
  }
  // BoringUtils.addSource(regs, "uniqueId")
  // for (i <- 0 until 32){
  //   printf("regfile[%d] = %x\n", i.U, regs(i.U))
  // }
  // regs(2):=1.U
}
