package rv64_3stage

import chisel3._
import chisel3.util._
import chisel3.iotesters._

class uncacheTester(c: Uncache) extends PeekPokeTester(c) {
  // define which csrfile, 341 makes up MEPC, we do not care others
  // because others will be decoded before the step
  
}

class uncacheTest extends ChiselFlatSpec {
  "uncacheTest" should "pass" in {
    iotesters.Driver.execute(Array(), () => new Uncache) {
      c => new uncacheTester(c)
    }
  }
}
