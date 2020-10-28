/**************************************************************************************
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
 ***************************************************************************************/

package device

import bus._
import chisel3._
import chisel3.util._
import utils._

import chisel3.util.experimental.BoringUtils

class PlicIO(val nrIntr: Int, val nrConxt: Int) extends Bundle {
  val intrVec = Input(UInt(nrIntr.W))
  val meip = Output(Vec(nrConxt, Bool()))
}

class AXI4PLIC(nrIntr: Int = 31, nrConxt: Int = 2) extends AXI4Slave(new PlicIO(nrIntr, nrConxt)) {
  require(nrIntr < 1024)
  require(nrConxt <= 15872)
  val addressSpaceSize = 0x4000000
  val addressBits = log2Up(addressSpaceSize)
  def getOffset(addr: UInt) = addr(addressBits-1,0)
  val nrIntrWord = (nrIntr + 31) / 32  // roundup

  val pending = List.fill(nrIntrWord)(RegInit(0.U.asTypeOf(Vec(32, Bool()))))
  val pendingMap = pending.zipWithIndex.map { case (r, intrWord) =>
    RegMap(0x1000 + intrWord * 4, Cat(r.reverse), RegMap.Unwritable)
  }.toMap


  // 0x0000004 => priority(0) source 1 priority
  // 0x0000008 => priority(1) source 2 priority
  val priority = List.fill(nrIntr)(Reg(UInt(32.W)))
  val priorityMap = priority.zipWithIndex.map{ case (reg, intr) => 
    RegMap((intr + 1) * 4, reg)
  }.toMap


  // 0x0002000 => enable(0)(0) context 0 source  0-31
  // 0x0002004 => enable(0)(1) context 0 source 32-63
  // 0x0002080 => enable(1)(0) context 1 source  0-31
  // 0x0002084 => enable(1)(1) context 1 source 32-63
  val enable = List.fill(nrConxt)( List.fill(nrIntrWord)(RegInit(0.U(32.W))) )
  val enableMap = enable.zipWithIndex.map { case (regs, context) =>
                  regs.zipWithIndex.map { case (reg, intrWord) => 
                    RegMap(0x2000 + context * 0x80 + intrWord * 4, reg) }
  }.reduce(_ ++ _).toMap


  // 0x0200000 => prio(0) context 0 priority
  // 0x0201000 => prio(1) context 1 priority
  val threshold = List.fill(nrConxt)(RegInit(0.U(32.W)))
  val thresholdMap = threshold.zipWithIndex.map { case (reg, context) => 
    RegMap(0x200000 + context * 0x1000, reg)
  }.toMap


  // 0x0200004 => claim(0) context 0 claim/completion
  // 0x0201004 => claim(1) context 1 claim/completion

  val inHandle = RegInit(0.U.asTypeOf(Vec(nrIntr + 1, Bool())))
  def completionFn(wdata: UInt) = {
    inHandle(wdata(31,0)) := false.B
    0.U
  }

  val claimCompletion = List.fill(nrConxt)(RegInit(0.U(32.W)))
  val claimCompletionMap = claimCompletion.zipWithIndex.map {
    case (reg, context) => {
      val addr = 0x200004 + context * 0x1000
      when (io.in.r.fire() && (getOffset(raddr) === addr.U)) { 
        inHandle(reg) := true.B 
      }
      RegMap(addr, reg, completionFn)
    }
  }.toMap

  io.extra.get.intrVec.asBools.zipWithIndex.map { case (intr, index) => 
    {
      val id = index + 1
      pending(id / 32)(id % 32) := intr 
      when (inHandle(id)) {
        pending(id / 32)(id % 32) := false.B 
      }
    } 
  }

  val pendingVec = Cat(pending.map(x => Cat(x.reverse)))
  claimCompletion.zipWithIndex.map { case (reg, context) => {
    val takenVec = pendingVec & Cat(enable(context))
    reg := Mux(takenVec === 0.U, 
               0.U, 
               PriorityEncoder(takenVec)
               )
  } }

  val mapping = priorityMap ++ pendingMap ++ enableMap ++ thresholdMap ++ claimCompletionMap

  val rdata = Wire(UInt(32.W))
  RegMap.generate(mapping, getOffset(raddr), rdata,
                  getOffset(waddr), io.in.w.fire(), io.in.w.bits.data, MaskExpand(io.in.w.bits.strb >> waddr(2,0)))
  // narrow read
  io.in.r.bits.data := Fill(2, rdata)

  io.extra.get.meip.zipWithIndex.map { case (externIntr, context) => 
    externIntr := claimCompletion(context) =/= 0.U 
  }

  BoringUtils.addSource(pending(0),           "difftestplicpend")
  BoringUtils.addSource(enable(0)(0),         "difftestplicenable")
  BoringUtils.addSource(priority(0),          "difftestplicpriority")
  BoringUtils.addSource(threshold(0),         "difftestplicthreshold")
  BoringUtils.addSource(claimCompletion(0),   "difftestplicclaimed")
}
