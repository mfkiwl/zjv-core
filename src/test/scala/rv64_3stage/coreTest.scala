package rv64_3stage

import chisel3._
import chisel3.util._
import chisel3.iotesters._

import java.nio.{IntBuffer, ByteOrder}
import java.io.FileInputStream
import java.nio.channels.FileChannel

class SimMem(val starAddr: Long) {
  private val memSize = 128 * 1024 * 1024
  private var mem: Array[Int] = Array()

  def init() = {
    mem = Array(
      0x07b08093,   // addi x1,x1,123
      0xf8508093,   // addi x1,x1,-123
      0x0000806b,   // trap x1
      0, 0, 0, 0
    )
  }

  def isDouble(mask: Int): Boolean = {
    if (mask == 4)
      true
    else
      false
  }

  def isSigned(mask: Int): Boolean = {
    if (mask >= 1 && mask <= 4)
      true
    else
      false
  }

  def getMask(sizeEncode: Int): Long = {
    sizeEncode match {
      case 1 => 0xff                  /*byte*/
      case 2 => 0xffff                /*half*/
      case 3 => 0xffffffff            /*word*/
      case 4 => 0xffffffffffffffffL   /*double*/
      case 5 => 0xff                  /*unsigned byte*/
      case 6 => 0xffff                /*unsigned half*/
      case 7 => 0xffffffff            /*unsigned word*/
      case _ => 0xffffffffffffffffL
    }
  }

  def read(addr: Long, wtype: Int): Long = {
    val index = (addr-starAddr).toInt
    val idx = index >> 2
    val offset = index & 0x3

    require(idx < mem.size)

    var rdata = 0L
    if (isDouble(wtype)) {
      rdata = (mem(idx) << 32) | mem(idx+1)
    }
    else {
      val data = mem(idx)
      rdata = (data >> (offset*8)) & getMask(wtype)
    }

    //println(f"rdataAlign = 0x$rdataAlign%08x")

    if (isSigned(wtype)) {
      // TODO
    }
    rdata
  }

  def write(addr: Long, wtype: Int, wdata: Long) = {
    val index = (addr-starAddr).toInt
    val idx = index >> 2
    val offset = index & 0x3

    require(idx < mem.size)

    if (isDouble(wtype)) {
      val high:Int = (wdata >> 32).toInt
      val low:Int = (wdata).toInt

      mem(idx) = low
      mem(idx+1) = high
    }
    else {

    }
    val oldData = mem(idx)
    val dataMask = getMask(wtype) << (offset * 8)
    val newData = (oldData & ~dataMask) | (wdata << (offset * 8) & dataMask)
    mem(idx) = newData.toInt
    //println(f"wdata = 0x$wdata%08x, realWdata = 0x$newData%08x")
  }
}

class CoreTester(core: Core) extends PeekPokeTester(core) {
  var pc = 0
  var trap = 0
  var instr = 0

  val mem = new SimMem(0x80000000)
  mem.init()

  do {
    pc = peek(core.io.imem.req.bits.addr).toLong
    poke(core.io.imem.resp.valid, peek(core.io.imem.req.valid))
    instr = mem.read(pc, peek(core.io.imem.req.bits.memtype).toLong)
    poke(core.io.imem.resp.bits.data, instr)

    poke(core.io.dmem.resp.valid, peek(core.io.dmem.req.valid))
    if (peek(core.io.dmem.req.valid).toInt == 1) {
      val dmemAddr = peek(core.io.dmem.req.bits.addr).toLong
      val size = peek(core.io.dmem.req.bits.memtype).toInt

      if (peek(core.io.dmem.req.bits.wen) == 1) {
        val wdata = peek(core.io.dmem.req.bits.data).toLong
        println(f" >> Write $wdata%x\n")
        mem.write(dmemAddr, size, wdata)
      } else {
        val rdata = mem.read(dmemAddr, size)
        println(f" >> Read $rdata%x\n")
        poke(core.io.dmem.resp.bits.data, rdata)
      }
    }

    step(1)
  } while (instr != 0)

}


class CoreTest extends ChiselFlatSpec {
  "Core Test" should "pass" in {
    iotesters.Driver.execute(Array(), () => new Core) {
      c => new CoreTester(c)
    }
  }
}