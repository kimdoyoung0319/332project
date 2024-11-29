package worker

import proto.worker._
import common.Block

class WorkerService(
    inputBlocks: Seq[Block],
    client: WorkerClient,
    outputDir: os.Path
) extends WorkerServiceGrpc.WorkerService {
  import proto.common.{Empty, RecordMessage}
  import scala.concurrent.Future
  import scala.collection.mutable.ListBuffer
  import utils.globalContext
  import common.{Worker, Record}
  import io.grpc.stub.StreamObserver

  val receivedBlocks = utils.ThreadSafeMutableList[Block]()
  /* TODO: Change this to use val. */
  var idToRange: Map[Int, (Vector[Byte], Vector[Byte])] = null
  val receivedNameAllocator =
    new utils.ThreadSafeNameAllocator(outputDir / "received", "received")

  val logger = com.typesafe.scalalogging.Logger("worker")

  override def sample(request: Empty): Future[SampleResponse] = Future {
    /* This routine assumes that each blocks are at most 32 MiB. */
    /* TODO: Enforce mimimum sample size. (Currently, the sample size varies
             according to the number of blocks.) */
    val sample = for (block <- blocks) yield block.sample().toMessage()
    SampleResponse(sample = sample)
  }

  override def shuffle(request: ShuffleRequest): Future[Empty] = {
    import common.Record
    import scala.concurrent.Promise
    import utils.{ByteStringExtended, ByteVectorExtended}

    logger.info(s"Worker #${client.id}: Starting shuffling phase...")

    val workers =
      for ((id, msg) <- request.idToWorker.toSeq)
        yield Worker(id, msg.ip, msg.port)

    /* TODO: What happens if the construction is not yet finished, but another
       worker demands records from this? i.e. What happens if another worker
       calls demand() and idToRange is still null? */
    val ranges =
      for ((id, msg) <- request.idToWorker.toSeq) yield {
        assert(msg.start.size == 10)
        assert(msg.end.size == 10)

        val start = msg.start.toByteVector
        val end = msg.end.toByteVector

        (id, start until end)
      }

    idToRange = ranges.toMap

    val done = Promise[Empty]()
    client.collect(workers).foreach { blocks =>
      blocks.foreach { receivedBlocks += _ }; done.success(Empty())
    }

    done.future
  }

  override def demand(
      request: DemandRequest,
      observer: StreamObserver[RecordMessage]
  ): Unit = {

    import utils.RecordRange

    assert(idToRange != null)

    /* TODO: Change this to send individual files separately. (Streaming causes
             OOM!) */
    for {
      block <- blocks; record <- block.contents
      if idToRange(request.id).contains(record)
    }
      observer.onNext(record.toMessage())

    observer.onCompleted()
  }

  override def sort(request: Empty): Future[Empty] =
    client.sort(receivedBlocks.toSeq).map { case _ => Empty() }

  override def send(request: SendRequest): Future[Empty] = Future {
    assert(request.size == request.partition.size)

    val records = request.partition.map(Record.fromMessage(_))
    receivedBlocks += Block.fromSeq(records, receivedNameAllocator.allocate())

    Empty()
  }

  def merge(request: Empty): Future[Empty] = ???
}

class WorkerServer(
    blocks: Seq[Block],
    client: WorkerClient,
    outputDir: os.Path
) {
  import utils.globalContext
  import io.grpc.ServerBuilder

  private val server =
    utils.makeServer(new WorkerService(blocks, client, outputDir))(
      WorkerServiceGrpc.bindService
    )

  val port = server.getPort

  def stop(): Unit = server.shutdown()

  def await(): Unit = server.awaitTermination()
}
