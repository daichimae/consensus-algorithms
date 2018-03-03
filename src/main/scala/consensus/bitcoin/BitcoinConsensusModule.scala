package consensus.bitcoin

import java.math.BigInteger
import java.security.MessageDigest

import com.google.common.primitives.Longs
import scorex.account.{Account, PrivateKeyAccount}
import scorex.block.{Block, BlockField}
import scorex.consensus.ConsensusModule
import scorex.transaction.{BalanceSheet, Transaction, TransactionModule}
import scorex.utils.{NTP, ScorexLogging}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
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

  val version = 1: Byte

  /**
    * Calculate the hash value of a block.
    *
    * @param block
    * @return
    */
  private def calculateBlockHash(block: Block): BigInt = {
    new BigInteger(1,
      MessageDigest.getInstance("SHA-256").digest(
      MessageDigest.getInstance("SHA-256").digest(block.bytes)))
  }

  override def isValid[TransactionalBlockData]
  (block: Block)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Boolean =
    calculateBlockHash(block) < consensusBlockData(block).target

  override def feesDistribution(block: Block): Map[Account, Long] = {
    val forger = block.consensusModule.generators(block).ensuring(_.size == 1).head
    val fee = block.transactions.map(_.fee).sum
    val height = block.transactionModule.blockStorage.history.height()
    val blockReward =
      if (height == 1) 0
      else InitialBlockReward / scala.math.pow(2, height / BlockRewardHalvingInterval).toInt

    Map(forger -> (fee + blockReward))
  }

  override def generators(block: Block): Seq[Account] = Seq(block.signerDataField.value.generator)

  override def blockScore(block: Block)(implicit transactionModule: TransactionModule[_]): BigInt =
    transactionModule.blockStorage.history.heightOf(block) match {
      case Some(i) => i
      case None => 1
    }

  override def generateNextBlock[TransactionalBlockData](account: PrivateKeyAccount)
  (implicit transactionModule: TransactionModule[TransactionalBlockData]): Future[Option[Block]] = {
    val lastBlock = transactionModule.blockStorage.history.lastBlock
    val lastBlockConsensusData = consensusBlockData(lastBlock)

    val lastBlockTime = lastBlock.timestampField.value

    val currentTime = NTP.correctedTime()

    // Generate a random number between 0 to 2^32 - 1 (unsigned 32-bit integer)
    val generatedNonce = (new scala.util.Random).nextInt().toLong + 2147483648l
    val currentTarget = lastBlockConsensusData.target

    val consensusData = new BitcoinConsensusBlockData {
      override val nonce: Long = generatedNonce
      override val target: BigInt = currentTarget
    }

    val eta = (currentTime - lastBlockTime) / 1000

    val unconfirmed = transactionModule.packUnconfirmed()

    /*val blockTry = Try(Block.buildAndSign(
      version,
      currentTime,
      lastBlock.uniqueId,
      consensusData,
      unconfirmed,
      account))
    val newBlock = Future(blockTry.recoverWith {
      case e =>
        log.error("Failed to build block:", e)
        Failure(e)
    }.toOption)*/

    val newBlock = Block.buildAndSign(
      version,
      currentTime,
      lastBlock.uniqueId,
      consensusData,
      unconfirmed,
      account)

    val hash = calculateBlockHash(newBlock)

    log.debug(s"hash: $hash, target: $currentTarget, generating ${hash < currentTarget}, eta $eta, " +
      s"nonce:  $generatedNonce")

    if (hash < currentTarget) {
      log.debug(s"Build block with ${unconfirmed.asInstanceOf[Seq[Transaction]].size} transactions")
      log.debug(s"Block time interval is $eta seconds ")
      Future(Some(newBlock))
    } else Future(None)
  }

  override def consensusBlockData(block: Block): BitcoinConsensusBlockData =
    block.consensusDataField.value match {
      case b: BitcoinConsensusBlockData => b
      case m => throw new AssertionError(s"Only BitcoinConsensusBlockData is available, $m given")
    }

  override def parseBytes(bytes: Array[Byte]): Try[BlockField[BitcoinConsensusBlockData]] =
    Try { BitcoinConsensusBlockField(new BitcoinConsensusBlockData {
      override val nonce: Long = Longs.fromByteArray(bytes.take(NonceLength))
      override val target: BigInt = new BigInteger(bytes.takeRight(bytes.size - NonceLength))
    })}

  override def genesisData: BlockField[BitcoinConsensusBlockData] =
    BitcoinConsensusBlockField(new BitcoinConsensusBlockData {
      override val nonce: Long = 0
      override val target: BigInt =
        //new BigInteger("00000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)
        new BigInteger("000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)
    })

  override def formBlockData(data: BitcoinConsensusBlockData): BlockField[BitcoinConsensusBlockData] =
    BitcoinConsensusBlockField(data)

  def generatingBalance[TransactionalBlockData]
  (address: String)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Long = {
    transactionModule.blockStorage.state.asInstanceOf[BalanceSheet]
      .balanceWithConfirmations(address, generatingBalanceDepth)
  }

  def generatingBalance[TransactionalBlockData]
  (account: Account)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Long =
    generatingBalance(account.address)

  val generatingBalanceDepth: Int = EffectiveBalanceDepth
}

object BitcoinConsensusModule {
  val NonceLength = 8
  val EffectiveBalanceDepth = 50
  val InitialBlockReward = 50
  val BlockRewardHalvingInterval = 210000
}