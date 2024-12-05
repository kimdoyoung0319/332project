package master

object Main {
  def main(args: Array[String]): Unit = {
    require(
      args.size == 2,
      "Two arguments are required: [1] count: the number of workers, [2] size: {small, big, large}."
    )
    require(
      args(0).toIntOption.isDefined,
      s"Invalid first argument '${args(0)}'. Please provide a valid integer for the number of workers."
    )
    require(
      Set("small", "big", "large").contains(args(1)),
      s"Invalid second argument '${args(1)}'. Please provide one of the following: small, big, large."
    )

    val count = args(0).toInt
    val size = args(1)
    val server = new Server(count)

    server.start(size)
    server.await()
  }
}
