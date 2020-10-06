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
  })
}

class UARTIO extends Bundle with phvntomParams {
  val offset = Input(UInt(xlen.W))
}

class AXI4UART extends AXI4Slave(new UARTIO) with AXI4Parameters {
//   val rx_tx = RegInit(0.U(8.W))
//   val interrupt_enable = RegInit(0.U(8.W))
//   val interrupt_fifo = RegInit(0.U(8.W))
//   val line_control = RegInit(0.U(8.W))
//   val modem_control = RegInit(0.U(8.W))
//   val line_status = RegInit(0.U(8.W))
//   val modem_status = RegInit(0.U(8.W))
//   val scratch_pad = RegInit(0.U(8.W))
  val wen = io.in.w.fire()
  val uart_sim = Module(new SimUART)
  uart_sim.io.clk := clock  
  uart_sim.io.wen := wen
  uart_sim.io.waddr := Cat(Fill(5, 0.U), io.extra.get.offset(2, 0))
  uart_sim.io.wdata := io.in.w.bits.data(7, 0)
  uart_sim.io.ren := ren
  uart_sim.io.raddr := Cat(Fill(5, 0.U), io.extra.get.offset(2, 0))
  val rdata = uart_sim.io.rdata << (io.extra.get.offset(2, 0) << 3)
  io.in.r.bits.data := RegEnable(rdata, ren)

  // when(wen && io.extra.get.offset(2, 0) === 0.U) {
  //   printf("%c", io.in.w.bits.data(7, 0))
  // }

  // printf("In UART: wen = %d, waddr = %d, wdata = %d; ren = %d, raddr = %d, rdata = %d\n", uart_sim.io.wen, io.extra.get.offset(2, 0), uart_sim.io.wdata, uart_sim.io.ren, io.extra.get.offset(2, 0), rdata)
  
//   val mapping = Map(
//     RegMap(0x0, rx_tx, putc),
//     RegMap(0x1, interrupt_enable),
//     RegMap(0x2, interrupt_fifo),
//     RegMap(0x3, line_control),
//     RegMap(0x4, modem_control),
//     RegMap(0x5, line_status),
//     RegMap(0x6, modem_status),
//     RegMap(0x7, scratch_pad)
//   )

//   RegMap.generate(
//     mapping,
//     raddr(2, 0),
//     io.in.r.bits.data,
//     waddr(2, 0),
//     io.in.w.fire(),
//     io.in.w.bits.data,
//     MaskExpand(io.in.w.bits.strb)
//   )
}
