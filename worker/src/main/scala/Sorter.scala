package worker

import scala.collection.mutable

/* For debug purposes. */
object SorterTester {
  def test(): Unit = {
    import common.Block
    import utils.globalContext
    import scala.util.{Success, Failure}
    import utils.test.Gensort
    import scala.io.StdIn.readLine

    val inputDir = os.home / "temp" / "input"
    val targetDir = os.home / "temp" / "output"

    for (entry <- os.list(inputDir))
      os.remove.all(entry)

    for (entry <- os.list(targetDir))
      os.remove.all(entry)

    Gensort.makeBinaryAt(1000, inputDir / "input.0")
    Gensort.makeBinaryAt(1000, inputDir / "input.1")
    Gensort.makeBinaryAt(1000, inputDir / "input.2")

    readLine("Dataset generated. Press any key to continue...")

    val inputBlocks = os.list(inputDir).map(Block(_))
    val sorter = new Sorter(inputBlocks, targetDir)

    sorter.run().onComplete {
      case Success(blocks) => blocks foreach println
      case Failure(exception) => throw exception
    }
  }
}

class Sorter(inputBlocks: Seq[common.Block], targetDir: os.Path) {
  import utils.globalContext
  import scala.concurrent.Future
  import scala.collection.mutable.Buffer
  import common.{Block, Record}
  import os.Path

  val logger = com.typesafe.scalalogging.Logger("worker")
  var nextFinalFileNumber = 0

  def run(): Future[Seq[Block]] = sortAll().map(merge)
  def runMergingOnly(): Future[Seq[Block]] = Future(merge(inputBlocks))

  private val sortedFilesTargetDir = targetDir / "sorted"

  private def sortAll(): Future[Seq[Block]] = {
    utils.cleanDirectory(sortedFilesTargetDir)

    val sortedFileNameAllocator =
      new utils.FileNameAllocator(sortedFilesTargetDir, "sorted")
    Future.sequence(inputBlocks.map(sortOneAndRemove(sortedFileNameAllocator)))
  }

  private def sortOneAndRemove(
      fileNameAllocator: utils.FileNameAllocator
  )(block: common.Block): Future[Block] = Future {
    val sortedFileName = fileNameAllocator.allocate()
    val resultBlock = block.sorted(sortedFileName)
    block.remove()
    resultBlock
  }

  private def merge(blocks: Seq[Block]): Seq[Block] = {
    import scala.collection.mutable.{PriorityQueue, ListBuffer}
    import utils.RecordsBufferExtended

    /* TODO: This may be problematic since it loads all the block contents into
             the memory... */
    val recordsPriorityQueue = PriorityQueue[Record]()(Record.Ordering.reverse)
    val writtenBlocks = ListBuffer[Block]()
    for (block <- blocks; record <- block.contents)
      recordsPriorityQueue.enqueue(record)

    val recordsBuffer = Buffer[Record]()
    while (recordsPriorityQueue.nonEmpty) {
      val smallestRecord = recordsPriorityQueue.dequeue()
      recordsBuffer += smallestRecord

      if (recordsBuffer.size == Block.size)
        writtenBlocks += recordsBuffer.writeIntoDisk(nextFilePath)
    }

    if (!recordsBuffer.isEmpty)
      writtenBlocks += recordsBuffer.writeIntoDisk(nextFilePath)
    writtenBlocks.toSeq
  }

  private def nextFilePath: os.Path = {
    val result = targetDir / s"partition.${nextFinalFileNumber}"
    nextFinalFileNumber += 1
    result
  }
}
