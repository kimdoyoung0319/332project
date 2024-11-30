package worker

import proto.worker._
import common.Block
import scala.util.Failure

class WorkerService(
    inputBlocks: Seq[Block],
    outputDir: os.Path
) extends WorkerServiceGrpc.WorkerService {
  import proto.common.{Empty, RecordMessage}
  import scala.concurrent.{Future, Promise}
  import scala.collection.mutable.ListBuffer
  import utils.globalContext
  import common.{Worker, Record}
  import io.grpc.stub.StreamObserver

  type WorkerWithRange = (Worker, (Vector[Byte], Vector[Byte]))
  type BlockPartition = Map[Int, (Int, Int)]

  val logger = com.typesafe.scalalogging.Logger("worker")
  val receivedBlocks = utils.ThreadSafeMutableList[Block]()
  val sortedBlocks = Promise[Seq[Block]]()
  val workers = Promise[Seq[WorkerWithRange]]()
  val thisId = Promise[Int]()
  val receivedNameAllocator =
    new utils.ThreadSafeNameAllocator(outputDir / "received", "received")

  override def sample(request: Empty): Future[SampleResponse] = Future {
    /* This routine assumes that each blocks is at most 32 MiB. */
    /* TODO: Enforce mimimum sample size. (Currently, the sample size varies
             according to the number of blocks.) */
    val sample =
      for (inputBlock <- inputBlocks) yield inputBlock.sample().toMessage()
    SampleResponse(sample = sample)
  }

  override def inform(request: InformRequest): Future[Empty] = Future {
    import utils.ByteStringExtended
    import utils.PromiseExtended

    val workersWithRange = request.idToWorker.map { case (id, message) =>
      val range = (message.start.toByteVector, message.end.toByteVector)
      val worker = Worker(id = id, ip = message.ip, port = message.port)
      (worker, range)
    }.toSeq

    logger.info(
      s"Worker #${thisId()}: Received worker informations from the master."
    )

    workers.success(workersWithRange)
    Empty()
  }

  override def shuffle(request: Empty): Future[Empty] = {
    import utils.PromiseExtended

    assert(sortedBlocks.isCompleted)

    val blockPartitionMap = partitionAll(sortedBlocks())
    val all =
      for ((worker, _) <- workers())
        yield sendBlockTo(blockPartitionMap)(worker)
    val finished = Future.sequence(all)

    finished.map { _ => Empty() }
  }

  override def sort(request: Empty): Future[Empty] = Future {
    import scala.util.{Success, Failure}

    val sortedNameAllocator =
      new utils.ThreadSafeNameAllocator(outputDir / "sorted", "sorted")

    val blocks = Future.sequence {
      inputBlocks.map { block =>
        Future(block.sorted(sortedNameAllocator.allocate()))
      }
    }

    /* Throwing exception here? */
    blocks.onComplete {
      case Success(sorted) => sortedBlocks.success(sorted)
      case Failure(exception) => throw exception
    }

    Empty()
  }

  override def send(request: SendRequest): Future[Empty] = Future {
    assert(request.size == request.partition.size)

    if (request.size != 0) {
      val records = request.partition.map(Record.fromMessage(_))
      receivedBlocks += Block.fromSeq(records, receivedNameAllocator.allocate())
    }

    Empty()
  }

  override def merge(request: Empty): Future[Empty] = {
    val sorter = new Sorter(receivedBlocks.toSeq, outputDir)
    sorter.runMergingOnly().map { _ => Empty() }
  }

  private def partition(block: Block): BlockPartition = {
    import utils.PromiseExtended
    import utils.RecordRange

    val lastId = workers().size
    val contents = block.contents
    val lastIndex = contents.count()

    var ranges = Seq[(Int, (Int, Int))]()
    var currentId = 0
    var currentIndex = 0

    while (currentId <= lastId) {
      val startIndex = currentIndex
      val currentRange = workers().find(_._1.id == currentId).get._2

      while (
        !currentRange.contains(contents.drop(currentIndex).head)
        && currentIndex <= lastIndex
      )
        currentIndex += 1

      val endIndex = currentIndex
      ranges = ranges.appended((currentId, (startIndex, endIndex)))
    }

    ranges.toMap
  }

  private def partitionAll(blocks: Seq[Block]): Map[Block, BlockPartition] = {
    val partitions = for (block <- blocks) yield (block, partition(block))
    partitions.toMap
  }

  private def sendBlockTo(partitionMap: Map[Block, BlockPartition])(
      worker: Worker
  ): Future[Unit] = {
    import utils.PromiseExtended

    val all = for (block <- sortedBlocks()) yield {
      val range = partitionMap(block)(worker.id)
      val size = range._2 - range._1
      val partition =
        block.contents.slice(range._1, range._2).toSeq.map(_.toMessage())
      val request = SendRequest(size = size, partition)

      worker.stub.send(request)
    }

    Future.sequence(all).map { _ => () }
  }
}

class WorkerServer(
    masterIp: String,
    masterPort: Int,
    inputBlocks: Seq[Block],
    outputDir: os.Path
) {
  import utils.globalContext
  import io.grpc.ServerBuilder
  import scala.concurrent.Future
  import scala.util.{Success, Failure}

  private val service = new WorkerService(inputBlocks, outputDir)
  private val server = utils.makeServer(service)(WorkerServiceGrpc.bindService)
  private val logger = com.typesafe.scalalogging.Logger("worker")

  logger.info(
    s"Worker machine listening port number ${server.getPort()} started."
  )

  register(masterIp, masterPort).onComplete {
    case Success(thisId) =>
      logger.info(
        s"Registration to master machine ${masterIp}:${masterPort} succeeded."
      )
      service.thisId.success(thisId)

    case Failure(exception) =>
      logger.info(
        s"Registration to master machine has failed. Shutting down..."
      )
      server.shutdown()
  }

  private def register(masterIp: String, masterPort: Int): Future[Int] = {
    import proto.master.{RegisterRequest, MasterServiceGrpc}

    val masterStub =
      utils.makeStub(masterIp, masterPort)(MasterServiceGrpc.stub)

    masterStub
      .register(RegisterRequest(ip = utils.thisIp, port = port))
      .map(_.id)
  }

  lazy val port = server.getPort

  def stop(): Unit = server.shutdown()

  def await(): Unit = server.awaitTermination()
}
