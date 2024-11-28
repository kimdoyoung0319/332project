package worker

import java.io.FilenameFilter
import utils.FileNameAllocator

class WorkerClient(masterIp: String, masterPort: Int, outputDir: os.Path) {
  import proto.master._
  import proto.common.{Empty, RecordMessage}
  import scala.concurrent.{Future, Promise}
  import common.{Worker, Record, Block}
  import io.grpc.stub.StreamObserver

  var id: Int = -1

  private val stub =
    utils.makeStub(masterIp, masterPort)(MasterServiceGrpc.stub)
  private val logger = com.typesafe.scalalogging.Logger("worker")

  def register(port: Int): Future[Int] = {
    import utils.globalContext

    stub.register(RegisterRequest(ip = utils.thisIp, port = port)).map {
      case reply => id = reply.id; reply.id
    }
  }

  def collect(workers: Seq[Worker]): Future[Seq[Block]] = {
    import utils.globalContext
    import proto.worker.DemandRequest

    assert(id != -1)

    val allocator = new FileNameAllocator(outputDir / "temp", "temp")
    val all = for (worker <- workers) yield {
      val reception = Promise[Seq[Block]]()
      val observer = new RecordsObserver(reception, allocator)

      logger.info(
        s"Worker #${id}: Starting shuffling with ${worker.id}..."
      )

      worker.stub.demand(DemandRequest(id), observer)
      reception.future
    }

    for (done <- all)
      done.foreach { blocks =>
        logger.info(
          s"Worker #${id}: Shuffling with unknown worker finished. The received blocks are..."
        )
        for (block <- blocks)
          logger.info(block.toString)
      }

    /* Maps sequence of sequence of blocks into a single sequence of blocks. */
    Future.sequence(all).map { seqs =>
      for (seq <- seqs; block <- seq) yield block
    }
  }

  def sort(inputs: Seq[Block]): Future[Unit] = {
    import utils.globalContext
    val sorter = new Sorter(inputs, outputDir)

    sorter.run().map { blocks =>
      logger.info("Sorting finished. The path for the output blocks are...")
      for (block <- blocks)
        logger.info(block.path.toString())
    }
  }

  class RecordsObserver(
      reception: Promise[Seq[Block]],
      allocator: utils.FileNameAllocator
  ) extends StreamObserver[RecordMessage] {

    import common.Block

    val maxElems = Block.size / Record.length
    /* TODO: Rewrite this using scala.collections.mutable.Buffer. */
    var buffer: Array[Record] = Array()
    var blocks: Seq[Block] = Nil

    def onNext(msg: RecordMessage): Unit = {
      buffer = buffer :+ Record.fromMessage(msg)

      if (buffer.size == maxElems) {
        blocks = blocks :+ Block.fromArr(buffer, allocator.allocate())
        buffer = Array()
      }
    }

    def onCompleted(): Unit = {
      blocks = blocks :+ Block.fromArr(buffer, allocator.allocate())
      reception.success(blocks)
    }

    def onError(exception: Throwable) = reception.failure(exception)
  }
}
