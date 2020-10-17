package rv64_nstage.register

import chisel3._
import chisel3.util._
import rv64_nstage.control.ControlConst._
import rv64_nstage.control._
import rv64_nstage.core.phvntomParams

class StageRegIO extends Bundle with phvntomParams {
  // Stall Signal
  val stall = Input(Bool())
  // Interrupt or Misprediction Flush
  val flush_one = Input(Bool())
  // Info After Last Stage
  val last_stage_atomic_stall_req = Input(Bool())
  val next_stage_atomic_stall_req = Input(Bool())
  // TODO *** CAUTION ***
  // TODO THIS SIGNAL IS PURELY FOR DIFFTEST
  // TODO BECAUSE ERET / TRAP / BRANCH DOES NOT MODIFY MEM OR REGS
  val next_stage_flush_req = Input(Bool())
  val bubble_in = Input(Bool())
  val pc_in = Input(UInt(xlen.W))
  val inst_af_in = Input(Bool())
  // Output
  val bubble_out = Output(Bool())
  val pc_out = Output(UInt(xlen.W))
  val inst_af_out = Output(Bool())
}

class RegIf1If2IO extends StageRegIO with phvntomParams {
  // TODO Maybe something to add, but I don't think so.
}

class RegIf1If2 extends Module with phvntomParams {
  val io = IO(new RegIf1If2IO)

  val bubble = RegInit(Bool(), true.B)
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.stall || io.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.stall) {
    when((last_delay && delay_flush) || io.bubble_in || io.flush_one) {
      pc := 0.U
      bubble := true.B
      inst_af := false.B
    }.otherwise {
      pc := io.pc_in
      bubble := false.B
      inst_af := io.inst_af_in
    }
  }.elsewhen(!io.next_stage_atomic_stall_req && io.flush_one && !io.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst_af := false.B
  }

  io.bubble_out := bubble
  io.pc_out := pc
  io.inst_af_out := inst_af
}

class RegIf2IdIO extends RegIf1If2IO with phvntomParams {
  val inst_in = Input(UInt(32.W)) // TODO only supports 32-bit inst now
  val inst_out = Output(UInt(32.W)) // TODO only supports 32-bit inst now
}

class RegIf2Id extends Module with phvntomParams {
  val io = IO(new RegIf2IdIO)
  
  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), 0.U) // TODO only supports 32-bit inst now
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.stall || io.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.stall) {
    when((last_delay && delay_flush) || io.bubble_in || io.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
    }.otherwise {
      pc := io.pc_in
      bubble := false.B
      inst := io.inst_in
      inst_af := io.inst_af_in
    }
  }.elsewhen(!io.next_stage_atomic_stall_req && io.flush_one && !io.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
  }

  io.bubble_out := bubble
  io.pc_out := pc
  io.inst_out := inst
  io.inst_af_out := inst_af
}

class RegIdExeIO extends RegIf2IdIO with phvntomParams {
  val inst_info_in = Flipped(new InstInfo)
  val inst_info_out = Flipped(Flipped(new InstInfo))
}

class RegIdExe extends Module with phvntomParams {
  val io = IO(new RegIdExeIO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), 0.U) // TODO only supports 32-bit inst now
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX)
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits).W),
    default_inst_info)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.stall || io.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.stall) {
    when((last_delay && delay_flush) || io.bubble_in || io.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
      inst_info := default_inst_info
    }.otherwise {
      pc := io.pc_in
      bubble := false.B
      inst := io.inst_in
      inst_af := io.inst_af_in
      inst_info := io.inst_info_in.asUInt
    }
  }.elsewhen(!io.next_stage_atomic_stall_req && io.flush_one && !io.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
    inst_info := default_inst_info
  }

  io.bubble_out := bubble
  io.pc_out := pc
  io.inst_out := inst
  io.inst_af_out := inst_af
  io.inst_info_out := inst_info.asTypeOf(new InstInfo)
}

class RegExeMem1IO extends RegIdExeIO with phvntomParams {
  val alu_val_in = Input(UInt(xlen.W))
  val inst_addr_misaligned_in = Input(Bool())
  val mem_wdata_in = Input(UInt(xlen.W))
  val external_int_in = Input(Bool())
  val software_int_in = Input(Bool())
  val timer_int_in = Input(Bool())
  val alu_val_out = Output(UInt(xlen.W))
  val inst_addr_misaligned_out = Output(Bool())
  val mem_wdata_out = Output(UInt(xlen.W))
  val external_int_out = Output(Bool())
  val software_int_out = Output(Bool())
  val timer_int_out = Output(Bool())
}

class RegExeMem1 extends Module with phvntomParams {
  val io = IO(new RegExeMem1IO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), 0.U) // TODO only supports 32-bit inst now
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX)
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits).W),
    default_inst_info)
  val alu_val = RegInit(UInt(xlen.W), 0.U)
  val inst_addr_misaligned = RegInit(Bool(), false.B)
  val mem_wdata = RegInit(UInt(xlen.W), 0.U)
  val soft_int = RegInit(Bool(), false.B)
  val extern_int = RegInit(Bool(), false.B)
  val timer_int = RegInit(Bool(), false.B)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.stall || io.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.stall) {
    when((last_delay && delay_flush) || io.bubble_in || io.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
      inst_info := default_inst_info
      alu_val := 0.U
      inst_addr_misaligned := false.B
      mem_wdata := 0.U
      soft_int := false.B
      extern_int := false.B
      timer_int := false.B
    }.otherwise {
      pc := io.pc_in
      bubble := false.B
      inst := io.inst_in
      inst_af := io.inst_af_in
      inst_info := io.inst_info_in.asUInt
      alu_val := io.alu_val_in
      inst_addr_misaligned := io.inst_addr_misaligned_in
      mem_wdata := io.mem_wdata_in
      soft_int := io.software_int_in
      extern_int := io.external_int_in
      timer_int := io.timer_int_in
    }
  }.elsewhen(!io.next_stage_atomic_stall_req && io.flush_one && !io.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
    inst_info := default_inst_info
    alu_val := 0.U
    inst_addr_misaligned := false.B
    mem_wdata := 0.U
    soft_int := false.B
    extern_int := false.B
    timer_int := false.B
  }.otherwise {
    soft_int := false.B
    extern_int := false.B
    timer_int := false.B
  }

  io.bubble_out := bubble
  io.pc_out := pc
  io.inst_out := inst
  io.inst_af_out := inst_af
  io.inst_info_out := inst_info.asTypeOf(new InstInfo)
  io.alu_val_out := alu_val
  io.inst_addr_misaligned_out := inst_addr_misaligned
  io.mem_wdata_out := mem_wdata
  io.external_int_out := extern_int
  io.software_int_out := soft_int
  io.timer_int_out := timer_int
}

class RegMem1Mem2IO extends RegExeMem1IO with phvntomParams {
  val csr_val_in = Input(UInt(xlen.W))
  val expt_in = Input(Bool())
  val int_resp_in = Input(Bool())
  val csr_val_out = Output(UInt(xlen.W))
  val expt_out = Output(Bool())
  val int_resp_out = Output(Bool())
}

class RegMem1Mem2 extends Module with phvntomParams {
  val io = IO(new RegMem1Mem2IO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), 0.U) // TODO only supports 32-bit inst now
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX)
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits).W),
    default_inst_info)
  val alu_val = RegInit(UInt(xlen.W), 0.U)
  val inst_addr_misaligned = RegInit(Bool(), false.B)
  val mem_wdata = RegInit(UInt(xlen.W), 0.U)
  val soft_int = RegInit(Bool(), false.B)
  val extern_int = RegInit(Bool(), false.B)
  val timer_int = RegInit(Bool(), false.B)
  val csr_val = RegInit(UInt(xlen.W), 0.U)
  val expt = RegInit(Bool(), false.B)
  val interrupt = RegInit(Bool(), false.B)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.stall || io.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.stall) {
    when((last_delay && delay_flush) || io.bubble_in || io.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
      inst_info := default_inst_info
      alu_val := 0.U
      inst_addr_misaligned := false.B
      mem_wdata := 0.U
      soft_int := false.B
      extern_int := false.B
      timer_int := false.B
      csr_val := 0.U
      expt := false.B
      interrupt := false.B
    }.otherwise {
      pc := io.pc_in
      bubble := false.B
      inst := io.inst_in
      inst_af := io.inst_af_in
      inst_info := io.inst_info_in.asUInt
      alu_val := io.alu_val_in
      inst_addr_misaligned := io.inst_addr_misaligned_in
      mem_wdata := io.mem_wdata_in
      soft_int := io.software_int_in
      extern_int := io.external_int_in
      timer_int := io.timer_int_in
      csr_val := io.csr_val_in
      expt := io.expt_in
      interrupt := io.int_resp_in
    }
  }.elsewhen(!io.next_stage_atomic_stall_req && io.flush_one && !io.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
    inst_info := default_inst_info
    alu_val := 0.U
    inst_addr_misaligned := false.B
    mem_wdata := 0.U
    soft_int := false.B
    extern_int := false.B
    timer_int := false.B
    csr_val := 0.U
    expt := false.B
    interrupt := false.B
  }

  io.bubble_out := bubble
  io.pc_out := pc
  io.inst_out := inst
  io.inst_af_out := inst_af
  io.inst_info_out := inst_info.asTypeOf(new InstInfo)
  io.alu_val_out := alu_val
  io.inst_addr_misaligned_out := inst_addr_misaligned
  io.mem_wdata_out := mem_wdata
  io.external_int_out := extern_int
  io.software_int_out := soft_int
  io.timer_int_out := timer_int
  io.csr_val_out := csr_val
  io.expt_out := expt
  io.int_resp_out := interrupt
}

class RegMem2WbIO extends RegMem1Mem2IO with phvntomParams {
  val mem_val_in = Input(UInt(xlen.W))
  val mem_val_out = Output(UInt(xlen.W))
}

class RegMem2Wb extends Module with phvntomParams {
  val io = IO(new RegMem2WbIO)

  val bubble = RegInit(Bool(), true.B)
  val inst = RegInit(UInt(32.W), 0.U) // TODO only supports 32-bit inst now
  val pc = RegInit(UInt(xlen.W), 0.U)
  val inst_af = RegInit(Bool(), false.B)
  val default_inst_info = Cat(instXXX, pcPlus4, false.B, brXXX, AXXX, BXXX, aluXXX, memXXX, wbXXX, wenXXX)
  val inst_info = RegInit(UInt((instBits + pcSelectBits +
    1 + brBits + ASelectBits + BSelectBits +
    aluBits + memBits + wbBits + wenBits + amoBits).W),
    default_inst_info)
  val alu_val = RegInit(UInt(xlen.W), 0.U)
  val inst_addr_misaligned = RegInit(Bool(), false.B)
  val mem_wdata = RegInit(UInt(xlen.W), 0.U)
  val soft_int = RegInit(Bool(), false.B)
  val extern_int = RegInit(Bool(), false.B)
  val timer_int = RegInit(Bool(), false.B)
  val csr_val = RegInit(UInt(xlen.W), 0.U)
  val expt = RegInit(Bool(), false.B)
  val interrupt = RegInit(Bool(), false.B)
  val mem_val = RegInit(UInt(xlen.W), 0.U)

  val delay_flush = RegInit(Bool(), false.B)
  val last_delay = RegInit(Bool(), false.B)
  val this_stall = io.stall || io.last_stage_atomic_stall_req

  last_delay := this_stall

  when(io.flush_one) {
    delay_flush := true.B
  }.elsewhen(!last_delay && this_stall) {
    delay_flush := false.B
  }

  when(!io.stall) {
    when((last_delay && delay_flush) || io.bubble_in || io.flush_one) {
      pc := 0.U
      bubble := true.B
      inst := BUBBLE
      inst_af := false.B
      inst_info := default_inst_info
      alu_val := 0.U
      inst_addr_misaligned := false.B
      mem_wdata := 0.U
      soft_int := false.B
      extern_int := false.B
      timer_int := false.B
      csr_val := 0.U
      expt := false.B
      interrupt := false.B
      mem_val := 0.U
    }.otherwise {
      pc := io.pc_in
      bubble := false.B
      inst := io.inst_in
      inst_af := io.inst_af_in
      inst_info := io.inst_info_in.asUInt
      alu_val := io.alu_val_in
      inst_addr_misaligned := io.inst_addr_misaligned_in
      mem_wdata := io.mem_wdata_in
      soft_int := io.software_int_in
      extern_int := io.external_int_in
      timer_int := io.timer_int_in
      csr_val := io.csr_val_in
      expt := io.expt_in
      interrupt := io.int_resp_in
      mem_val := io.mem_val_in
    }
  }.elsewhen(!io.next_stage_atomic_stall_req && io.flush_one && !io.next_stage_flush_req) {
    pc := 0.U
    bubble := true.B
    inst := BUBBLE
    inst_af := false.B
    inst_info := default_inst_info
    alu_val := 0.U
    inst_addr_misaligned := false.B
    mem_wdata := 0.U
    soft_int := false.B
    extern_int := false.B
    timer_int := false.B
    csr_val := 0.U
    expt := false.B
    interrupt := false.B
    mem_val := 0.U
  }

  io.bubble_out := bubble
  io.pc_out := pc
  io.inst_out := inst
  io.inst_af_out := inst_af
  io.inst_info_out := inst_info.asTypeOf(new InstInfo)
  io.alu_val_out := alu_val
  io.inst_addr_misaligned_out := inst_addr_misaligned
  io.mem_wdata_out := mem_wdata
  io.external_int_out := extern_int
  io.software_int_out := soft_int
  io.timer_int_out := timer_int
  io.csr_val_out := csr_val
  io.expt_out := expt
  io.int_resp_out := interrupt
  io.mem_val_out := mem_val
}