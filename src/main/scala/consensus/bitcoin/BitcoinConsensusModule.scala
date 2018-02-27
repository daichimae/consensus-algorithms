package consensus.bitcoin

import java.math.BigInteger
import java.security.MessageDigest

import scorex.account.{Account, PrivateKeyAccount}
import scorex.block.{Block, BlockField}
import scorex.consensus.ConsensusModule
import scorex.crypto.hash.{Blake256, SecureCryptographicHash}
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
  (block: Block)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Boolean = true

  override def feesDistribution(block: Block): Map[Account, Long] = {
    val forger = block.consensusModule.generators(block).ensuring(_.size == 1).head
    val fee = block.transactions.map(_.fee).sum
    Map(forger -> fee)
  }

  override def generators(block: Block): Seq[Account]

  override def blockScore(block: Block)(implicit transactionModule: TransactionModule[_]): BigInt = ???

  override def generateNextBlock[TransactionalBlockData]
  (account: PrivateKeyAccount)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Future[Option[Block]] = ???

  override def consensusBlockData(block: Block): BitcoinConsensusBlockData = ???

  override def parseBytes(bytes: Array[Byte]): Try[BlockField[BitcoinConsensusBlockData]] = ???

  override def genesisData: BlockField[BitcoinConsensusBlockData] =
    BitcoinConsensusBlockField(new BitcoinConsensusBlockData {
      override val nonce: Long = -1
      override val target: BigInt = new BigInteger("00000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)
    })

  override def formBlockData(data: BitcoinConsensusBlockData): BlockField[BitcoinConsensusBlockData] = ???
}
