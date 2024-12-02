package master

object Main {
  def main(args: Array[String]): Unit = {
    require(
      args.size == 1,
      "Only the number of workers should be given as an argument."
    )
    require(
      args(0).toIntOption != None,
      "The first argument should be the number of workers."
    )

    val count = args(0).toInt
    val server = new Server(count)

    server.start()
    server.await()
  }
}
