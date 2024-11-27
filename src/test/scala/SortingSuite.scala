/* TODO: Clean up global namespace of this module. */
import org.scalatest.funsuite.AnyFunSuite

class SortingSuite extends AnyFunSuite {
  import utils.test.{Gensort, Valsort}

  val logger = com.typesafe.scalalogging.Logger("root")
  val temp = os.temp.dir()

  def logRecordsWithPath(path: os.Path, msg: String): Unit = {
    assert(os.exists(path))
    utils.logRecords(
      logger,
      common.Block(path).load().toSeq,
      s"Contents of ${path.toString}, ${msg}."
    )
  }

  test("A block storing single record must be already sorted.") {
    val test = Gensort.makeBinary(1)
    assert(Valsort.validate(test))
  }

  test("A block storing a hundred of records may not have been sorted.") {
    val test = Gensort.makeBinary(100)
    assert(!Valsort.validate(test))
  }

  test("A single ASCII block should be sorted properly.") {
    val test = Gensort.makeAscii(5)
    val sorted = common.Block(test).sorted(temp / "ascii-test")
    assert(Valsort.validate(sorted.path))
  }

  test("A single binary block should be sorted properly.") {
    val test = Gensort.makeBinary(5)
    val sorted = common.Block(test).sorted(temp / "binary-test")
    assert(Valsort.validate(sorted.path))
  }
}
