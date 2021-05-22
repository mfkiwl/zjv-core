package tile

import chisel3._
import device.MemIO
import tile.common.rf._
import tile.common.control._
import tile.icore._

trait phvntomParams extends config.projectConfig {
}

class CoreIO extends Bundle with phvntomParams {
  val imem = Flipped(new MemIO)
  val dmem = Flipped(new MemIO)
  val immu = Flipped(new MemIO(cachiLine * cachiBlock))
  val dmmu = Flipped(new MemIO(cachiLine * cachiBlock))
  val int = new InterruptIO
  // DIFFTEST regs
  val regs     = Output(Vec(regNum/2, UInt(xlen.W)))
  // CSR DIFF
  val mstatusr = Output(UInt(xlen.W))
  val mipr = Output(UInt(xlen.W))
  val mier = Output(UInt(xlen.W))
  val mcycler = Output(UInt(xlen.W))
  val current_p = Output(UInt(xlen.W))
  val mepcr = Output(UInt(xlen.W))
  val mtvalr = Output(UInt(xlen.W))
  val mcauser = Output(UInt(xlen.W))
  val sstatusr = Output(UInt(xlen.W))
  val sipr = Output(UInt(xlen.W))
  val sier = Output(UInt(xlen.W))
  val sepcr = Output(UInt(xlen.W))
  val stvalr = Output(UInt(xlen.W))
  val scauser = Output(UInt(xlen.W))
  val stvecr = Output(UInt(xlen.W))
  val mtvecr = Output(UInt(xlen.W))
  val midelegr = Output(UInt(xlen.W))
  val medelegr = Output(UInt(xlen.W))
  // Stalls
  val streqs   = Output(Vec(10, UInt(xlen.W)))
  val dtest_pc = Output(UInt(xlen.W))
  val dtest_inst = Output(UInt(xlen.W))
  val dtest_wbvalid = Output(Bool())
  val dtest_int = Output(Bool())
  val dtest_alu = Output(UInt(xlen.W))
  val dtest_mem = Output(UInt(xlen.W))
}

class Core extends Module with phvntomParams {
  val io = IO(new CoreIO)
  val dpath = Module(new DataPath)
  val cpath = Module(new ControlPath)

  dpath.io.ctrl <> cpath.io
  dpath.io.imem <> io.imem
  dpath.io.dmem <> io.dmem
  dpath.io.immu <> io.immu
  dpath.io.dmmu <> io.dmmu
  dpath.io.int  <> io.int

  if (diffTest) {
  // Difftest
    io.mstatusr := dpath.io.mstatusr
    io.mipr := dpath.io.mipr
    io.mier := dpath.io.mier
    io.mcycler := dpath.io.mcycler
    io.current_p := dpath.io.current_p
    io.mepcr := dpath.io.mepcr
    io.mtvalr := dpath.io.mtvalr
    io.mcauser := dpath.io.mcauser
    io.sstatusr := dpath.io.sstatusr
    io.sipr := dpath.io.sipr
    io.sier := dpath.io.sier
    io.sepcr := dpath.io.sepcr
    io.stvalr := dpath.io.stvalr
    io.scauser := dpath.io.scauser
    io.stvecr := dpath.io.stvecr
    io.mtvecr := dpath.io.mtvecr
    io.midelegr := dpath.io.midelegr
    io.medelegr := dpath.io.medelegr
  } else {
    io.mstatusr := 0.U
    io.mipr := 0.U
    io.mier := 0.U
    io.mcycler := 0.U
    io.current_p := 0.U
    io.mepcr := 0.U
    io.mtvalr := 0.U
    io.mcauser := 0.U
    io.sstatusr := 0.U
    io.sipr := 0.U
    io.sier := 0.U
    io.sepcr := 0.U
    io.stvalr := 0.U
    io.scauser := 0.U
    io.stvecr := 0.U
    io.mtvecr := 0.U
    io.midelegr := 0.U
    io.medelegr := 0.U    
  }

  if (diffTest) {
    io.regs := VecInit((0 to regNum/2-1).map(i => dpath.io.regs(i)))
  } else {
    io.regs := VecInit((0 to regNum/2-1).map(i => 0.U))
  }

  if (diffTest) {
    io.streqs := dpath.io.streqs
    io.dtest_pc := dpath.io.dtest_pc
    io.dtest_inst := dpath.io.dtest_inst
    io.dtest_wbvalid := dpath.io.dtest_wbvalid
    io.dtest_int := dpath.io.dtest_int
    io.dtest_alu := dpath.io.dtest_alu
    io.dtest_mem := dpath.io.dtest_mem
  } else {
    io.streqs := VecInit((0 to 9).map(i => 0.U))
    io.dtest_pc := 0.U
    io.dtest_inst := 0.U
    io.dtest_wbvalid := 0.U
    io.dtest_int := 0.U
    io.dtest_alu := 0.U
    io.dtest_mem := 0.U
  }
}