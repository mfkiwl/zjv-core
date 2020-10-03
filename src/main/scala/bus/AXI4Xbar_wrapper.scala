// package bus

// import chisel3._
// import chisel3.util._
// import chisel3.experimental._

// class AXI4XbarWrapper(n: Int, addressSpace: List[(Long, Long)]) extends Module with AXI4Parameters {
//   val io = IO(new Bundle {
//     val in = Flipped(Vec(n, new AXI4Bundle))
//     val out = Vec(addressSpace.length, new AXI4Bundle)
//   })

//   // val inXbar = Module(new SimpleBusCrossbarNto1(n))
//   // val outXbar = Module(new SimpleBusCrossbar1toN(addressSpace))

//   // inXbar.io.in <> io.in
//   // outXbar.io.in <> inXbar.io.out
//   // io.out <> outXbar.io.out
//   var params = Map.empty[String, Param]
//   params = params + ("C_AXI_DATA_WIDTH" -> xlen, "C_AXI_ADDR_WIDTH" -> xlen, "C_AXI_ID_WIDTH" -> idBits)
//   params = params + ("NM" -> n, "NS" -> addressSpace.length)
//   var addresses = ""
//   var masks = ""
//   // params = params + ()
//   for (i <- 0 until addressSpace.length) {
//     addresses = addresses + addressSpace(i)._1
//     masks = masks + addressSpace(i)._2
//   }
//   params = params + ("SLAVE_ADDR" -> addresses, "SLAVE_MASK" -> masks)
//   printf(p"params = ${params}\n")

//   val xbar_real = Module(new axixbar(params))
// }

// class AXI4XbarRealIO extends Bundle {}

// class axixbar(params: Map[String, Param] = Map.empty[String, Param])
//     extends BlackBox(params) {
//   val io = IO(new AXI4XbarRealIO)
// }
