package consensus.bitcoin

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

  implicit val consensusModule = this
  private def calculateBlockHash(block: Block): BigInt = ???

  override def isValid[TransactionalBlockData]
  (block: Block)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Boolean = ???

  override def feesDistribution(block: Block): Map[Account, Long] = ???

  override def generators(block: Block): Seq[Account] = ???

  override def blockScore(block: Block)(implicit transactionModule: TransactionModule[_]): BigInt = ???

  override def generateNextBlock[TransactionalBlockData]
  (account: PrivateKeyAccount)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Future[Option[Block]] = ???

  override def consensusBlockData(block: Block): BitcoinConsensusBlockData = ???

  override def parseBytes(bytes: Array[Byte]): Try[BlockField[BitcoinConsensusBlockData]] = ???

  override def genesisData: BlockField[BitcoinConsensusBlockData] = ???

  override def formBlockData(data: BitcoinConsensusBlockData): BlockField[BitcoinConsensusBlockData] = ???
}
