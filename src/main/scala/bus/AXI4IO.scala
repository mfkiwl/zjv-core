package bus

import chisel3._
import chisel3.util._

abstract class AXI4BundleBase() extends Bundle

class AXI4BundleA extends AXI4BundleBase with AXI4Parameters {
  val id = UInt(idBits.W)
  val addr = UInt(addrBits.W)
  val len = UInt(lenBits.W) // number of beats - 1
  val size = UInt(sizeBits.W) // bytes in beat = 2^size
  val burst = UInt(burstBits.W) // burst type
  val lock = UInt(lockBits.W) // lock type
  val cache = UInt(cacheBits.W) // memory type
  val prot = UInt(protBits.W) // protection type
  val qos = UInt(qosBits.W) // 0=no QoS, bigger = higher priority
  //   val region = UInt(width = 4) // optional
  val user = UInt(userBits.W)
}

class AXI4BundleAW extends AXI4BundleA
class AXI4BundleAR extends AXI4BundleA

class AXI4BundleW extends AXI4BundleBase with AXI4Parameters {
  val data = UInt(dataBits.W)
  val strb = UInt((dataBits / 8).W)
  val last = Bool()
  val user = UInt(userBits.W)
}

class AXI4BundleR extends AXI4BundleBase with AXI4Parameters {
  val id = UInt(idBits.W)
  val data = UInt(dataBits.W)
  val resp = UInt(respBits.W)
  val last = Bool()
  val user = UInt(userBits.W)
}

class AXI4BundleB extends AXI4BundleBase with AXI4Parameters {
  val id = UInt(idBits.W)
  val resp = UInt(respBits.W)
  val user = UInt(userBits.W)
}

class AXI4Bundle extends AXI4BundleBase with AXI4Parameters {
  // Irrevocable provides ready&valid bit
  val aw = Irrevocable(new AXI4BundleAW) // address write
  val w = Irrevocable(new AXI4BundleW) // data write
  val b = Flipped(Irrevocable(new AXI4BundleB)) // write response
  val ar = Irrevocable(new AXI4BundleAR) // address read
  val r = Flipped(Irrevocable(new AXI4BundleR)) // data read
}
