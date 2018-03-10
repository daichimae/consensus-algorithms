package consensus.bitcoin.api.http

import javax.ws.rs.Path

import akka.actor.ActorRefFactory
import akka.http.scaladsl.server.Route
import consensus.bitcoin.BitcoinConsensusModule
import io.swagger.annotations._
import play.api.libs.json.Json
import scorex.api.http.{ApiRoute, CommonApiFunctions}
import scorex.app.Application

/**
  * This class defines APIs. Currently the following APIs are defined:
  *   /consensus/algo - Return the name of the consensus algorithm being used
  *
  * @param application Scorex Application instance
  * @param context ActorRefFactory instance
  */
@Path("/consensus")
@Api(value = "/consensus", description = "Consensus-related calls")
class BitcoinConsensusApiRoute(override val application: Application)(implicit val context: ActorRefFactory)
  extends ApiRoute with CommonApiFunctions {
  private val consensusModule = application.consensusModule.asInstanceOf[BitcoinConsensusModule]
  private val blockStorage = application.blockStorage

  override val route: Route =
    pathPrefix("consensus") {
      algo
    }

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
