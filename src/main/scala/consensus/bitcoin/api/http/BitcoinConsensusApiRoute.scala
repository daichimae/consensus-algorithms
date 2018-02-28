package consensus.bitcoin.api.http

import javax.ws.rs.Path

import akka.actor.ActorRefFactory
import akka.http.scaladsl.server.Route
import consensus.bitcoin.BitcoinConsensusModule
import io.swagger.annotations._
import play.api.libs.json.Json
import scorex.account.Account
import scorex.api.http.{ApiRoute, CommonApiFunctions, InvalidAddress}
import scorex.app.Application
import scorex.crypto.encode.Base58


@Path("/consensus")
@Api(value = "/consensus", description = "Consensus-related calls")
class BitcoinConsensusApiRoute(override val application: Application)(implicit val context: ActorRefFactory)
  extends ApiRoute with CommonApiFunctions {

  private val consensusModule = application.consensusModule.asInstanceOf[BitcoinConsensusModule]
  private val blockStorage = application.blockStorage

  override val route: Route =
    pathPrefix("consensus") {
      //algo ~ basetarget ~ baseTargetId ~ generatingBalance
      algo ~ generatingBalance
    }

  @Path("/generatingbalance/{address}")
  @ApiOperation(value = "Generating balance", notes = "Account's generating balance(the same as balance atm)", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "address", value = "Address", required = true, dataType = "String", paramType = "path")
  ))
  def generatingBalance: Route = {
    path("generatingbalance" / Segment) { case address =>
      getJsonRoute {
        if (!Account.isValidAddress(address)) {
          InvalidAddress.json
        } else {
          Json.obj(
            "address" -> address,
            "balance" -> consensusModule.generatingBalance(address)(application.transactionModule)
          )
        }
      }
    }
  }

/*  @Path("/basetarget/{blockId}")
  @ApiOperation(value = "Base target", notes = "base target of a block with specified id", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockId", value = "Block id ", required = true, dataType = "String", paramType = "path")
  ))
  def baseTargetId: Route = {
    path("basetarget" / Segment) { case encodedSignature =>
      getJsonRoute {
        withBlock(blockStorage.history, encodedSignature) { block =>
          Json.obj(
            "baseTarget" -> consensusModule.consensusBlockData(block).target
          )
        }
      }
    }
  }

  @Path("/basetarget")
  @ApiOperation(value = "Base target last", notes = "Base target of a last block", httpMethod = "GET")
  def basetarget: Route = {
    path("basetarget") {
      getJsonRoute {
        val lastBlock = blockStorage.history.lastBlock
        val bt = consensusModule.consensusBlockData(lastBlock).target
        Json.obj("baseTarget" -> bt)
      }
    }
  }*/

  @Path("/algo")
  @ApiOperation(value = "Consensus algo", notes = "Shows which consensus algo being using", httpMethod = "GET")
  def algo: Route = {
    path("algo") {
      getJsonRoute {
        Json.obj("consensusAlgo" -> "bitcoin")
      }
    }
  }
}
