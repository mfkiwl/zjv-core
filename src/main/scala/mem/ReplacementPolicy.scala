package mem

import chisel3._
import chisel3.util._
import rv64_3stage._

abstract class ReplacementPolicyBase {
  def choose_victim(array: Vec[MetaData]): Int
  def update_meta(
      array: Vec[MetaData],
      hitVec: UInt,
      victim_index: Int
  ): Vec[MetaData]
}

object RandomPolicy extends ReplacementPolicyBase {
  def apply() = {}
  def choose_victim(array: Vec[MetaData]): Int = {
    val invalidVec = VecInit(array.map(m => !m.valid)).asUInt
    0
  }
  def update_meta(
      array: Vec[MetaData],
      hitVec: UInt,
      victim_index: Int
  ): Vec[MetaData] = { array }
}

object LRUPolicy extends ReplacementPolicyBase {
  def apply() = {}
  def choose_victim(array: Vec[MetaData]): Int = {
    val invalidVec = VecInit(array.map(m => !m.valid)).asUInt
    0
  }
  def update_meta(
      array: Vec[MetaData],
      hitVec: UInt,
      victim_index: Int
  ): Vec[MetaData] = { array }
}
