package master

import proto.master._
import proto.worker.SampleResponse
import proto.worker.ShuffleRequest

class MasterService(workerCount: Int) extends MasterServiceGrpc.MasterService {
  import proto.common.Empty
  import proto.worker.{SampleResponse, WorkerServiceGrpc}
  import scala.concurrent.{Future, Promise}
  import utils.globalContext

  case class Worker(
      val id: Int,
      val ip: String,
      val port: Int,
      val stub: WorkerServiceGrpc.WorkerServiceStub
  )

  var workers: Seq[Worker] = Nil
  var nextId: Int = 0

  val logger = com.typesafe.scalalogging.Logger("master")
  val registration = Promise[Unit]()
  val sampling = Promise[Seq[SampleResponse]]()
  val shuffling = Promise[Unit]()

  override def register(request: RegisterRequest): Future[Empty] = Future {
    import proto.worker.WorkerServiceGrpc

    if (workers.size >= workerCount)
      throw new IllegalStateException

    val (ip, port) = (request.ip, request.port)
    val stub = utils.makeStub(ip, port)(WorkerServiceGrpc.stub)

    workers = workers.appended(Worker(nextId, ip, port, stub))
    nextId += 1

    if (workers.size == workerCount)
      registration.success(())

    logger.info(s"Connection with ${ip}:${port} has been established.")

    Empty()
  }
}

class MasterServer(workerCount: Int) {
  import common.Record
  import utils.globalContext

  private val logger = com.typesafe.scalalogging.Logger("master")
  private val service = new MasterService(workerCount)
  private val server = utils.makeServer(service)(MasterServiceGrpc.bindService)

  logger.info(s"Starting master server at ${utils.thisIp}:${server.getPort}...")

  println(s"${utils.thisIp}:${server.getPort}")

  service.registration.future.foreach { case _ =>
    import proto.common.Empty
    import scala.concurrent.Future

    logger.info("All workers have established connections.")

    val all = for (worker <- service.workers) yield worker.stub.sample(Empty())
    service.sampling.completeWith(Future.sequence(all))
  }

  service.sampling.future.foreach { responses =>
    import proto.common.Empty
    import scala.concurrent.Future
    import proto.worker.ShuffleRequest

    logger.info("All samples have been collected. ")

    val samples = responses.toRecords()
    val partitions = partition(samples)
    val request = ShuffleRequest(partitions)
    val all = for (worker <- service.workers) yield worker.stub.shuffle(request)

    all.foreach { case _ => service.shuffling.success(()) }
  }

  def await(): Unit = server.awaitTermination()

  implicit class Responses(responses: Seq[SampleResponse]) {
    def toRecords(): Seq[Record] =
      for (response <- responses; message <- response.sample)
        yield Record.fromMessage(message)
  }

  private def partition(
      samples: Seq[Record]
  ): Map[Int, proto.worker.ShuffleRequest.WorkerInfos] = {

    import proto.worker.ShuffleRequest.WorkerInfos
    import common.Bytes

    val sorted = samples.sorted
    val offset = samples.size / workerCount
    val seq = for (worker <- service.workers) yield {
      val key = sorted(worker.id * offset).key.toByteString()
      val workerInfo = WorkerInfos(worker.ip, worker.port, key)

      (worker.id, workerInfo)
    }

    seq.toMap
  }

}
