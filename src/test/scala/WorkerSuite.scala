import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.PrivateMethodTester
import worker._

class WorkerSuite extends AnyFunSuite with PrivateMethodTester {
  val logger = com.typesafe.scalalogging.Logger("worker")

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

    intercept[IllegalArgumentException]((Main invokePrivate parseArgs(args)))
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

    intercept[IllegalArgumentException]((Main invokePrivate parseArgs(args)))
  }

  test("All files in the input directories must be identified correctly.") {
    import os.{temp, makeDir, write}
    import common.Block

    val tempDir = temp.dir()
    val inputDir1 = tempDir / "input" / "dir1"
    val inputDir2 = tempDir / "input" / "dir2"
    val inputDirs = Seq(inputDir1, inputDir2)

    makeDir.all(inputDir1)
    makeDir.all(inputDir2)

    val inputFiles = Seq(
      inputDir1 / "file.1",
      inputDir1 / "file.2",
      inputDir1 / "file.3",
      inputDir2 / "file.1",
      inputDir2 / "file.2"
    )

    inputFiles.foreach(write(_, "blah"))

    val identifyBlocks = PrivateMethod[Seq[Block]](Symbol("identifyBlocks"))
    val blocks = (Main invokePrivate identifyBlocks(inputDirs))

    assert(blocks.map(_.path) === inputFiles)
  }
}
