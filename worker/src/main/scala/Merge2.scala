package worker

import common.LoadedRecords

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

  def run(): Future[DiskRecordsSeq] = Future {
    val recordsPriorityQueue = PriorityQueue[Record]()
    val mergedDiskRecords = ListBuffer[DiskRecords]()
    var postfix = 0

    for (sortedFile <- sortedFiles; record <- sortedFile.loadAll().contents)
      recordsPriorityQueue.enqueue(record)

    val recordsBuffer = LoadedRecords()
    while (recordsPriorityQueue.nonEmpty) {
      recordsBuffer += recordsPriorityQueue.dequeue()

      if (recordsBuffer.sizeInByte == utils.general.maxPartitionSize)
        mergedDiskRecords += recordsBuffer.writeIntoAndClear(
          outputDir / s"partition.${postfix}"
        )
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
