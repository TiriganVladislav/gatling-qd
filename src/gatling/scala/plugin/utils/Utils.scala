package plugin.utils

import com.devexperts.qd.ng.RecordBuffer
import io.gatling.commons.stats.KO
import io.gatling.commons.util.Clock
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.core.action.Action
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.netty.util.StringBuilderPool

import scala.collection.mutable

case class Utils(statsEngine: StatsEngine, clock: Clock, next: Action) {
  def logFailAndMoveOn(requestName: String, session: Session, startTimestamp: Long, message: String): Unit = {
    statsEngine.logResponse(
      session.scenario,
      session.groups,
      requestName,
      startTimestamp = startTimestamp,
      endTimestamp = clock.nowMillis,
      status = KO,
      responseCode = Some("KO"),
      message = Some(message)
    )
    val newSession = session.markAsFailed
    next ! newSession
  }

  def getContentString(buffer: RecordBuffer): String = {
    buffer.rewind()
    val sb = StringBuilderPool.DEFAULT.get().append(Eol)
    var cur = buffer.next()
    while (cur != null) {
      val record = cur.getRecord
      sb.append("**************************************")
        .append(Eol)
        .append("Decoded symbol: ")
        .append(cur.getDecodedSymbol)
        .append(Eol)
        .append("Record name: " + record.getName)
        .append(Eol)
        .append("Record IntFieldCount: " + record.getIntFieldCount)
        .append(Eol)
        .append("Record ObjFieldCount: " + record.getObjFieldCount)
        .append(Eol)
        .append("Record hasTime: " + record.hasTime)
        .append(Eol)
        .append("Ints section start")
        .append(Eol)

      val ints: Array[Int] = new Array[Int](record.getIntFieldCount)
      cur.getIntsTo(0, ints, 0, record.getIntFieldCount)
      for (i: Int <- 0 until record.getIntFieldCount) {
        val f = record.getIntField(i)
        sb.append(s"ints[${i}]: {Name=${f.getName}, Value=${ints(i)}}")
          .append(Eol)
      }
      sb.append("Ints section end")
        .append(Eol)
      cur = buffer.next()
    }
    sb.append(Eol).append("**************************************")
    sb.toString()
  }

  def getStatsString(buffer: RecordBuffer): String = {
    val m: mutable.Map[String, Int] = mutable.Map[String, Int]()
    buffer.rewind()
    var cur = buffer.next()
    while (cur != null) {
      val record = cur.getRecord
      val name = record.getName
      if (m.contains(name)) m(name) = m(name) + 1
      else m.addOne(name, 1)
      cur = buffer.next()
    }
    val sb = StringBuilderPool.DEFAULT.get().append(Eol)
    for (key <- m.keys) {
      sb.append(s"${m(key)} of ${key} records").append(Eol)
    }
    sb.toString()
  }
}
