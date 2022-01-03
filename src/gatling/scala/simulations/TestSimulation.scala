package simulations

import io.gatling.core.Predef._
import plugin.Predef._

import scala.concurrent.duration._
import scala.language.postfixOps

class TestSimulation extends Simulation {
  val config = qd(address = "localhost:5555")

  val req = rmirequest("RMIRequest")
    .service("echo2")
    .method("")
    .returnType[String]
    .subject(null)
    .parameters(session => session("someNumber").as[Long], 2, 3.0, session => s"Hello ${session.userId}")
    //.check(substring("Hello").count.is(1))
    //.check(extract((_:String).some))
    //.extract(_.contains("Hellox").some)(_.saveAs("containsHello"))
    .extract(_.contains("Hello").some)(_.find.is(true))


  val testScenario1 = scenario("QDRMITestScenario1")
    .exec(session => session.set("someNumber", session.userId))
    .repeat(10) {
      exec(connect("QDConnect"))
        .exec(req)
        .pause(1 second)
        .exec(disconnect("QDDisconnect"))
        .pause(1 second)
    }

  val testScenario2 = scenario("QDRMITestScenario2")
    .exec(session => session.set("someNumber", session.userId))
    .repeat(10) {
      exec(connect("QDConnect"))
        .exec(req)
        .exec(disconnect("QDDisconnect"))
        .pause(5 second)
    }

  setUp(
    testScenario1.inject(rampUsers(5) during (5 seconds)),
    testScenario2.inject(rampUsers(10) during (10 seconds))
  ).protocols(config)
}
