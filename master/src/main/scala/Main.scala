package master

object Main {
  def main(args: Array[String]): Unit = {
    require(
      args.nonEmpty && args.length <= 2,
      "Invalid arguments. Provide either [1] count or [1] count, [2] inputPath."
    )
    require(
      args(0).toIntOption.isDefined,
      s"Invalid first argument '${args(0)}'. Please provide a valid integer for the number of workers."
    )

    val count = args(0).toInt
    val inputPath = if (args.length == 2) args(1) else null

    if(inputPath != null){
      require(
        isValidPath(inputPath),
        s"Invalid second argument '${inputPath}'. Please provide a valid file path."
      )
    }

    val server = new Server(count)
    server.start(inputPath)
    server.await()
  }

  private def isValidPath(path: String): Boolean = {
    import java.nio.file.Paths
    import java.nio.file.InvalidPathException

    try {
      Paths.get(path)
      true
    } catch {
      case _: InvalidPathException => false
    }
  }
}
