package worker

import proto.worker._
import common.Block

class WorkerService(blocks: Seq[Block])
    extends WorkerServiceGrpc.WorkerService {
  import proto.common.Empty
  import scala.concurrent.Future
  import utils.globalContext

  override def sample(request: Empty): Future[SampleResponse] = Future {
    val sample = for (block <- blocks) yield block.sample().toMessage()
    SampleResponse(sample = sample)
  }

  /* TODO: Implement shuffling between workers. */
  override def shuffle(request: ShuffleRequest): Future[Empty] = ???
}

class WorkerServer(blocks: Seq[Block]) {
  import utils.globalContext
  import io.grpc.ServerBuilder

  private val server =
    utils.makeServer(new WorkerService(blocks))(WorkerServiceGrpc.bindService)

  val port = server.getPort

  def stop(): Unit = server.shutdown()

  def await(): Unit = server.awaitTermination()
}
