package master

object Main {
  def main(args: Array[String]): Unit = {
    val workerCount = parseArgs(args)
    val server = new MasterServer(workerCount)

    server.await()
  }

  def parseArgs(args: Array[String]): Int = {
    assert(args.size == 1)
    args(0).toInt
  }
}
