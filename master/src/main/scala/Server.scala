package master
import scala.util.Failure
import org.scalactic.Fail

class MasterService(workerCount: Int)
    extends proto.master.MasterServiceGrpc.MasterService {
  import proto.master._
  import proto.common.Empty
  import proto.worker.SampleResponse
  import scala.concurrent.{Future, Promise}
  import utils.globalContext
  import collection.mutable.ListBuffer
  import common.Worker

  val workers = ListBuffer[Worker]()
  var nextId: Int = 0

  val logger = com.typesafe.scalalogging.Logger("master")

  val registration = Promise[Unit]()
  val sampling = Promise[Seq[SampleResponse]]()
  val shuffling = Promise[Unit]()
  val sorting = Promise[Unit]()
  val merging = Promise[Unit]()

  /* TODO: Rewrite this in thread-safe manner. */
  override def register(request: RegisterRequest): Future[RegisterReply] =
    Future {
      import proto.worker.WorkerServiceGrpc

      if (workers.size >= workerCount)
        throw new IllegalStateException

      val (ip, port) = (request.ip, request.port)
      val worker = Worker(nextId, ip, port)
      workers += worker
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
  import scala.util.{Success, Failure}
  import proto.common.Empty
  import proto.master._
  import proto.worker.SampleResponse

  private val logger = com.typesafe.scalalogging.Logger("master")
  private val service = new MasterService(workerCount)
  private val server = utils.makeServer(service)(MasterServiceGrpc.bindService)

  logger.info(s"Starting master server at ${utils.thisIp}:${server.getPort}...")

  println(s"${utils.thisIp}:${server.getPort}")

  /* TODO: Add invariants for each completion of phases. */
  /* Registration phase callback function. */
  service.registration.future.foreach { _ =>
    import scala.util.{Success, Failure}

    assert(service.workers.size == workerCount)
    logger.info("All workers have established connections.")

    val all = for (worker <- service.workers) yield worker.stub.sample(Empty())

    Future.sequence(all.toSeq).onComplete {
      case Success(value) => service.sampling.success(value)
      case Failure(exception) => throw exception
    }
  }

  /* Sampling phase callback function. */
  service.sampling.future.foreach { responses =>
    import proto.worker.InformRequest
    import scala.util.{Success, Failure}

    val samples =
      for (response <- responses; message <- response.sample)
        yield Record.fromMessage(message)

    val idToWorker = partition(samples)

    val allInformed = Future.sequence {
      for (worker <- service.workers)
        yield worker.stub.inform(InformRequest(idToWorker))
    }

    /* TODO: Is there any way to refactor this? */
    allInformed.foreach { _ =>
      val allSorted = Future.sequence {
        for (worker <- service.workers) yield worker.stub.sort(Empty())
      }

      logger.info("Informed worker informations to all workers.")
      allSorted.onComplete {
        case Success(_) => service.sorting.success(())
        case Failure(exception) => throw exception
      }
    }
  }

  /* Sorting phase callback function. */
  service.sorting.future.foreach { _ =>
    val all = Future.sequence {
      for (worker <- service.workers) yield worker.stub.shuffle(Empty())
    }

    all.onComplete {
      case Success(_) => service.shuffling.success(())
      case Failure(exception) => throw exception
    }
  }

  /* Shuffling phase callback function. */
  service.shuffling.future.foreach { _ =>
    val all = for (worker <- service.workers) yield worker.stub.merge(Empty())

    Future.sequence(all).onComplete {
      case Success(_) => service.merging.success(())
      case Failure(exception) => throw exception
    }
  }

  /* Merging phase callback function. */
  service.merging.future.foreach { _ =>
    logger.info(
      "All procedure has been finished. The order of the workers is..."
    )

    for (worker <- service.workers.sortBy(_.id)) {
      println(worker.ip)
      logger.info(worker.ip)
    }

    logger.info("Shutting down...")
  }

  def await(): Unit = server.awaitTermination()

  implicit class Responses(responses: Seq[SampleResponse]) {
    def toRecords(): Seq[Record] =
      for (response <- responses; message <- response.sample)
        yield Record.fromMessage(message)
  }

  private def partition(
      samples: Seq[Record]
  ): Map[Int, proto.worker.InformRequest.WorkerMessage] = {

    import proto.worker.InformRequest.WorkerMessage
    import com.google.protobuf.ByteString
    import utils.{ByteStringExtended, ByteVectorExtended}

    def allIdAllocated: Boolean =
      (0 until workerCount).forall { id => service.workers.exists(_.id == id) }

    assert(
      allIdAllocated,
      s"All IDs from 0 to ${workerCount} should be allocated to some worker."
    )

    val sorted = samples.sorted
    val offset = samples.size / workerCount
    val seq =
      for (worker <- service.workers) yield {
        import utils.{maxKeyString, minKeyString}

        val lastId = workerCount - 1
        def idKeyString(id: Int): ByteString =
          sorted(id * offset).key.toByteString()

        val (start, end) = worker.id match {
          case 0 => (minKeyString, idKeyString(1))
          case id if id == lastId => (idKeyString(id), maxKeyString)
          case id => (idKeyString(id), idKeyString(id + 1))
        }

        logger.info(
          s"Partition for ${worker.id} is [${start.toHexString}, ${end.toHexString})."
        )

        val workerMessage =
          WorkerMessage(ip = worker.ip, port = worker.port, start, end)
        (worker.id, workerMessage)
      }

    seq.toMap
  }
}
