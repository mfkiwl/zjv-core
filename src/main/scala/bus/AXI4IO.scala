package bus

import chisel3._
import chisel3.util._

abstract class AXI4BundleBase() extends Bundle

class AXI4BundleA extends AXI4BundleBase with AXI4Parameters {
  val id = Output(UInt(idBits.W))
  val addr = Output(UInt(addrBits.W))
  val len = Output(UInt(lenBits.W)) // number of beats - 1
  val size = Output(UInt(sizeBits.W)) // bytes in beat = 2^size
  val burst = Output(UInt(burstBits.W)) // burst type
  val lock = Output(UInt(lockBits.W)) // lock type
  val cache = Output(UInt(cacheBits.W)) // memory type
  val prot = Output(UInt(protBits.W)) // protection type
  val qos = Output(UInt(qosBits.W)) // 0=no QoS, bigger = higher priority
  //   val region = UInt(width = 4) // optional
  val user = Output(UInt(userBits.W))
  override def toPrintable: Printable = p"addr = 0x${Hexadecimal(addr)}, len = ${len}, size = ${size}"
}

class AXI4BundleAW extends AXI4BundleA
class AXI4BundleAR extends AXI4BundleA

class AXI4BundleW extends AXI4BundleBase with AXI4Parameters {
  val data = Output(UInt(dataBits.W))
  val strb = Output(UInt((dataBits / 8).W))
  val last = Output(Bool())
  val user = Output(UInt(userBits.W))
  override def toPrintable: Printable = p"data = 0x${Hexadecimal(data)}, strb = 0x${Hexadecimal(strb)}, last = ${last}"
}

class AXI4BundleR extends AXI4BundleBase with AXI4Parameters {
  val id = Output(UInt(idBits.W))
  val data = Output(UInt(dataBits.W))
  val resp = Output(UInt(respBits.W))
  val last = Output(Bool())
  val user = Output(UInt(userBits.W))
  override def toPrintable: Printable = p"data = 0x${Hexadecimal(data)}, resp = ${resp}, last = ${last}"
}

class AXI4BundleB extends AXI4BundleBase with AXI4Parameters {
  val id = Output(UInt(idBits.W))
  val resp = Output(UInt(respBits.W))
  val user = Output(UInt(userBits.W))
  override def toPrintable: Printable = p"resp = ${resp}"
}

class AXI4Bundle extends AXI4BundleBase with AXI4Parameters {
  // Decoupled provides ready&valid bit
  val aw = Decoupled(new AXI4BundleAW) // address write
  val w = Decoupled(new AXI4BundleW) // data write
  val b = Flipped(Decoupled(new AXI4BundleB)) // write response
  val ar = Decoupled(new AXI4BundleAR) // address read
  val r = Flipped(Decoupled(new AXI4BundleR)) // data read
}
