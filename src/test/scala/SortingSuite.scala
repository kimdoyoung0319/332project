/* TODO: Clean up global namespace of this module. */
import com.typesafe.scalalogging.Logger
import org.scalatest.funsuite.AnyFunSuite
import common.{Block, Record}
import os.Path

final class GensortFailedException extends Exception {}

object Gensort {
  var count = 0
  val gensort = os.pwd / "bin" / "gensort"
  val temp = os.temp.dir()

  require(os.exists(gensort))

  def makeBinary(n: Int): Path = {
    val file = temp / s"temp.$count"
    val cmd = (gensort, n, file)

    os.call(cmd = cmd, check = false).exitCode match {
      case 0 => count += 1; file
      case _ => throw new GensortFailedException
    }
  }

  def makeAscii(n: Int): Path = {
    val file = temp / s"temp.$count"
    val cmd = (gensort, "-a", n, file)

    os.call(cmd = cmd, check = false).exitCode match {
      case 0 => count += 1; file
      case _ => throw new GensortFailedException
    }
  }
}

object Valsort {
  val valsort = os.pwd / "bin" / "valsort"

  require(os.exists(valsort))

  def validate(file: Path): Boolean = {
    require(os.exists(file))

    val cmd = (valsort, file)
    os.call(cmd = cmd, check = false).exitCode match {
      case 0 => true
      case _ => false
    }
  }
}

class SortingSuite extends AnyFunSuite {
  val logger = Logger("root")
  val temp = os.temp.dir()

  def logRecordsWithPath(path: Path, msg: String): Unit = {
    assert(os.exists(path))
    utils.logRecords(
      logger,
      Block(path).load().toSeq,
      s"Contents of ${path.toString}, ${msg}."
    )
  }

  test(
    """
    This test suite requires gensort and valsort executables to be in bin/ 
    directory. Compile and put them if they aren't.
    """
  ) {}

  test("A block storing single record must be already sorted.") {
    val test = Gensort.makeBinary(1)
    logRecordsWithPath(test, "single block with 1 record")
    assert(Valsort.validate(test))
  }

  test("A block storing a hundred of records may not have been sorted.") {
    val test = Gensort.makeBinary(100)
    logRecordsWithPath(test, "block with hundred of records")
    assert(!Valsort.validate(test))
  }

  test("A single ASCII block should be sorted properly.") {
    val test = Gensort.makeAscii(5)
    logRecordsWithPath(test, "unsorted ASCII block")
    val sorted = Block(test).sorted(temp / "ascii-test")
    logRecordsWithPath(sorted.path, "sorted ASCII block")
    assert(Valsort.validate(sorted.path))
  }

  test("A single binary block should be sorted properly.") {
    val test = Gensort.makeBinary(5)
    logRecordsWithPath(test, "unsorted binary block")
    val sorted = Block(test).sorted(temp / "binary-test")
    logRecordsWithPath(sorted.path, "sorted binary block")
    assert(Valsort.validate(sorted.path))
  }
}
