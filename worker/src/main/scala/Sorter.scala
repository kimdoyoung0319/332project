package worker

class Sorter(inputFiles: Seq[os.Path], targetDir: os.Path) {
  import scala.concurrent.Future
  import utils.globalContext
  import common.{Block, Record}

  assert(os.isDir(targetDir))
  assert(inputFiles.forall(os.isFile(_)))

  def run(): Future[Seq[os.Path]] = sortAll(inputFiles).map(merge(targetDir))

  private def sortAll(files: Seq[os.Path]): Future[Seq[os.Path]] = {
    val sortedFutures = for (file <- files) yield sortAndRemove(file)
    Future.sequence(sortedFutures)
  }

  private def sortAndRemove(file: os.Path): Future[os.Path] = Future {
    val sortedFilePath = file / os.up / s"${file.last}.sorted"
    val sortedFile = Block(file).sorted(sortedFilePath)

    os.remove(file)
    sortedFilePath
  }

  private def merge(targetDir: os.Path)(files: Seq[os.Path]): Seq[os.Path] = {
    import scala.collection.mutable.Buffer

    var finalResultPaths = Seq[os.Path]()

    val blocksToMerge = files.map(Block(_))
    val outputBuffer = Buffer[Record]()
    val finalResultPathAllocator =
      new utils.FileNameAllocator(targetDir, "partition")

    while (blocksToMerge.forall(!_.exhausted())) {
      val largestHead = findBlockWithSmallestHead(blocksToMerge)
      outputBuffer += largestHead.advance()

      if (outputBuffer.size == Block.size) {
        val finalResultPath = finalResultPathAllocator.allocate()
        os.write(finalResultPath, outputBuffer.map(_.toArray()))
        outputBuffer.clear()
        finalResultPaths = finalResultPaths.appended(finalResultPath)
      }
    }

    val remainingResultPath = finalResultPathAllocator.allocate()
    os.write(remainingResultPath, outputBuffer.map(_.toArray()))
    remainingResultPath +: finalResultPaths
  }

  private def findBlockWithSmallestHead(blocks: Seq[Block]): Block = {
    val notExhuasted = blocks.filter(!_.exhausted())
    notExhuasted.minBy(_.contents.head)
  }
}
