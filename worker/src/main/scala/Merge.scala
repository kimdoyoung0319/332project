package worker

class Merger(
    sortedFiles: Seq[common.DiskRecords],
    outputDir: os.Path,
    thisId: Int
) {
  import scala.concurrent.Future
  import scala.collection.mutable.{ListBuffer, Queue}
  import common.{DiskRecords, LoadedRecords}
  import utils.concurrent.{global, FutureCompanionOps}
  import utils.memory.MemoryManager

  type DiskRecordsSeq = Seq[DiskRecords]

  private val logger = utils.logger.LoggerFactoryUtil.getLogger("worker")
  private val counter = utils.concurrent.SafeCounter(0)
  private val memoryNeededToMerge = utils.general.maxPartitionSize * 3
  private val mergeTaskQueue =
    utils.concurrent.SafeQueue[DiskRecordsSeq](sortedFiles.map(Seq(_)))

  def run(): Future[DiskRecordsSeq] = {
    val concurrency = 10

    logger.info(
      s"[${thisId}] Merging ${sortedFiles.size} files, whose names are..."
    )
    for (sortedFile <- sortedFiles)
      logger.info(s"[${thisId}] ${sortedFile.path}")

    Future
      .sequence {
        for (id <- 0 until concurrency) yield mergeWorkerFuture(id)
      }
      .map { _ =>
        val finalSortedFiles = mergeTaskQueue.dequeue()
        diskRecordsMovedIntoOutputDirectory(finalSortedFiles)
      }
  }

  /* TODO: Fix the problem where the files are not popped properly. */
  private def mergeWorkerFuture(id: Int): Future[Unit] = Future.repeat {
    mergeTaskQueue.tryDequeueTwo() match {
      case None => Future(false)
      case Some((first, second)) => mergeTwoDiskRecordsSeq(id)(first, second)
    }
  }

  private def mergeTwoDiskRecordsSeq(id: Int)(
      first: DiskRecordsSeq,
      second: DiskRecordsSeq
  ): Future[Boolean] = MemoryManager.ensured(memoryNeededToMerge) {
    logger.info(s"[${thisId}] {${id}} Merging two disk records sequence...")
    for (firstDiskRecord <- first)
      logger.info(s"[${thisId}] {${id}} First: ${firstDiskRecord.path}")
    for (secondDiskRecord <- first)
      logger.info(s"[${thisId}] {${id}} Second: ${secondDiskRecord.path}")

    val outputBuffer = LoadedRecords()
    val sortedDiskRecordsList = ListBuffer[DiskRecords]()

    val firstQueue = Queue.from(first)
    val secondQueue = Queue.from(second)

    var firstDiskRecords = firstQueue.dequeue()
    var secondDiskRecords = secondQueue.dequeue()

    var firstIndex = 0
    var secondIndex = 0

    while (firstQueue.nonEmpty && secondQueue.nonEmpty) {
      val indices =
        mergeTwoDiskRecords(id)(
          firstDiskRecords,
          secondDiskRecords,
          firstIndex,
          secondIndex,
          outputBuffer,
          sortedDiskRecordsList
        )

      firstIndex = indices._1
      secondIndex = indices._2

      assert(
        firstIndex == firstDiskRecords.size || secondIndex == secondDiskRecords.size,
        "Either first disk record or second disk record should have been read completely."
      )

      if (firstIndex == firstDiskRecords.size)
        firstDiskRecords = firstQueue.dequeue()
      else
        secondDiskRecords = secondQueue.dequeue()
    }

    exhaustDiskRecordsInto(
      firstDiskRecords,
      outputBuffer,
      sortedDiskRecordsList,
      firstIndex
    )
    exhaustDiskRecordsInto(
      secondDiskRecords,
      outputBuffer,
      sortedDiskRecordsList,
      secondIndex
    )

    if (outputBuffer.nonEmpty)
      writeClear(outputBuffer, sortedDiskRecordsList)

    exhaustDiskRecordsQueueInto(firstQueue, sortedDiskRecordsList)
    exhaustDiskRecordsQueueInto(secondQueue, sortedDiskRecordsList)

    mergeTaskQueue.enqueue(sortedDiskRecordsList.toSeq)

    logger.info(
      s"[${thisId}] {${id}} Finished merging two files. The output sequence is..."
    )
    for (sorted <- sortedDiskRecordsList)
      logger.info(s"[${thisId}] {$id} ${sorted.path}")

    true
  }

  private def mergeTwoDiskRecords(id: Int)(
      first: DiskRecords,
      second: DiskRecords,
      firstInitialIndex: Int,
      secondInitialIndex: Int,
      outputBuffer: LoadedRecords,
      sortedDiskRecordsList: ListBuffer[DiskRecords]
  ): (Int, Int) = {
    val firstLoaded = first.loadAll()
    val secondLoaded = second.loadAll()

    var firstIndex = firstInitialIndex
    var secondIndex = secondInitialIndex

    while (firstIndex < firstLoaded.size && secondIndex < secondLoaded.size) {
      if (firstLoaded(firstIndex) < secondLoaded(secondIndex)) {
        outputBuffer += firstLoaded(firstIndex)
        firstIndex += 1
      } else {
        outputBuffer += secondLoaded(secondIndex)
        secondIndex += 1
      }

      checkAndWriteClear(outputBuffer, sortedDiskRecordsList)
    }

    (firstIndex, secondIndex)
  }

  private def exhaustDiskRecordsInto(
      diskRecords: DiskRecords,
      buffer: LoadedRecords,
      sorteds: ListBuffer[DiskRecords],
      index: Int
  ): Unit = {
    val loaded = diskRecords.loadAll()

    while (index < diskRecords.size) {
      buffer += loaded(index)
      checkAndWriteClear(buffer, sorteds)
    }
  }

  private def exhaustDiskRecordsQueueInto(
      queue: Queue[DiskRecords],
      sorteds: ListBuffer[DiskRecords]
  ): Unit = {
    while (queue.nonEmpty) {
      val postfix = counter.increment()
      val diskRecords = queue.dequeue()
      sorteds += diskRecords.movedInto(outputDir / "temp" / s"temp.${postfix}")
    }
  }

  private def checkAndWriteClear(
      buffer: LoadedRecords,
      sorteds: ListBuffer[DiskRecords]
  ): Unit = {
    import utils.general.maxPartitionSize

    if (buffer.size == maxPartitionSize)
      writeClear(buffer, sorteds)
  }

  private def writeClear(
      buffer: LoadedRecords,
      sorteds: ListBuffer[DiskRecords]
  ): Unit = {
    val postfix = counter.increment()
    sorteds += buffer.writeIntoAndClear(outputDir / "temp" / s"temp.${postfix}")
  }

  private def diskRecordsMovedIntoOutputDirectory(
      diskRecordsSeq: DiskRecordsSeq
  ): DiskRecordsSeq = {
    for (postfix <- 0 until diskRecordsSeq.size) yield {
      val diskRecords = diskRecordsSeq(postfix)
      diskRecords.movedInto(outputDir / s"partition.${postfix}")
    }
  }
}
