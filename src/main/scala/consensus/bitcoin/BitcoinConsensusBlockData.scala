package consensus.bitcoin

/**
  * This trait defines block data fields for a Bitcoin-style Proof-of-Work
  * consensus algorithm.
  *
  * @author Daichi Mae
  */
trait BitcoinConsensusBlockData {
  val nonce: Long
  val target: BigInt
}
