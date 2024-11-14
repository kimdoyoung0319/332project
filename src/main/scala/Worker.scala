import scala.concurrent.{Future, ExecutionContext}
import scala.util.Random
import io.grpc.{Server, ServerBuilder}
import example.compute.{RemoteComputeGrpc, ComputeRequest, ComputeReply}

object Worker {
  /* Runs worker. */
  def run(port: Int): Unit = {
    val listener = new Listener(ExecutionContext.global)
    listener.listen(port)
    listener.await()
  }
}

class Listener(executionContext: ExecutionContext) {
  private[this] var server: Server = null
  private val random = new Random

  /* Listens on 'port', redirecting any request to RemoteComputeImpl. */
  def listen(port: Int): Unit = {
    val service =
      RemoteComputeGrpc.bindService(new RemoteComputeImpl, executionContext)

    server = ServerBuilder.forPort(port).addService(service).build.start
    println(s"Server listening to ${port} has been started.")
  }

  /* Awaits for the server to terminate. */
  def await(): Unit = {
    if (server != null)
      server.awaitTermination()
  }

  /* Stops the server. */
  private def stop(): Unit = {
    if (server != null)
      server.shutdown()
  }

  private class RemoteComputeImpl extends RemoteComputeGrpc.RemoteCompute {
    /* Remote procedure to be called by the master. */
    override def askResult(request: ComputeRequest): Future[ComputeReply] = {
      val operation = random.nextInt(4)
      val result = operation match {
        case 0 => request.first + request.second
        case 1 => request.first - request.second
        case 2 => request.first * request.second
        case 3 => request.first / request.second
      }
      val reply = ComputeReply(result = result)
      Future.successful(reply)
    }
  }
}
