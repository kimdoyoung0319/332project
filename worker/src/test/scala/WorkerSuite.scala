import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.PrivateMethodTester
import worker._

class WorkerSuit extends AnyFunSuite with PrivateMethodTester {
  test("Arguments must be parsed properly.") {
    import utils.stringToPath
    import os.Path

    val parseArgs =
      PrivateMethod[((String, Int), Seq[Path], Path)](Symbol("parseArgs"))
    val args = Array(
      "1.1.1.1:2222",
      "-I",
      "input/dir1",
      "input/dir2",
      "-O",
      "output/dir"
    )
    assert(
      (Main invokePrivate parseArgs(args)) === (("1.1.1.1", 2222),
      Seq(stringToPath("input/dir1"), stringToPath("input/dir2")),
      stringToPath("output/dir"))
    )
  }

  test("Ill-formed arguments must be caught by assertions. (1)") {
    import utils.stringToPath
    import os.Path

    val parseArgs =
      PrivateMethod[((String, Int), Seq[Path], Path)](Symbol("parseArgs"))
    val args = Array(
      "1.1.1.1.2222",
      "-I",
      "input/dir1",
      "input/dir2",
      "-O",
      "output/dir"
    )

    intercept[AssertionError]((Main invokePrivate parseArgs(args)))
  }

  test("Ill-formed arguments must be caught by assertions. (2)") {
    import utils.stringToPath
    import os.Path

    val parseArgs =
      PrivateMethod[((String, Int), Seq[Path], Path)](Symbol("parseArgs"))
    val args = Array(
      "1.1.1.1:2222",
      "-I",
      "-O",
      "output/dir"
    )

    intercept[AssertionError]((Main invokePrivate parseArgs(args)))
  }
}
