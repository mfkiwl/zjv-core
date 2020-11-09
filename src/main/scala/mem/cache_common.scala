package mem

import chisel3._
import chisel3.util._
import rv64_nstage.core._
import device._

case class CacheConfig(
    readOnly: Boolean = false,
    hasMMIO: Boolean = true,
    name: String = "cache", // used for debug info
    userBits: Int = 0,
    idBits: Int = 0,
    blockBits: Int = 64, // size for each block in cache line
    ways: Int = 4, // set associativity
    lines: Int = 4, // number of `xlen`-bit blocks in each cache line
    totalSize: Int = 32, // K Bytes
    replacementPolicy: String = "lru" // lru, random not implemented
)

trait CacheParameters extends phvntomParams {
  implicit val cacheConfig: CacheConfig

  val readOnly = cacheConfig.readOnly
  val hasMMIO = cacheConfig.hasMMIO
  val cacheName = cacheConfig.name // used for debug info
  val userBits = cacheConfig.userBits
  val idBits = cacheConfig.idBits
  val blockBits = cacheConfig.blockBits
  val nWays = cacheConfig.ways
  val nLine = cacheConfig.lines
  val nBytes = cacheConfig.totalSize * 1024
  val nBits = nBytes * 8
  val lineBits = nLine * blockBits
  val lineBytes = lineBits / 8
  val lineLength = log2Ceil(nLine)
  val nSets = nBytes / lineBytes / nWays
  val offsetLength = log2Ceil(lineBytes)
  val indexLength = log2Ceil(nSets)
  val tagLength = xlen - (indexLength + offsetLength)
  val replacementPolicy: String = cacheConfig.replacementPolicy
  val policy: ReplacementPolicyBase = if (replacementPolicy == "lru") {
    LRUPolicy
  } else { RandomPolicy }
}

class CacheIO(implicit val cacheConfig: CacheConfig)
    extends Bundle
    with CacheParameters {
  val in = new MemIO(blockBits)
  val mem = Flipped(new MemIO(lineBits))
  val mmio = if (hasMMIO) { Flipped(new MemIO) }
  else { null }
}

class L2CacheIO(val n_sources: Int = 1)(implicit val cacheConfig: CacheConfig)
    extends Bundle
    with CacheParameters {
  val in = Vec(n_sources, new MemIO(blockBits))
  val mem = Flipped(new MemIO(lineBits))
}

class MetaData(implicit val cacheConfig: CacheConfig)
    extends Bundle
    with CacheParameters {
  val valid = Bool()
  val dirty = if (!readOnly) { Bool() }
  else { null }
  val meta = if (replacementPolicy == "lru") { UInt(log2Ceil(nWays).W) }
  else { UInt(1.W) }
  val tag = UInt(tagLength.W)
  override def toPrintable: Printable =
    p"MetaData(valid = ${valid}, dirty = ${dirty}, meta = ${meta}, tag = 0x${Hexadecimal(tag)})\n"
}

class CacheLineData(implicit val cacheConfig: CacheConfig)
    extends Bundle
    with CacheParameters {
  val data = Vec(nLine, UInt(blockBits.W))
  override def toPrintable: Printable =
    p"CacheLineData(data = ${data})\n"
}
