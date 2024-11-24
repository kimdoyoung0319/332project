package worker

import proto.worker._
import common.Block

class WorkerService(blocks: Seq[Block], client: WorkerClient)
    extends WorkerServiceGrpc.WorkerService {
  import proto.common.{Empty, RecordMessage}
  import scala.concurrent.Future
  import utils.globalContext
  import common.Worker
  import io.grpc.stub.StreamObserver

  var received: Seq[Block] = null
  var idToRange: Map[Int, (Vector[Byte], Vector[Byte])] = null

  override def sample(request: Empty): Future[SampleResponse] = Future {
    val sample = for (block <- blocks) yield block.sample().toMessage()
    SampleResponse(sample = sample)
  }

  override def shuffle(request: ShuffleRequest): Future[Empty] = Future {
    import common.Record

    val workers =
      for ((id, msg) <- request.idToWorker.toSeq)
        yield Worker(id, msg.ip, msg.port)

    /* What happens if the construction is not yet finished, but another worker
       demands records from this? i.e. What happens if another worker calls
       demand() and idToRange is still null? */
    val ranges =
      for ((id, msg) <- request.idToWorker.toSeq) yield {
        assert(msg.start.size == Record.length)
        assert(msg.end.size == Record.length)

        val start = Record(msg.start.toByteArray())
        val end = Record(msg.end.toByteArray())

        (id, start until end)
      }

    idToRange = ranges.toMap
    client.collect(workers).foreach { blocks => received = blocks }

    Empty()
  }

  override def demand(
      request: DemandRequest,
      observer: StreamObserver[RecordMessage]
  ): Unit = {

    import utils.RecordRange

    assert(idToRange != null)

    val id = request.id

    for {
      block <- blocks
      record <- block.contents
      if idToRange(id).contains(record)
    }
      observer.onNext(record.toMessage())

    observer.onCompleted()
  }

  override def sort(request: Empty): Future[Empty] =
    client.sort(received).map { case _ => Empty() }
}

class WorkerServer(blocks: Seq[Block], client: WorkerClient) {
  import utils.globalContext
  import io.grpc.ServerBuilder

  private val server =
    utils.makeServer(new WorkerService(blocks, client))(
      WorkerServiceGrpc.bindService
    )

  val port = server.getPort

  def stop(): Unit = server.shutdown()

  def await(): Unit = server.awaitTermination()
}
