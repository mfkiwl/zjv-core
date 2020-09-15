package rv64_3stage

import chisel3._
import chisel3.util._
import common._


class TopIO extends Bundle with phvntomParams {
  // Difftest
}


class Top extends Module with phvntomParams {
  val io = IO(new TopIO)

  val tile = Module(new Tile)
}

object elaborate {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      chisel3.Driver.execute(Array("--target-dir", "build/verilog/rv64_3stage"), () => new Top)
    else
      chisel3.Driver.execute(args, () => new Top)
  }
}