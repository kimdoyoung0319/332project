package worker

class WorkerClient(masterIp: String, masterPort: Int, outputDir: os.Path) {
  import proto.master._
  import proto.common.{Empty, RecordMessage}
  import scala.concurrent.{Future, Promise}
  import common.{Worker, Record, Block}
  import io.grpc.stub.StreamObserver

  var id: Int = -1
  private val stub =
    utils.makeStub(masterIp, masterPort)(MasterServiceGrpc.stub)

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

    val temp = outputDir / "tmp"
    val all = for (worker <- workers) yield {
      val reception = Promise[Seq[Block]]()
      val observer = new RecordsObserver(reception)

      worker.stub.demand(DemandRequest(id), observer)
      reception.future
    }

    Future.sequence(all).map { seqs =>
      for (seq <- seqs; block <- seq) yield block
    }
  }

  class RecordsObserver(reception: Promise[Seq[Block]])
      extends StreamObserver[RecordMessage] {

    import common.Block

    val maxElems = Block.size / Record.length
    var buffer: Array[Record] = Array()
    var blocks: Seq[Block] = Nil

    def onNext(msg: RecordMessage): Unit = {
      buffer = buffer :+ Record.fromMessage(msg)

      if (buffer.size == maxElems) {
        blocks = blocks :+ Block.fromArr(buffer, FileNameAllocator.allocate())
        buffer = Array()
      }
    }

    def onCompleted(): Unit = reception.success(blocks)

    def onError(exception: Throwable) = reception.failure(exception)
  }

  object FileNameAllocator {
    val temp = outputDir / "temp"
    var counter = 0

    def allocate(): os.Path = synchronized {
      val result = temp / s"temp.${counter}"
      counter += 1
      result
    }
  }
}
