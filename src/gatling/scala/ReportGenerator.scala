import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

import java.io.File
import java.nio.file.attribute.{BasicFileAttributes, FileTime}
import java.nio.file.Files
import scala.math.Ordered.orderingToOrdered

object ReportGenerator extends App {
  val resultsDirectory = new File(IDEPathHelper.resultsDirectory.toString)
  val resultToAnalyze = findLatestResult()
  analyzeResult(resultToAnalyze)

  private def analyzeResult(resultToAnalyze: String): Unit = {
    if (resultToAnalyze.isBlank == false) {
      println(s"Analyzing ${resultToAnalyze}")
      val props = new GatlingPropertiesBuilder()
        .resourcesDirectory(IDEPathHelper.resourcesDirectory.toString)
        .resultsDirectory(IDEPathHelper.resultsDirectory.toString)
        .binariesDirectory(IDEPathHelper.binariesDirectory.toString)
        .reportsOnly(resultToAnalyze)
      Gatling.fromMap(props.build)
    }
    else println("Did not find any simulation results to analyze")
  }

  private def findLatestResult(): String = {
    val contents = resultsDirectory.list()
    var latestFt: FileTime = FileTime.fromMillis(0)
    var latestResult = ""
    println(s"Found ${contents.size} simulation results in \"${resultsDirectory.toString}\"")
    for (c <- contents) {
      val p = new File(resultsDirectory + "\\" + c).toPath
      val attributes = Files.readAttributes(p, classOf[BasicFileAttributes])
      println(s"Simulation: ${c}, creationTime:  ${attributes.creationTime()}")
      val ft = attributes.creationTime()
      if (ft > latestFt) {
        latestFt = ft
        latestResult = c
      }
    }
    println(s"Latest simulation result is \"${latestResult}\".")
    latestResult
  }
}
