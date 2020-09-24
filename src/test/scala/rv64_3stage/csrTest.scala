package rv64_3stage

import chisel3._
import chisel3.util._
import chisel3.iotesters._

class CSRTester(c: CSR) extends PeekPokeTester(c) {
  // define which csrfile, 341 makes up MEPC, we do not care others
  // because others will be decoded before the step
  poke(c.io.inst, 0x34100000)
  // should be 0
  expect(c.io.out, 0)
  poke(c.io.stall, 0)
  poke(c.io.cmd, CSR.S)
  poke(c.io.in, 0x1234abcd)
  poke(c.io.tim_int, 0)
  poke(c.io.soft_int, 0)
  poke(c.io.external_int, 0)
  step(1)
  // shoud be 0x1234abcd
  expect(c.io.out, 0x1234abcd)
  poke(c.io.stall, 0)
  poke(c.io.cmd, CSR.C)
  poke(c.io.in, 0xd)
  step(1)
  // should be 0x1234abc0
  expect(c.io.out, 0x1234abc0)
  println("Finished")
}

class CSRTest extends ChiselFlatSpec {
  "CSRTest" should "pass" in {
    iotesters.Driver.execute(Array(), () => new CSR) {
      c => new CSRTester(c)
    }
  }
}
