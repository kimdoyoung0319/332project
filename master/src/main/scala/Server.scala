package master

/* TODO: Clean up global namespace of this module. */
import proto.master._
import proto.worker.SampleResponse
import proto.worker.ShuffleRequest

class MasterService(workerCount: Int) extends MasterServiceGrpc.MasterService {
  import proto.common.Empty
  import proto.worker.SampleResponse
  import scala.concurrent.{Future, Promise}
  import utils.globalContext
  import common.Worker

  /* TODO: Rewrite this using Future. */
  var workers: Seq[Worker] = Nil
  var nextId: Int = 0

  val logger = com.typesafe.scalalogging.Logger("master")
  val registration = Promise[Unit]()
  val sampling = Promise[Seq[SampleResponse]]()
  val shuffling = Promise[Unit]()
  val sorting = Promise[Unit]()

  override def register(request: RegisterRequest): Future[RegisterReply] =
    Future {
      import proto.worker.WorkerServiceGrpc

      if (workers.size >= workerCount)
        throw new IllegalStateException

      val (ip, port) = (request.ip, request.port)
      val worker = Worker(nextId, ip, port)
      workers = workers.appended(worker)
      nextId += 1

      if (workers.size == workerCount)
        registration.success(())

      logger.info(s"Connection with ${ip}:${port} has been established.")

      RegisterReply(worker.id)
    }
}

class MasterServer(workerCount: Int) {
  import common.Record
  import utils.globalContext
  import scala.concurrent.Future
  import proto.common.Empty

  private val logger = com.typesafe.scalalogging.Logger("master")
  private val service = new MasterService(workerCount)
  private val server = utils.makeServer(service)(MasterServiceGrpc.bindService)

  logger.info(s"Starting master server at ${utils.thisIp}:${server.getPort}...")

  println(s"${utils.thisIp}:${server.getPort}")

  /* TODO: Add invariants for each completion of phases. */
  /* Registration phase callback function. */
  service.registration.future.foreach { case _ =>
    logger.info("All workers have established connections.")

    val all = for (worker <- service.workers) yield {
      println(s"Worker #${worker.id}: ${worker.ip}:${worker.port}.")
      worker.stub.sample(Empty())
    }
    service.sampling.completeWith(Future.sequence(all))
  }

  /* Sampling phase callback function. */
  service.sampling.future.foreach { responses =>
    import proto.worker.ShuffleRequest
    import scala.util.{Success, Failure}

    logger.info("All samples have been collected. ")

    val samples = responses.toRecords()
    val partitions = partition(samples)
    val request = ShuffleRequest(partitions)
    val all = for (worker <- service.workers) yield worker.stub.shuffle(request)

    Future.sequence(all).onComplete {
      case Success(_) => service.shuffling.success(())
      case Failure(e) => throw e
    }
  }

  /* Shuffling phase callback function. */
  service.shuffling.future.foreach { case _ =>
    logger.info("Shuffling phase has been finished.")

    val all = for (worker <- service.workers) yield worker.stub.sort(Empty())

    Future.sequence(all).foreach { case _ => service.sorting.success(()) }
  }

  /* Sorting phase callback function. */
  service.sorting.future.foreach { case _ =>
    logger.info(
      "The whole procedure has been finished. The order of the workers is..."
    )

    /* According to partition() method, the order of IDs is identical with
       the order of the records in the worker. This routine assumes that the
       IDs in range [0, workerCount) are all assigned. */
    for (id <- 0 until workerCount) {
      val worker = service.workers.find(_.id == id).get

      printf(s"${worker.ip} ")
      logger.info(s"${worker.id}: ${worker.ip}")
    }

    server.shutdown()
  }

  def await(): Unit = server.awaitTermination()

  implicit class Responses(responses: Seq[SampleResponse]) {
    def toRecords(): Seq[Record] =
      for (response <- responses; message <- response.sample)
        yield Record.fromMessage(message)
  }

  private def partition(
      samples: Seq[Record]
  ): Map[Int, proto.worker.ShuffleRequest.WorkerMessage] = {

    import proto.worker.ShuffleRequest.WorkerMessage
    import com.google.protobuf.ByteString
    import utils.ByteStringExtended
    import utils.ByteVectorExtended

    /* This routine assumes that all IDs of the workers are not duplicated and
       have consecutive values. i.e. There must not be any unassigned ID values
       within the ID range. */
    val sorted = samples.sorted
    val offset = samples.size / workerCount
    val seq = for (worker <- service.workers) yield {
      import utils.{maxKey, minKey}

      /* TODO: Refactor this. */
      val (start, end) = worker.id match {
        case 0 => (minKey.toByteString(), sorted(offset).key.toByteString())
        case id if id == (workerCount - 1) =>
          (sorted(worker.id * offset).key.toByteString(), maxKey.toByteString())
        case id =>
          (
            sorted(worker.id * offset).key.toByteString(),
            sorted((worker.id + 1) * offset).key.toByteString()
          )
      }

      logger.info(
        s"Partition for ${worker.id} is [${start.toHexString}, ${end.toHexString})."
      )

      worker.toMapping(start, end)
    }

    seq.toMap
  }

}
