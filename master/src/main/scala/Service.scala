package master

import proto.master._
import proto.common._

class Service(count: Int, finished: scala.concurrent.Promise[Unit])
    extends MasterServiceGrpc.MasterService {
  import scala.concurrent.{Future, Promise}
  import common.{Worker, Record}
  import utils.concurrent.{global, PromiseCompanionOps, UnitPromiseOps}

  def binded: io.grpc.ServerServiceDefinition =
    proto.master.MasterServiceGrpc.bindService(this, global)

  private val logger = utils.logger.LoggerFactoryUtil.getLogger("master")
  private val workers = utils.concurrent.SafeBuffer[Worker]()
  private val idCounter = utils.concurrent.SafeCounter(0)

  private val allRegistered = Promise.withCallback(informOtherWorkers)
  private val allInformed = Promise.withCallback(startSamplingPhase)
  private val allSampled = Promise.withCallback[Seq[Record]](sendPartitions)
  private val allReceivedPartitions = Promise.withCallback(startSortingPhase)
  private val allSorted = Promise.withCallback(constructIndex)
  private val allConstructedIndex = Promise.withCallback(startShufflingPhase)
  private val allShuffled = Promise.withCallback(startMergingPhase)
  private val allMerged = Promise.withCallback(finish)

  def registerWorker(request: WorkerRegisterInfo) = Future {
    assert(workers.size < count, "Too many workers.")

    val id = idCounter.increment()
    workers += Worker(id, request.ip, request.port)
    if (workers.size == count) {
      allRegistered.success(workers.toSeq)
    }

    logger.info(
      s"Connection with ${request.ip}:${request.port} as ID of ${id} has been established."
    )

    AllocatedId(id)
  }

  private def informOtherWorkers: Unit = {
    import proto.worker.AllWorkers

    val workersInfo = workers.map { worker =>
      WorkerRegisterInfo(worker.id, worker.ip, worker.port)
    }.toSeq
    val request = AllWorkers(count = count, workersInfo)
    val all = Future
      .sequence {
        for (worker <- workers) yield worker.stub.informOthers(request)
      }
      .map { _ => () }

    allInformed.completeWith(all)
  }

  private def startSamplingPhase: Unit = {
    import utils.proto.common.RecordMessageOps

    logger.info("Starting sampling phase...")

    val all = Future.sequence {
      for (worker <- workers) yield worker.stub.demandSample(Empty())
    }

    all.foreach { message =>
      val records =
        for (sampleRecords <- message; recordMessage <- sampleRecords.sample)
          yield recordMessage.toRecord

      allSampled.success(records.toSeq)
    }
  }

  private def sendPartitions(sample: Seq[Record]): Unit = {
    val anchors = calculatePartition(sample)
    /* TODO: Is it able to simplify this boilerplates into a procedure? */
    val all = Future
      .sequence {
        for (worker <- workers)
          yield worker.stub.sendPartitionAnchors(anchors)
      }
      .map { _ => () }

    allReceivedPartitions.completeWith(all)
  }

  private def calculatePartition(
      sample: Seq[Record]
  ): proto.worker.PartitionAnchors = {
    import com.google.protobuf.ByteString
    import utils.general.ByteStringOps

    val max = ByteString.copyFrom(Array.fill(Record.Key.length)((-1).toByte))
    val sorted = sample.sorted
    val span = sample.size / count + 1

    /*
      The anchors are last keys for each workers. For example, let's assume
      that the key values from all samples are [2, 3, 7, 8, 10, 11, 13] and
      there are 3 workers. Then, records within [2, 8) go to the first worker,
      [8, 11) to second, and [11, 13) for last. Hence, the anchor should be
      [8, 11, 13].

      However, there's small issue that samples can be incomplete. i.e. Records
      that lies in [13, 0xFF...FF] might be lost according to above principle.
      Therefore, we enforce the last anchor to be the maximum value for a key,
      which is 0xFF...FF.

      So, the final constructed anchor sequence will be [8, 11, 0xFF...FF], for
      above example.
     */

    val anchors = sorted
      .grouped(span)
      .map(group => ByteString.copyFrom(group.last.key))
      .toSeq
      .dropRight(1)
      .appended(max)

    logger.info(s"The anchors for each workers are...")
    for (anchor <- anchors) logger.info(anchor.toHexString)

    proto.worker.PartitionAnchors(anchors)
  }

  private def startSortingPhase: Unit = {
    val all = Future
      .sequence {
        for (worker <- workers)
          yield worker.stub.startSorting(proto.common.Empty())
      }
      .map { _ => () }

    allSorted.completeWith(all)
  }

  private def constructIndex: Unit = {
    val all = Future
      .sequence {
        for (worker <- workers)
          yield worker.stub.constructFileIndex(proto.common.Empty())
      }
      .map { _ => () }

    allConstructedIndex.completeWith(all)
  }

  private def startShufflingPhase: Unit = {
    val all = Future
      .sequence {
        for (worker <- workers)
          yield worker.stub.startShuffling(proto.common.Empty())
      }
      .map { _ => () }

    allShuffled.completeWith(all)
  }

  private def startMergingPhase: Unit = {
    val all = Future
      .sequence {
        for (worker <- workers)
          yield worker.stub.startShuffling(proto.common.Empty())
      }
      .map { _ => () }

    allMerged.completeWith(all)
  }

  private def finish: Unit = {
    logger.info("The whole procedure finished. The order of the workers is...")

    val all = Future.sequence {
      for (worker <- workers) yield {
        println(s"${worker.ip}")
        logger.info(s"[${worker.id}] ${worker.ip}:${worker.port}")
        worker.stub.finish(request = proto.common.Empty())
      }
    }

    all.foreach { _ => finished.fulfill() }
  }
}
