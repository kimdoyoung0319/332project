package worker

import proto.worker._

class WorkerService extends WorkerServiceGrpc.WorkerService {
  import proto.common.Empty
  import scala.concurrent.Future
  import utils.globalContext

  override def sample(request: Empty): Future[SampleResponse] = ???
}

class WorkerServer {
  import utils.globalContext
  import io.grpc.ServerBuilder

  private val server =
    utils.makeServer(new WorkerService)(WorkerServiceGrpc.bindService)

  val port = server.getPort

  def await(): Unit = server.awaitTermination()
}
