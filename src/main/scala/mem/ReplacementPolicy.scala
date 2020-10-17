package mem

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR
import rv64_3stage._

abstract class ReplacementPolicyBase {
  def choose_victim(array: Vec[MetaData]): UInt
  def update_meta(
      array: Vec[MetaData],
      access_index: UInt
  ): Vec[MetaData]

  // def choose_victim(array: Vec[IMetaData]): UInt
  // def update_meta(
  //     array: Vec[IMetaData],
  //     hitVec: UInt,
  //     victim_index: UInt
  // ): Vec[IMetaData]
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
      access_index: UInt
  ): Vec[MetaData] = { array }
}

object LRUPolicy extends ReplacementPolicyBase {
  def choose_victim(array: Vec[MetaData]): UInt = {
    val ageVec = VecInit(array.map(m => !m.meta.orR)).asUInt
    PriorityEncoder(ageVec)
  }
  def update_meta(
      array: Vec[MetaData],
      access_index: UInt
  ): Vec[MetaData] = {
    // printf(p"array=${array}, access_index=${access_index}\n")
    val length = log2Ceil(array.length)
    val new_meta = WireDefault(array)
    val old_meta_value =
      Mux(array(access_index).valid, array(access_index).meta, 0.U)
    (new_meta zip array).map {
      case (n, o) =>
        when(o.meta > old_meta_value) { n.meta := o.meta - 1.U }
        // printf(p"o.meta=${o.meta}, n.meta=${n.meta}\n")
    }
    new_meta(access_index).meta := Fill(length, 1.U(1.W))
    // printf(
    //   p"lru: access_index=${access_index}, old_meta_value=${old_meta_value}\n"
    // )
    new_meta
  }
}
