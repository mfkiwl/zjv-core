package mem

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR
import rv64_3stage._

abstract class ReplacementPolicyBase {
  def choose_victim(array: Vec[MetaData]): UInt
  def update_meta(
      array: Vec[MetaData],
      hitVec: UInt,
      victim_index: UInt
  ): Vec[MetaData]
}

object RandomPolicy extends ReplacementPolicyBase {
  def choose_victim(array: Vec[MetaData]): UInt = {
    val invalidVec = VecInit(array.map(m => !m.valid)).asUInt
    val length = log2Ceil(array.length)
    val result = Wire(UInt(length.W))
    when(invalidVec.orR) { 
      result := PriorityEncoder(invalidVec) 
    }.otherwise {
      result := LFSR(length)
    }
    result
  }
  def update_meta(
      array: Vec[MetaData],
      hitVec: UInt,
      victim_index: UInt
  ): Vec[MetaData] = { array }
}

object LRUPolicy extends ReplacementPolicyBase {
  def choose_victim(array: Vec[MetaData]): UInt = {
    val invalidVec = VecInit(array.map(m => !m.valid)).asUInt
    0.U
  }
  def update_meta(
      array: Vec[MetaData],
      hitVec: UInt,
      victim_index: UInt
  ): Vec[MetaData] = { array }
}
