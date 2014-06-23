
package basic

import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.jdbc.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import akka.util.duration._
import bootstrap._

class BasicSimulation extends Simulation {

  val httpConf = httpConfig.baseURL(System.getProperty("url", "http://localhost:8080/"))

  val range = 1 to 100

  val chains = range.map(i =>
    exec(http("files").get(i.toString()).check(status.is(200))))
  val scen = scenario("test").roundRobinSwitch(chains.head, chains.tail: _*)

  setUp(scen.users(1000).ramp(5).protocolConfig(httpConf))
}
