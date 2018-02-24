import akka.actor.Props
import scorex.account.Account
import scorex.api.http._
import scorex.app.{Application, ApplicationVersion}
import scorex.block.BlockField
import scorex.consensus.nxt.NxtLikeConsensusModule
import scorex.consensus.nxt.api.http.NxtConsensusApiRoute
import scorex.network._
import scorex.settings.Settings
import scorex.transaction.SimpleTransactionModule.StoredInBlock
import scorex.transaction._

import scala.reflect.runtime.universe._

/**
  * A simple blockchain application with SimpleTransactionModule and
  * NxtLikeConsensusModule.
  *
  * @param settingsFilename name of the settings file
  */
class MyApplication(val settingsFilename: String) extends Application {
  // Your application config
  override val applicationName = "my application"
  override val appVersion = ApplicationVersion(0, 1, 0)
  override implicit lazy val settings = new Settings with TransactionSettings {
     override lazy val filename: String = settingsFilename
  }

  // Define consensus and transaction modules of your application
  override implicit lazy val consensusModule = new NxtLikeConsensusModule()
  override implicit lazy val transactionModule= new SimpleTransactionModule()(settings, this) {
    override def genesisData: BlockField[StoredInBlock] = {
      val timestamp = 0L
      val totalBalance = InitialBalance
      val txs = List(
        // Give the foundation tokens to the address that is created with the
        // seed value "bcnode1".
        GenesisTransaction(new Account("2nN9bhaoYV5J9avR2seG4UwPwzKWXbnZPsR"),
          totalBalance, timestamp)
      )
      TransactionsBlockField(txs)
    }
  }

  // Define API routes of your application
  override lazy val apiRoutes = Seq(
    BlocksApiRoute(this),
    TransactionsApiRoute(this),
    new NxtConsensusApiRoute(this),
    WalletApiRoute(this),
    PaymentApiRoute(this),
    UtilsApiRoute(this),
    PeersApiRoute(this),
    AddressApiRoute(this)
  )

  // API types are required for swagger support
  override lazy val apiTypes = Seq(
    typeOf[BlocksApiRoute],
    typeOf[TransactionsApiRoute],
    typeOf[NxtConsensusApiRoute],
    typeOf[WalletApiRoute],
    typeOf[PaymentApiRoute],
    typeOf[UtilsApiRoute],
    typeOf[PeersApiRoute],
    typeOf[AddressApiRoute]
  )

  // Create your custom messages and add them to additionalMessageSpecs
  override lazy val additionalMessageSpecs = TransactionalMessagesRepo.specs

  // Start additional actors
  actorSystem.actorOf(Props(classOf[UnconfirmedPoolSynchronizer], this))
}