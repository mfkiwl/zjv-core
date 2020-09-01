package rv64_3stage

import Common.Util.intToBoolean
import chisel3._
import chisel3.util._
import chisel3.iotesters._

import scala.util.Random


class RegFileTester(c: RegFile) extends PeekPokeTester(c) {

  var reg = new Array[Long](32)

  def reg_sim(rs1_addr: Int, rs2_addr: Int, rd_addr: Int, rd_data: Long, wen: Int): (Long, Long) = {

    var rs1_data = 0L
    var rs2_data = 0L

    if (wen && (rd_addr != 0)) {
//      println(f"Write $rd_data to $rd_addr")
      reg(rd_addr) = rd_data
    }

    if (rs1_addr == 0) {
      rs1_data = 0
    } else {
      rs1_data = reg(rs1_addr)
    }

    if (rs2_addr == 0) {
      rs2_data = 0
    } else {
      rs2_data = reg(rs2_addr)
    }

    (rs1_data, rs2_data)
  }

  for (i <- 0 until 1024) {
    val rs1_addr = Random.nextInt(32)
    val rs2_addr = Random.nextInt(32)
    val rd_addr = Random.nextInt(32)
    val wen = Random.nextInt(2)
    val rd_data = Random.nextLong()

//    println(f">> $rs1_addr $rs2_addr $wen $rd_addr $rd_data")

    val (rs1_data, rs2_data) = reg_sim(rs1_addr, rs2_addr, rd_addr, rd_data, wen)

    poke(c.io.rs1_addr, rs1_addr)
    poke(c.io.rs2_addr, rs2_addr)
    poke(c.io.rd_addr, rd_addr)
    poke(c.io.wen, wen)
    poke(c.io.rd_data, rd_data)

    step(1)

    expect(c.io.rs1_data, longToUnsignedBigInt(rs1_data))
    expect(c.io.rs2_data, longToUnsignedBigInt(rs2_data))
//    print(longToUnsignedBigInt(rs1_data))
  }
}


class RegFileTest extends ChiselFlatSpec {
  "RegFile Test" should "pass" in {
    iotesters.Driver.execute(Array(), () => new RegFile) {
      c => new RegFileTester(c)
    }
  }
}