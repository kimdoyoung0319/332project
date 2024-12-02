package worker

object Main {
  def main(args: Array[String]): Unit = {
    val ((masterIp, masterPort), inputDirs, outputDir) = parseArgs(args)
    val inputs = identifyInputs(inputDirs)
    val server = new Server(masterIp, masterPort, inputs, outputDir)

    server.start()
    server.await()
  }

  private def identifyInputs(dirs: Seq[os.Path]): Seq[common.DiskRecords] = {
    for (dir <- dirs; file <- os.list(dir) if os.isFile(file))
      yield common.DiskRecords(file)
  }

  private def parseArgs(
      args: Array[String]
  ): ((String, Int), Seq[os.Path], os.Path) = {

    require(
      utils.network.addressPattern.matches(args(0)),
      "The pattern for master server address does not match."
    )

    val (masterIp, masterPort) = {
      val parts = args(0).split(":")
      (parts(0), parts(1).toInt)
    }

    var isInputDir: Boolean = false
    var inputDirs: Seq[os.Path] = Nil
    var outputDir: os.Path = null

    for (arg <- args.drop(1)) {
      arg match {
        case "-I" => isInputDir = true
        case "-O" => isInputDir = false
        case _ if isInputDir =>
          inputDirs = inputDirs.appended(os.Path(arg))
        case _ if !isInputDir => outputDir = os.Path(arg, os.pwd)
      }
    }

    require(inputDirs != Nil && outputDir != null)

    ((masterIp, masterPort), inputDirs, outputDir)
  }
}
