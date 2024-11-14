import java.util.concurrent.TimeUnit
import io.grpc.{ManagedChannelBuilder, ManagedChannel}
import scala.concurrent.{Await, Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Try, Success, Failure}
import example.compute.{ComputeRequest, ComputeReply, RemoteComputeGrpc}
import example.compute.RemoteComputeGrpc.RemoteComputeStub

object Master {
  val workers =
    Seq(
      (1, "2.2.2.101", 50051),
      (2, "2.2.2.102", 50052),
      (3, "2.2.2.103", 50053)
    )

  /* Makes new master object. */
  def apply(id: Int, host: String, port: Int): Master = {
    val channel =
      ManagedChannelBuilder.forAddress(host, port).usePlaintext().build
    val stub = RemoteComputeGrpc.stub(channel)
    new Master(id, channel, stub)
  }

  /* Runs master client. */
  def run(first: Int, second: Int): Unit = {
    val clients = for (worker <- workers) yield {
      val (id, host, port) = worker
      Master(id, host, port)
    }
    val results =
      for (client <- clients)
        yield client.assignComputation(first, second)
    val all = Future.sequence(results)

    all andThen {
      case Success(results) =>
        results sortBy (_._2.result) foreach { case (id, reply) =>
          println(s"id: ${id}, result: ${reply.result}")
        }
      case Failure(exception) =>
        println("RPC failed with!")
    }

    Await.ready(all, 5.seconds)
  }
}

class Master private (
    private val id: Int,
    private val channel: ManagedChannel,
    private val stub: RemoteComputeStub
) {
  def shutdown(): Unit = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  /* Assigns computation with 'first' and 'second' operands. */
  def assignComputation(
      first: Int,
      second: Int
  ): Future[(Int, ComputeReply)] = {
    val request = ComputeRequest(first = first, second = second)
    stub.askResult(request) transform {
      case Success(reply)     => Success((id, reply))
      case Failure(exception) => Failure(exception)
    }
  }
}
