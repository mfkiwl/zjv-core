package rv64_nstage.control

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams

// TODO only support 'STALL' without 'FORWARDING'

class ALUSchedulerIO extends Bundle with phvntomParams {
  val is_bubble = Input(Bool())
  val rs1_used_exe = Input(Bool())
  val rs1_addr_exe = Input(UInt(regWidth.W))
  val rs2_used_exe = Input(Bool())
  val rs2_addr_exe = Input(UInt(regWidth.W))
  val rd_used_mem1 = Input(Bool())
  val rd_addr_mem1 = Input(UInt(regWidth.W))
  val rd_used_mem2 = Input(Bool())
  val rd_addr_mem2 = Input(UInt(regWidth.W))
  val rd_used_wb = Input(Bool())
  val rd_addr_wb = Input(UInt(regWidth.W))
  val rs1_from_reg = Input(UInt(xlen.W))
  val rs2_from_reg = Input(UInt(xlen.W))
  val rd_from_mem1 = Input(UInt(xlen.W))
  val rd_from_mem2 = Input(UInt(xlen.W))
  val rd_from_wb = Input(UInt(xlen.W))
  val stall_req = Output(Bool())
  val rs1_val = Output(UInt(xlen.W))
  val rs2_val = Output(UInt(xlen.W))
}

// TODO Here the stall and forwarding may be too bad
// TODO If this is really that bad, I will try reg_used_table instead
// TODO For example, table[x0] = (mem1, wb) means x0 is in mem1 and it can be forwarded in WB
// TODO Of course, we can guess that there is a LOAD something to [x0] instruction in mem1
// TODO If even this doesn't work, I may try a inst-info-FIFO between Decoder and ALU
class ALUScheduler extends Module with phvntomParams {
  val io = IO(new ALUSchedulerIO)

  // MEM1 = 1; MEM2 = 2; WB = 3; NONE = 0
  val rs1_fwd_stage = Wire(UInt(2.W))
  val rs2_fwd_stage = Wire(UInt(2.W))
  val rs1_hazard = io.rs1_used_exe && ((io.rs1_addr_exe === io.rd_addr_mem1 && io.rd_used_mem1) ||
    (io.rs1_addr_exe === io.rd_addr_mem2 && io.rd_used_mem2))
  rs1_fwd_stage := Mux(io.rs1_used_exe && io.rs1_addr_exe === io.rd_addr_wb && io.rd_used_wb, 3.U, 0.U)
  val rs1_wb_fwd_val = io.rd_from_wb
  val rs2_hazard = io.rs2_used_exe && ((io.rs2_addr_exe === io.rd_addr_mem1 && io.rd_used_mem1) ||
    (io.rs2_addr_exe === io.rd_addr_mem2 && io.rd_used_mem2))
  rs2_fwd_stage := Mux(io.rs2_used_exe && io.rs2_addr_exe === io.rd_addr_wb && io.rd_used_wb, 3.U, 0.U)
  val rs2_wb_fwd_val = io.rd_from_wb
printf("fwd %x, stall_req %x, rs1fs %x, rs2fx %x\n", rs1_fwd_stage.orR || rs2_fwd_stage.orR, io.stall_req, rs1_fwd_stage, rs2_fwd_stage)
  io.rs1_val := MuxLookup(rs1_fwd_stage, io.rs1_from_reg,
    Seq(
      1.U -> 0.U,
      2.U -> 0.U,
      3.U -> rs1_wb_fwd_val
    )
  )
  io.rs2_val := MuxLookup(rs2_fwd_stage, io.rs2_from_reg,
    Seq(
      1.U -> 0.U,
      2.U -> 0.U,
      3.U -> rs2_wb_fwd_val
    )
  )

  io.stall_req := (rs1_hazard || rs2_hazard) && !io.is_bubble
}
