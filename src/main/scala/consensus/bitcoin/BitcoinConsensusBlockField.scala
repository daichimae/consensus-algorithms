package consensus.bitcoin

import com.google.common.primitives.{Bytes, Longs}
import play.api.libs.json.{JsObject, Json}
import scorex.block.BlockField
import scorex.crypto.encode.Base58

/**
  * This class represents a part of a block, wrapping the data that is used for
  * Proof-of-Work, and provides binary & json serializations.
  *
  * @author Daichi Mae
  */
case class BitcoinConsensusBlockField(override val value: BitcoinConsensusBlockData)
  extends BlockField[BitcoinConsensusBlockData] {
  override val name: String = "bitcoin-consensus"

  override def bytes: Array[Byte] = Bytes.ensureCapacity(Longs.toByteArray(value.nonce)
    ++ value.target.toByteArray, 8, 0)

  override def json: JsObject = Json.obj(name -> Json.obj(
    "nonce" -> value.nonce,
    "target" -> Base58.encode(value.target.toByteArray)
  ))
}
