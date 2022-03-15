package simulations

import com.devexperts.qd.ng.{RecordBuffer, RecordMode}
import com.devexperts.qd.DataScheme
import com.devexperts.rmi.RMIOperation
import io.gatling.core.Predef._
import plugin.Predef._
import servers.SampleScheme

import scala.concurrent.duration._
import scala.language.postfixOps

class TestSimulation extends Simulation {
  val scheme: DataScheme = SampleScheme.getInstance()

  def createSubscription(session: Session, symbol: String): RecordBuffer = {
    val sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION)
    val codec = scheme.getCodec
    sub.add(scheme.getRecord(0), codec.encode(symbol), symbol)
    sub
  }

  def createHistorySubscription(session: Session, symbol: String): RecordBuffer = {
    val sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION)
    val codec = scheme.getCodec
    // record should have hasTime==True
    sub.add(scheme.getRecord(2), codec.encode(symbol), symbol)
      .setTime((System.currentTimeMillis / 1000 - 20) << 32)
    sub
  }

  val config = qd(address = "localhost:5555")
    .withRMI
    .withStream
    .withTicker
    .withHistory
    .withScheme(scheme)

  val operation: RMIOperation[String] = RMIOperation.valueOf("echo2", classOf[String], "",
    classOf[Long], classOf[Int], classOf[Double], classOf[String])
  val req = rmiRequest("RMIRequest").operation(operation).subject(null)
    .parameters(session => session("someNumber").as[Long], 2, 3.0, session => s"Hello ${session.userId}")
    //.check(substring("Hello").count.is(1))
    //.check(extract((_:String).some))
    //.extract(_.contains("Hellox").some)(_.saveAs("containsHello"))
    .extract(_.contains("Hello").some)(_.find.is(true))

  val testScenario1 = scenario("QDRMITestScenario1")
    .exec(session => session.set("someNumber", session.userId))
    .repeat(10) {
      exec(connect("QDConnect"))
        .pause(1 second)
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
      //.pause(5 second)
    }

  val testScenario4 = scenario("QDRMITestScenario4")
    .exec(connect("QDConnect"))
    .exec(history("SPXHistory").subscribe(session => {
      createHistorySubscription(session, "SPX")
    }))
    .pause(15 seconds)
    .exec(history("SPXHistory").close())
    .pause(15 seconds)
    .exec(disconnect("QDDisconnect"))

  val testScenario5 = scenario("QDRMITestScenario5")
    .exec(session => session.set("someNumber", session.userId))
    .exec(connect("QDConnect"))
    .exec(stream("IBMStream").subscribe(session => {
      createSubscription(session, "IBM")
    }))
    .exec(ticker("ORCLTicker").subscribe(session => {
      createSubscription(session, "ORCL")
    }))
    .pause(10 seconds)
    .exec(history("SPXHistory").subscribe(session => {
      createHistorySubscription(session, "SPX")
    }))
    .pause(10 seconds)
    .exec(stream("MSFTStream").subscribe(session => {
      createSubscription(session, "MSFT")
    }))
    .pause(10 second)
    .exec(req)
    .pause(30 seconds)
    .exec(stream("IBMStream").close())
    .pause(10 seconds)
    .exec(stream("MSFTStream").close())
    .pause(10 seconds)
    .exec(ticker("ORCLTicker").close())
    .pause(10 seconds)
    .exec(history("SPXHistory").close())
    .pause(10 seconds)
    .exec(disconnect("QDDisconnect"))

  setUp(
    //testScenario1.inject(atOnceUsers(1)),
    //testScenario1.inject(rampUsers(5) during (5 seconds)),
    //testScenario2.inject(rampUsers(10) during (10 seconds)),
    testScenario1.inject(atOnceUsers(1))
  ).protocols(config)


}
