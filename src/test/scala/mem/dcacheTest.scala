package mem

import chisel3._
import chisel3.util._
import chisel3.iotesters._

class dcacheTester(c: DCacheSimple) extends PeekPokeTester(c) {
}

class dcacheTest extends ChiselFlatSpec {
  "uncacheTest" should "pass" in {
    iotesters.Driver.execute(Array(), () => new DCacheSimple()) {
      c => new dcacheTester(c)
    }
  }
}
