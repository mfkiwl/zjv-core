package device

import chisel3._
import chisel3.util._
import rv64_3stage.phvntomParams
import bus._
import utils._

class SimUART extends BlackBox with phvntomParams {
  val io = IO(new Bundle {
    val clk = Input(Clock())    
    val wen = Input(Bool())
    val waddr = Input(UInt(8.W))
    val wdata = Input(UInt(8.W))
    val ren = Input(Bool())
    val raddr = Input(UInt(8.W))
    val rdata = Output(UInt(8.W))
    val irq = Output(Bool())
  })
}

class FPGAUART extends BlackBox with AXI4Parameters {
  val io = IO(new Bundle{
    
  })
}

class UARTIO extends Bundle with phvntomParams {
  // val offset = Input(UInt(xlen.W))
  val irq = Output(Bool())
}

class AXI4UART(name: String = "uart") extends AXI4Slave(new UARTIO, name) with AXI4Parameters {
  val wen = io.in.w.fire()
  val uart_sim = Module(new SimUART)
  uart_sim.io.clk := clock  
  uart_sim.io.wen := wen
  uart_sim.io.waddr := Cat(Fill(5, 0.U), io.in.aw.bits.addr(2, 0))
  uart_sim.io.wdata := io.in.w.bits.data(7, 0)
  uart_sim.io.ren := ren
  uart_sim.io.raddr := Cat(Fill(5, 0.U), io.in.ar.bits.addr(2, 0))
  val rdata = uart_sim.io.rdata << (io.in.ar.bits.addr(2, 0) << 3)
  io.in.r.bits.data := rdata

  io.extra.get.irq := uart_sim.io.irq


//   printf("In UART: wen = %d, waddr = %d, wdata = %d; ren = %d, raddr = %d, rdata = %d\n", uart_sim.io.wen, io.in.aw.bits.addr(2, 0), uart_sim.io.wdata, uart_sim.io.ren, io.in.ar.bits.addr(2, 0), rdata)
}
