package consensus.bitcoin

import java.math.BigInteger
import java.security.MessageDigest

import com.google.common.primitives.Longs
import scorex.account.{Account, PrivateKeyAccount}
import scorex.block.{Block, BlockField}
import scorex.consensus.ConsensusModule
import scorex.transaction.TransactionModule
import scorex.utils.ScorexLogging

import scala.concurrent.Future
import scala.util.Try

/**
  * This class represents a Bitcoin-style Proof-of-Work consensus module.
  *
  * @author Daichi Mae
  */
class BitcoinConsensusModule extends ConsensusModule[BitcoinConsensusBlockData]
  with ScorexLogging {
  import BitcoinConsensusModule._

  implicit val consensusModule = this

  /**
    * Calculate the hash value of a block.
    *
    * @param block
    * @return
    */
  private def calculateBlockHash(block: Block): BigInt = {
    new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(block.bytes))
  }

  override def isValid[TransactionalBlockData]
  (block: Block)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Boolean = {
    calculateBlockHash(block) < consensusBlockData(block).target
  }

  override def feesDistribution(block: Block): Map[Account, Long] = {
    val forger = block.consensusModule.generators(block).ensuring(_.size == 1).head
    val fee = block.transactions.map(_.fee).sum
    Map(forger -> fee)
  }

  override def generators(block: Block): Seq[Account] = Seq(block.signerDataField.value.generator)

  override def blockScore(block: Block)(implicit transactionModule: TransactionModule[_]): BigInt =
    transactionModule.blockStorage.history.score()

  override def generateNextBlock[TransactionalBlockData](account: PrivateKeyAccount)
  (implicit transactionModule: TransactionModule[TransactionalBlockData]): Future[Option[Block]] = ???

  override def consensusBlockData(block: Block): BitcoinConsensusBlockData =
    block.consensusDataField.value match {
      case b: BitcoinConsensusBlockData => b
      case m => throw new AssertionError(s"Only BitcoinConsensusBlockData is available, $m given")
    }

  override def parseBytes(bytes: Array[Byte]): Try[BlockField[BitcoinConsensusBlockData]] =
    Try { BitcoinConsensusBlockField(new BitcoinConsensusBlockData {
      override val nonce: Long = Longs.fromByteArray(bytes.take(NonceLength))
      override val target: BigInt = new BigInteger(bytes)
    })}

  override def genesisData: BlockField[BitcoinConsensusBlockData] =
    BitcoinConsensusBlockField(new BitcoinConsensusBlockData {
      override val nonce: Long = -1
      override val target: BigInt =
        new BigInteger("00000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)
    })

  override def formBlockData(data: BitcoinConsensusBlockData): BlockField[BitcoinConsensusBlockData] =
    BitcoinConsensusBlockField(data)
}

object BitcoinConsensusModule {
  val NonceLength = 8
  val
}