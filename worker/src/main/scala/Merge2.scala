package worker

class Merger2(
    sortedFiles: Seq[common.DiskRecords],
    outputDir: os.Path,
    thisId: Int
) {
  import scala.concurrent.Future
  import scala.collection.mutable.{PriorityQueue, ListBuffer}
  import common.{DiskRecords, LoadedRecords, Record}
  import utils.concurrent.global

  type DiskRecordsSeq = Seq[DiskRecords]

  private val logger = utils.logger.LoggerFactoryUtil.getLogger("worker")

  def run(): Future[DiskRecordsSeq] = Future {
    logger.info(
      s"[${thisId}] Merging ${sortedFiles.size} files, whose names are..."
    )
    for (sortedFile <- sortedFiles)
      logger.info(s"[${thisId}] ${sortedFile.path}")

    val recordsPriorityQueue = PriorityQueue[Record]()(Record.Ordering.reverse)
    val mergedDiskRecords = ListBuffer[DiskRecords]()
    var postfix = 0

    for (sortedFile <- sortedFiles; record <- sortedFile.loadAll().contents)
      recordsPriorityQueue.enqueue(record)

    val recordsBuffer = LoadedRecords()
    while (recordsPriorityQueue.nonEmpty) {
      recordsBuffer += recordsPriorityQueue.dequeue()

      if (recordsBuffer.sizeInByte >= utils.general.maxPartitionSize) {
        mergedDiskRecords += recordsBuffer.writeIntoAndClear(
          outputDir / s"partition.${postfix}"
        )
        postfix += 1
      }
    }

    if (recordsBuffer.nonEmpty)
      mergedDiskRecords += recordsBuffer.writeIntoAndClear(
        outputDir / s"partition.${postfix}"
      )

    cleanTempDirs()
    mergedDiskRecords.toSeq
  }

  private def cleanTempDirs(): Unit = {
    os.remove.all(outputDir / "received")
    os.remove.all(outputDir / "sorted")
  }
}
