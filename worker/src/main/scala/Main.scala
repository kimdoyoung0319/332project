package worker

object Main {
  import os.Path
  import common.Block
  import utils.globalContext

  private val logger = com.typesafe.scalalogging.Logger("worker")

  def main(args: Array[String]): Unit = {
    import scala.util.{Success, Failure}

    val ((masterIp, masterPort), inputDirs, outputDir) = parseArgs(args)
    val blocks = identifyBlocks(inputDirs)

    val client = new WorkerClient(masterIp, masterPort, outputDir)
    val server = new WorkerServer(blocks, client)

    client.register(server.port).onComplete {
      case Success(id) =>
        logger.info(
          s"Succeeded to establish connection to ${masterIp}:${masterPort} as ID of ${id}."
        )
        server.await()
      case Failure(_) =>
        logger.error(
          s"Failed to establish connection to ${masterIp}:${masterPort}. Shutting down..."
        )
        server.stop()
    }

    server.await()
  }

  private def parseArgs(
      args: Array[String]
  ): ((String, Int), Seq[Path], Path) = {

    import utils.stringToPath

    val ipPortPattern = """^(\d{1,3}\.){3}\d{1,3}:\d{1,5}$""".r
    assert(ipPortPattern.matches(args(0)))
    val (masterIp, masterPort) = {
      val parts = args(0).split(":")
      (parts(0), parts(1).toInt)
    }

    /* I know what you gonna complain about, but the imperative style is way
         more concise for this kind of problems. */
    var isInputDir: Boolean = false
    var inputDirs: Seq[Path] = Nil
    var outputDir: Path = null

    for (arg <- args.drop(1)) {
      arg match {
        case "-I" => isInputDir = true
        case "-O" => isInputDir = false
        case _ if isInputDir =>
          inputDirs = inputDirs.appended(stringToPath(arg))
        case _ if !isInputDir => outputDir = stringToPath(arg)
      }
    }

    assert(inputDirs != Nil && outputDir != null)

    ((masterIp, masterPort), inputDirs, outputDir)
  }

  private def identifyBlocks(inputDirs: Seq[Path]): Seq[Block] =
    for (inputDir <- inputDirs; file <- os.list(inputDir) if os.isFile(file))
      yield Block(file)
}
