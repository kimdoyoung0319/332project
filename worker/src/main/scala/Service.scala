/* TODO: Clean up output directory after all procedure. */
package worker

import proto.worker._
import proto.common._

class Service(
    inputs: Seq[common.DiskRecords],
    outputDir: os.Path,
    masterIp: String,
    masterPort: Int,
    finished: scala.concurrent.Promise[Unit]
) extends WorkerServiceGrpc.WorkerService {
  import scala.concurrent.{Future, Promise}
  import scala.collection.mutable.Queue
  import utils.concurrent.{global, PromiseOps, FutureCompanionOps}
  import common.{DiskRecords, LoadedRecords, Worker}

  private val logger = utils.logger.LoggerFactoryUtil.getLogger("worker")
  private val masterStub = utils.grpc.makeMasterStub(masterIp, masterPort)

  def binded: io.grpc.ServerServiceDefinition =
    proto.worker.WorkerServiceGrpc.bindService(this, global)

  def registerToMaster(thisPort: Int): Unit = {
    import proto.master._

    val masterInformedId = Promise[AllocatedId]().completeWith {
      val request =
        WorkerRegisterInfo(ip = utils.network.thisIp, port = thisPort)
      masterStub.registerWorker(request)
    }

    masterInformedId.future.foreach { reply =>
      receivedThisId.success(reply.id)
    }
  }

  private val receivedThisId = Promise[Int]()
  private val receivedCount = Promise[Int]()
  private val receivedOtherWorkers = Promise[Seq[Worker]]()
  private val receivedPartitions = Promise[Seq[Array[Byte]]]()
  private val hasSortedDiskRecords = Promise[Seq[DiskRecords]]()
  private val hasConstructedIndices =
    Promise[Map[Int, Queue[DiskRecordsIndex]]]()

  private lazy val thisId = receivedThisId.value
  private lazy val count = receivedCount.value
  private lazy val workers = receivedOtherWorkers.value
  private lazy val partitions = receivedPartitions.value
  private lazy val sortedDiskRecords = hasSortedDiskRecords.value
  private lazy val workerToIndices = hasConstructedIndices.value

  def informOthers(request: AllWorkers): Future[Empty] = Future {
    assert(
      request.count == request.workers.size,
      "The number of the workers should be same with the received sequence length."
    )

    val workers = request.workers.map { message =>
      Worker(message.id, message.ip, message.port)
    }

    logger.info(
      s"[${thisId}] Received informations about other workers. The received list of workers is..."
    )
    for (worker <- workers)
      logger.info(s"[${thisId}] [${worker.id}] ${worker.ip}:${worker.port}")

    receivedCount.success(request.count)
    receivedOtherWorkers.success(workers)

    Empty()
  }

  def demandSample(request: Empty): Future[SampleRecords] = {
    logger.info(
      s"[${thisId}] Received sample request from the master machine."
    )

    /* TODO: Modify this to conform with larger test cases. */
    val recordsPerInput = 100
    val sample =
      for (input <- inputs; record <- input.grabSample(recordsPerInput))
        yield record.toMessage

    Future(SampleRecords(sample))
  }

  def sendPartitionAnchors(request: PartitionAnchors): Future[Empty] = {
    import utils.general.ByteArrayOps

    val anchors = request.anchors.map(_.toByteArray)

    logger.info(
      s"[${thisId}] Received partition anchors from the master machine. The anchors are..."
    )
    for (anchor <- anchors)
      logger.info(
        s"[${thisId}] ${anchor.toHexString}"
      )
    receivedPartitions.success(anchors)
    Future(Empty())
  }

  def startSorting(request: Empty): Future[Empty] = {
    val counter = utils.concurrent.SafeCounter(0)
    val manager = new utils.memory.MemoryManager
    val sortSingle = sortSingleDiskRecords(counter, manager)(_)
    val allInputSorted = Future.sequence {
      for (input <- inputs) yield sortSingle(input)
    }

    allInputSorted.map { diskRecords =>
      hasSortedDiskRecords.success(diskRecords)
      Empty()
    }
  }

  def constructFileIndex(request: Empty): Future[Empty] = Future {
    hasConstructedIndices.success(makeIndex())
    Empty()
  }

  def startShuffling(request: Empty): Future[Empty] = {
    val manager = new utils.memory.MemoryManager
    val counter = utils.concurrent.SafeCounter(0)
    val collect = collectPartitionFrom(counter, manager)(_)

    Future
      .sequence {
        for (worker <- workers) yield collect(worker)
      }
      .map { _ => Empty() }
  }

  def requestPartition(request: ThisId): Future[PartitionedRecords] = Future {
    assert(
      hasConstructedIndices.isCompleted,
      "The worker machine must have constructed indices for each other worker machines."
    )

    if (workerToIndices(request.id).nonEmpty) {
      val (file, (start, end)) = workerToIndices(request.id).dequeue()
      val loaded = file.load(start, end - start)
      val partition = loaded.contents.map(_.toMessage).toSeq

      logger.info(
        s"[${thisId}] [${request.id}] Received partition request. Sending partition of size ${partition.size}"
      )

      PartitionedRecords(partition)
    } else {
      PartitionedRecords(Nil)
    }
  }

  def startMerging(request: Empty): Future[Empty] = {
    import utils.concurrent.FutureCompanionOps

    val concurrency = 10
    val counter = utils.concurrent.SafeCounter(0)
    val manager = new utils.memory.MemoryManager
    val queue =
      utils.concurrent.SafeQueue(collectedPartitions.map(Seq(_)).toSeq)

    def mergeWorker = merge(counter, queue, manager)
    /* TODO: Refactor this. */
    val all = Future.forall {
      for (_ <- 0 until concurrency) yield mergeWorker
    }

    all.map { _ => Empty() }
  }

  def finish(request: Empty): Future[Empty] = Future {
    import utils.concurrent.UnitPromiseOps

    finished.fulfill()
    Empty()
  }

  private def sortSingleDiskRecords(
      counter: utils.concurrent.SafeCounter,
      manager: utils.memory.MemoryManager
  )(records: DiskRecords): Future[DiskRecords] = {
    import utils.concurrent.UnitFutureOps

    manager.ensured(records.sizeInByte) {
      val loaded = records.loadAll()
      val prefix = counter.increment()

      loaded.sort()
      loaded.writeInto(outputDir / "sorted" / s"sorted.${prefix}")
    }
  }

  type DiskRecordsIndex = (DiskRecords, (Int, Int))

  private def makeIndex(): Map[Int, Queue[DiskRecordsIndex]] = {
    val indices =
      for (diskRecord <- sortedDiskRecords)
        yield makeIndexForSingleFile(diskRecord)
    val transposed = indices.transpose
    val queue = transposed.map(Queue.from(_))
    val result = queue.indices.zip(queue).toMap

    logger.info(
      s"[$thisId] Constructed indices for each files. The indices are..."
    )
    for (worker <- workers; (file, (start, end)) <- result(worker.id)) {
      logger.info(
        s"[${thisId}] [${worker.id}] ${file.path}: [${start}, ${end})"
      )
    }

    result
  }

  private def makeIndexForSingleFile(
      diskRecords: DiskRecords
  ): Seq[DiskRecordsIndex] = {
    import utils.general.ByteArrayOrdering.compare

    val records = diskRecords.loadAll().contents
    val length = records.length
    var index = 0

    for (id <- 0 until count) yield {
      val start = index
      val anchor = partitions(id)

      while (index < length && compare(records(index).key, anchor) < 0)
        index += 1

      val end = index
      (diskRecords, (start, end))
    }
  }

  val collectedPartitions = utils.concurrent.SafeBuffer[DiskRecords]()

  private def collectPartitionFrom(
      counter: utils.concurrent.SafeCounter,
      manager: utils.memory.MemoryManager
  )(worker: Worker): Future[Unit] = Future.repeat {
    val request = proto.worker.ThisId(thisId)
    val append = appendIntoCollected(counter)(_)

    manager.requiring(utils.general.maxPartitionSize) {
      worker.stub.requestPartition(request).map {
        case PartitionedRecords(Nil, _) =>
          logger
            .info(s"[${thisId}] [${worker.id}] Finished requesting partitions.")
          false
        case PartitionedRecords(partition, _) =>
          logger
            .info(s"[${thisId}] [${worker.id}] Received a partition.")
          append(partitionToLoaded(partition))
          true
      }
    }
  }

  private def partitionToLoaded(
      partition: Seq[RecordMessage]
  ): LoadedRecords = {
    import common.Record

    val source = partition.map(message => Record(message.record))
    LoadedRecords(source)
  }

  private def appendIntoCollected(
      counter: utils.concurrent.SafeCounter
  )(loaded: LoadedRecords): Unit = {
    import utils.proto.worker.PartitionedRecordsOps

    val prefix = counter.increment()
    val file =
      loaded.writeInto(outputDir / "received" / s"received.${prefix}")

    collectedPartitions += file
  }

  private def merge(
      counter: utils.concurrent.SafeCounter,
      queue: utils.concurrent.SafeQueue[Seq[DiskRecords]],
      manager: utils.memory.MemoryManager
  ): Future[Unit] = Future.repeat {
    import scala.concurrent.Await
    import scala.concurrent.duration._

    if (queue.size >= 2) {
      mergeTwoDiskFiles(counter, queue, manager).map { _ => true }
    } else
      Future(false)
  }

  private def mergeTwoDiskFiles(
      counter: utils.concurrent.SafeCounter,
      queue: utils.concurrent.SafeQueue[Seq[DiskRecords]],
      manager: utils.memory.MemoryManager
  ): Future[Unit] = {
    import scala.collection.mutable.ListBuffer
    import utils.concurrent.UnitFutureOps
    import utils.general.maxPartitionSize

    def writeAndAppend = writeIntoTargetAndAppendWith(counter)(_, _)

    manager.ensured(maxPartitionSize * 2) {
      val q1 = Queue.from(queue.dequeue())
      val q2 = Queue.from(queue.dequeue())
      val merged = ListBuffer[DiskRecords]()

      while (q1.nonEmpty && q2.nonEmpty) {
        val target = LoadedRecords()

        /* TODO: This is problematic... */
        val file1 = q1.dequeue()
        val file2 = q2.dequeue()

        logger.info(s"[${thisId}] Merging ${file1.path} with ${file2.path}.")

        val arr1 = file1.loadAll().contents
        val arr2 = file2.loadAll().contents

        var i1 = 0
        var i2 = 0

        while (i1 < arr1.length && i2 < arr2.length) {
          if (arr1(i1) > arr2(i2)) {
            target += arr1(i1)
            i1 += 1
          } else {
            target += arr2(i2)
            i2 += 1
          }

          if (target.sizeInByte == maxPartitionSize)
            writeAndAppend(target, merged)
        }

        while (i1 < arr1.length) {
          target += arr1(i1)
          i1 += 1

          if (target.sizeInByte == maxPartitionSize)
            writeAndAppend(target, merged)
        }

        while (i2 < arr2.length) {
          target += arr2(i2)
          i2 += 1

          if (target.sizeInByte == maxPartitionSize)
            writeAndAppend(target, merged)
        }

        if (target.nonEmpty)
          writeAndAppend(target, merged)
      }

      queue.enqueue(merged.toSeq)
    }
  }

  private def writeIntoTargetAndAppendWith(
      counter: utils.concurrent.SafeCounter
  )(
      target: LoadedRecords,
      written: scala.collection.mutable.ListBuffer[DiskRecords]
  ): Unit = {
    val prefix = counter.increment()
    target.writeIntoAndClear(outputDir / s"partition.${prefix}")
  }
}
