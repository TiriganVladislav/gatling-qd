import java.nio.file.Paths

object IDEPathHelper {

  val projectRootDir = Paths.get(
    getClass
      .getClassLoader
      .getResource("gatling.conf")
      .toURI
  )
    .getParent
    .getParent
    .getParent

  val targetDirectory = projectRootDir.resolve("target")
  val resourcesDirectory = projectRootDir.resolve("resources")
  val binariesDirectory = targetDirectory.resolve("classes")
  val resultsDirectory = targetDirectory.resolve("gatling")
}