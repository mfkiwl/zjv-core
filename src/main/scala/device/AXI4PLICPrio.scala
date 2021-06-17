/** ************************************************************************************
  * Copyright (c) 2020 Institute of Computing Technology, CAS
  * Copyright (c) 2020 University of Chinese Academy of Sciences
  *
  * NutShell is licensed under Mulan PSL v2.
  * You can use this software according to the terms and conditions of the Mulan PSL v2.
  * You may obtain a copy of Mulan PSL v2 at:
  *             http://license.coscl.org.cn/MulanPSL2
  *
  * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER
  * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR
  * FIT FOR A PARTICULAR PURPOSE.
  *
  * See the Mulan PSL v2 for more details.
  * *************************************************************************************
  */

package device

import bus._
import chisel3._
import chisel3.util._
import utils._

// class PlicIO(val nrIntr: Int, val nrConxt: Int) extends Bundle {
//   val intrVec = Input(UInt(nrIntr.W))
//   val meip = Output(Vec(nrConxt, Bool()))
//   // DIFFTEST
//   val plicip   = Output(Vec(32, Bool()))
//   val plicie   = Output(UInt(32.W))
//   val plicprio = Output(UInt(32.W))
//   val plicthrs = Output(UInt(32.W))
//   val plicclaim = Output(UInt(32.W))
// }

class AXI4PLICPrio(nrIntr: Int = 31, nrConxt: Int = 2, nPriorities: Int = 7)
    extends AXI4LiteSlave(new PlicIO(nrIntr, nrConxt)) {
  require(nrIntr < 1024)
  require(nrConxt <= 15872)
  val addressSpaceSize = 0x4000000
  val addressBits = log2Up(addressSpaceSize)
  def getOffset(addr: UInt) = addr(addressBits - 1, 0)
  val nrIntrWord = (nrIntr + 31) / 32 // roundup

  val pending = List.fill(nrIntrWord)(RegInit(0.U.asTypeOf(Vec(32, Bool()))))
  val pendingMap = pending.zipWithIndex.map {
    case (r, intrWord) =>
      RegMap(0x1000 + intrWord * 4, Cat(r.reverse), RegMap.Unwritable)
  }.toMap

  // 0x0000004 => priority(0) source 1 priority
  // 0x0000008 => priority(1) source 2 priority
  val prioBits = log2Ceil(nPriorities+1)
  val priority = List.fill(nrIntr)(Reg(UInt(32.W)))
  val priorityMap = priority.zipWithIndex.map {
    case (reg, intr) =>
      RegMap((intr + 1) * 4, reg)
  }.toMap

  // 0x0002000 => enable(0)(0) context 0 source  0-31
  // 0x0002004 => enable(0)(1) context 0 source 32-63
  // 0x0002080 => enable(1)(0) context 1 source  0-31
  // 0x0002084 => enable(1)(1) context 1 source 32-63
  val enable = List.fill(nrConxt)(List.fill(nrIntrWord)(RegInit(0.U(32.W))))
  val enableMap = enable.zipWithIndex
    .map {
      case (regs, context) =>
        regs.zipWithIndex.map {
          case (reg, intrWord) =>
            RegMap(0x2000 + context * 0x80 + intrWord * 4, reg)
        }
    }
    .reduce(_ ++ _)
    .toMap

  // 0x0200000 => prio(0) context 0 priority
  // 0x0201000 => prio(1) context 1 priority
  val threshold = List.fill(nrConxt)(RegInit(0.U(32.W)))
  val thresholdMap = threshold.zipWithIndex.map {
    case (reg, context) =>
      RegMap(0x200000 + context * 0x1000, reg)
  }.toMap

  // 0x0200004 => claim(0) context 0 claim/completion
  // 0x0201004 => claim(1) context 1 claim/completion
  val inHandle = RegInit(0.U.asTypeOf(Vec(nrIntr + 1, Bool())))
  def completionFn(wdata: UInt) = {
    inHandle(wdata(31, 0)) := false.B
    0.U
  }

  val claimCompletion = List.fill(nrConxt)(RegInit(0.U(32.W)))
  val claimCompletionMap = claimCompletion.zipWithIndex.map {
    case (reg, context) => {
      val addr = 0x200004 + context * 0x1000
      when(io.in.r.fire() && (getOffset(raddr) === addr.U)) {
        inHandle(reg) := true.B
      }
      RegMap(addr, reg, completionFn)
    }
  }.toMap

  io.extra.get.intrVec.asBools.zipWithIndex.map {
    case (intr, index) => {
      val id = index + 1
      pending(id / 32)(id % 32) := intr
      when(inHandle(id)) {
        pending(id / 32)(id % 32) := false.B
      }
    }
  }

  val pendingVec = Cat(pending.map(x => Cat(x.reverse)))
  claimCompletion.zipWithIndex.map {
    case (reg, context) => {
      val takenVec = pendingVec & Cat(enable(context))
      reg := Mux(takenVec === 0.U, 0.U, PriorityEncoder(takenVec))
    }
  }

  val mapping =
    priorityMap ++ pendingMap ++ enableMap ++ thresholdMap ++ claimCompletionMap

  val rdata = Wire(UInt(32.W))
  RegMap.generate(
    mapping,
    getOffset(raddr),
    rdata,
    getOffset(waddr),
    io.in.w.fire(),
    io.in.w.bits.data,
    MaskExpand(io.in.w.bits.strb >> waddr(2, 0))
  )
  // narrow read
  io.in.r.bits.data := Fill(2, rdata)

  val maxDevs = Reg(Vec(nrConxt, UInt(log2Ceil(nrIntr+1).W)))
  io.extra.get.meip.zipWithIndex.map {
    case (externIntr, context) =>
      val fanin = Module(new PLICFanIn(nrIntr, prioBits))
      fanin.io.prio := priority
      fanin.io.ip   := Cat(enable(context)) & pendingVec
      maxDevs(context) := fanin.io.dev
      externIntr     := RegNext(fanin.io.max) > threshold(context)
  }

  if (diffTest) {
    io.extra.get.plicip := pending(0)
    io.extra.get.plicie := enable(0)(0)
    io.extra.get.plicprio := priority(0)
    io.extra.get.plicthrs := threshold(0)
    io.extra.get.plicclaim := claimCompletion(0)
  } else {
    io.extra.get.plicip := VecInit((0 to 31).map(i => false.B))
    io.extra.get.plicie := 0.U
    io.extra.get.plicprio := 0.U
    io.extra.get.plicthrs := 0.U
    io.extra.get.plicclaim := 0.U
  }
}

// borrowed from Rocket Chip
object MuxT {
  def apply[T <: Data, U <: Data](cond: Bool, con: (T, U), alt: (T, U)): (T, U) =
    (Mux(cond, con._1, alt._1), Mux(cond, con._2, alt._2))

  def apply[T <: Data, U <: Data, W <: Data](cond: Bool, con: (T, U, W), alt: (T, U, W)): (T, U, W) =
    (Mux(cond, con._1, alt._1), Mux(cond, con._2, alt._2), Mux(cond, con._3, alt._3))

  def apply[T <: Data, U <: Data, W <: Data, X <: Data](cond: Bool, con: (T, U, W, X), alt: (T, U, W, X)): (T, U, W, X) =
    (Mux(cond, con._1, alt._1), Mux(cond, con._2, alt._2), Mux(cond, con._3, alt._3), Mux(cond, con._4, alt._4))
}

class PLICFanIn(nDevices: Int, prioBits: Int) extends Module {
  val io = new Bundle {
    val prio = Input(Vec(nDevices, UInt(prioBits.W)))
    val ip   = Input(UInt(nDevices.W))
    val dev  = Output(UInt(log2Ceil(nDevices+1).W))
    val max  = Output(UInt(prioBits.W))
  }

  def findMax(x: Seq[UInt]): (UInt, UInt) = {
    if (x.length > 1) {
      val half = 1 << (log2Ceil(x.length) - 1)
      val left = findMax(x take half)
      val right = findMax(x drop half)
      MuxT(left._1 >= right._1, left, (right._1, half.U | right._2))
    } else (x.head, 0.U)
  }

  val effectivePriority = (1.U(1.W) << prioBits) +: (io.ip.asBools zip io.prio).map { case (p, x) => Cat(p, x) }
  val (maxPri, maxDev) = findMax(effectivePriority)
  io.max := maxPri // strips the always-constant high '1' bit
  io.dev := maxDev
}