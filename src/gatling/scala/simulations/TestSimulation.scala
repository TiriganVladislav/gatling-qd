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
    .parameters(1, 2, 3.0, s => {
      val userId = s.userId
      s"Hello ${userId}"
    })
    //.check(substring("Hello").count.is(1))
    //.check(extract((_:String).some))
    //.extract(_.contains("Hellox").some)(_.saveAs("containsHello"))
    .extract(_.contains("Hello").some)(_.find.is(true))


  val testScenario = scenario("QDRMITestScenario")
    .forever(
      pace(1 second)
        .exec(session => {
          val newSession = session.set("userId", session.userId)
          println(newSession)
          newSession
        })
        .exec(req)
        .exec(session => {
          println(session)
          session
        })
    )

  setUp(testScenario.inject(atOnceUsers(1))).protocols(config).maxDuration(5 seconds)
}
