package master

import java.nio.file.Paths
import java.nio.file.InvalidPathException

object Main {
  def main(args: Array[String]): Unit = {
    require(
      args.size == 2,
      "Two arguments are required: [1] count: the number of workers, [2] inputPath: the path to the input data."
    )
    require(
      args(0).toIntOption.isDefined,
      s"Invalid first argument '${args(0)}'. Please provide a valid integer for the number of workers."
    )
    require(
      isValidPath(args(1)),
      s"Invalid second argument '${args(1)}'. Please provide a valid file path."
    )

    val count = args(0).toInt
    val inputPath = args(1)
    val server = new Server(count)

    server.start(inputPath)
    server.await()
  }

  private def isValidPath(path: String): Boolean = {
    try {
      Paths.get(path)
      true
    } catch {
      case _: InvalidPathException => false
    }
  }
}
