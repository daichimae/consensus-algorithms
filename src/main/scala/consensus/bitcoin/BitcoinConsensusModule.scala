package consensus.bitcoin

import java.math.BigInteger
import java.security.MessageDigest

import com.google.common.primitives.Longs
import scorex.account.{Account, PrivateKeyAccount}
import scorex.block.{Block, BlockField}
import scorex.consensus.ConsensusModule
import scorex.transaction.{Transaction, TransactionModule}
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

  /**
    * Calculate the hash value of a block by applying SHA-256 twice to it.
    *
    * @param block block to be hashed
    * @return 256 bit hash value
    */
  private def calculateBlockHash(block: Block): BigInt = {
    new BigInteger(1,
      MessageDigest.getInstance("SHA-256").digest(
      MessageDigest.getInstance("SHA-256").digest(block.bytes)))
  }

  /**
    * Calculate the target for the block to be mined. The difficulty is adjusted
    * every 2016 block so it takes roughly 2 weeks to generate 2016 blocks, that
    * is, 1 block every 10 minutes.
    *
    * @param block block to get the current target from
    * @return new target value
    */
  private def calculateTarget(block:Block): BigInt = {
    val height = block.transactionModule.blockStorage.history.height()
    if (height % DifficultyAdjustmentBlockInterval == 0) {
      // Adjust the difficulty.
      val lastTimestamp =
        block.transactionModule.blockStorage.history.parent(block, DifficultyAdjustmentBlockInterval - 1)
        match {
          case Some(i) => i.timestampField.value
          case None => 0l
        }
      // If the timestamp field doesn't have a value return the previous target.
      if (lastTimestamp == 0)
        consensusBlockData(block).target
      else {
        // Calculate the time it took to generate 2016 blocks.
        val timeElapsed = NTP.correctedTime() - lastTimestamp
        // The correction factor is calculated as (2 weeks / time it took to generate 2016 blocks).
        var correctionFactor = DifficultyAdjustmentTimeInterval.toDouble / timeElapsed
        // Bound the correction factor to prevent the change to be too abrupt.
        if (correctionFactor > 4)
          correctionFactor = 4.0
        else if (correctionFactor < 1.0 / 4)
          correctionFactor = 1.0 / 4

        // The new target is calculated as (last target / correction factor).
        val newTarget = (BigDecimal(consensusBlockData(block).target) / correctionFactor).toBigInt()
        log.debug(s"correction factor: $correctionFactor, new target: $newTarget")
        newTarget
      }
    } else {
      // Use the current target.
      consensusBlockData(block).target
    }
  }

  /**
    * Generate a positive integer for the nonce. The integer value is incremented
    * by 1 when block generation fails and is reset to 0 if someone successfully
    * mined a block, which is detected by examining the timestamp of the last block.
    *
    * @param block last block
    * @return nonce
    */
  private def calculateNonce(block: Block): Long = {
    val generatedNonce = nonce
    if (LastBlockTimeStamp == block.timestampField.value) {
      nonce += 1
    } else {
      LastBlockTimeStamp = block.timestampField.value
      nonce = 0
    }
    generatedNonce
  }

  override def isValid[TransactionalBlockData]
  (block: Block)(implicit transactionModule: TransactionModule[TransactionalBlockData]): Boolean =
    calculateBlockHash(block) < consensusBlockData(block).target

  override def feesDistribution(block: Block): Map[Account, Long] = {
    val forger = block.consensusModule.generators(block).ensuring(_.size == 1).head
    val fee = block.transactions.map(_.fee).sum
    val height = block.transactionModule.blockStorage.history.height()
    // Block reward is halved every specified number of blocks.
    val blockReward = InitialBlockReward / scala.math.pow(2, height / BlockRewardHalvingInterval).toInt

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
    val currentTime = NTP.correctedTime()

    val currentTarget = calculateTarget(lastBlock)

    val consensusData = new BitcoinConsensusBlockData {
      override val nonce: Long = calculateNonce(lastBlock)
      override val target: BigInt = currentTarget
    }

    val eta = (currentTime - lastBlock.timestampField.value) / 1000
    val unconfirmed = transactionModule.packUnconfirmed()

    val newBlock = Block.buildAndSign(
      Version,
      currentTime,
      lastBlock.uniqueId,
      consensusData,
      unconfirmed,
      account)

    val hash = calculateBlockHash(newBlock)

    /*log.debug(s"hash: $hash, target: $currentTarget, generating ${hash < currentTarget}, eta $eta, " +
      s"nonce:  ${consensusData.nonce}")*/

    if (hash < currentTarget) {
      // Found a valid nonce and successfully mined a block.
      log.debug(s"height: ${blockScore(lastBlock)+1}, hash: $hash, target: $currentTarget, eta: $eta, " +
        s"timestamp: $currentTime, nonce: ${consensusData.nonce}, "+
      s"block id: ${newBlock.encodedId}, last block id: ${lastBlock.encodedId}")
      log.debug(s"Build block with ${unconfirmed.asInstanceOf[Seq[Transaction]].size} transactions")
      //log.debug(s"Block time interval is $eta seconds ")
      Future(Some(newBlock))
    } else {
      Future(None)
    }
  }

  override def consensusBlockData(block: Block): BitcoinConsensusBlockData =
    block.consensusDataField.value match {
      case b: BitcoinConsensusBlockData => b
      case m => throw new AssertionError(s"Only BitcoinConsensusBlockData is available, $m given")
    }

  override def parseBytes(bytes: Array[Byte]): Try[BlockField[BitcoinConsensusBlockData]] =
    Try { BitcoinConsensusBlockField(new BitcoinConsensusBlockData {
      override val nonce: Long = Longs.fromByteArray(bytes.take(NonceByteLength))
      override val target: BigInt = new BigInteger(bytes.takeRight(bytes.length - NonceByteLength))
    })}

  override def genesisData: BlockField[BitcoinConsensusBlockData] =
    BitcoinConsensusBlockField(new BitcoinConsensusBlockData {
      override val nonce: Long = 0
      override val target: BigInt = InitialTarget
    })

  override def formBlockData(data: BitcoinConsensusBlockData): BlockField[BitcoinConsensusBlockData] =
    BitcoinConsensusBlockField(data)
}

object BitcoinConsensusModule {
  val Version = 1: Byte
  val NonceByteLength = 8
  val InitialBlockReward = 50
  val BlockRewardHalvingInterval = 210000
  val DifficultyAdjustmentBlockInterval = 50
  val DifficultyAdjustmentTimeInterval = 30000000 // 500 minutes in milliseconds
  //val DifficultyAdjustmentBlockInterval = 2016
  //val DifficultyAdjustmentTimeInterval = 2 weeks in milliseconds
  //val InitialTarget = new BigInteger("00000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)
  val InitialTarget = new BigInteger("000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)

  var nonce = 0: Long
  var LastBlockTimeStamp = 0: Long
}
