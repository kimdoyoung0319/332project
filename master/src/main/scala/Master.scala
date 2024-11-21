package master

import utils._
import common._
import proto.master._
import proto.worker._
import proto.common._
import scala.concurrent._
import io.grpc._
import com.typesafe.scalalogging._

object Main {
  def main(args: Array[String]): Unit = {
    assert(args.size == 1)

    val count = args(0).toInt
    val server = MasterServer(count)

    server.start()
    server.await()
  }
}

object MasterServer {
  def apply(workerCount: Int): MasterServer = new MasterServer(workerCount)
}

class MasterServer private (workerCount: Int) {
  val service =
    MasterGrpc.bindService(new Master(workerCount), ExecutionContext.global)
  val server = ServerBuilder.forPort(0).addService(service).build
  val logger = Logger("MasterServer")

  def start(): Unit = {
    logger.info("The server has been started.")
    server.start()

    val address = thisAddress
    val port = server.getPort

    println(s"${address}:${port}")
  }

  def await(): Unit = server.awaitTermination()
}

class Master(workerCount: Int) extends MasterGrpc.Master {
  import scala.util.{Success, Failure}

  type WorkerStub = WorkerGrpc.WorkerStub

  object TooManyWorkersException extends Exception

  implicit val context: ExecutionContext = ExecutionContext.global

  var workers: List[WorkerStub] = Nil
  val logger = Logger("Master")
  val registerFinished = Promise[Unit]()
  val sampleFinished = Promise[Unit]()

  registerFinished.future.map { case _ =>
    logger.info("All connections are established.")
    val li = for (worker <- workers) yield worker.sample(Empty())
    Future.sequence(li) foreach { case _ => sampleFinished.success(()) }
  }

  override def register(request: RegisterRequest): Future[Empty] = Future {
    val (address, port) = (request.address, request.port)

    logger.info(
      s"Register request has been sent from ${address}:${port}."
    )

    val channel = ManagedChannelBuilder
      .forAddress(address, port)
      .usePlaintext()
      .build()
    val stub = WorkerGrpc.stub(channel)

    workers = workers.appended(stub)

    if (workers.size == workerCount)
      registerFinished.complete(Success(()))
    else if (workers.size > workerCount)
      throw TooManyWorkersException

    Empty()
  }
}
