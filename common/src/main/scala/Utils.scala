package utils

package object general {
  implicit object ByteArrayOrdering extends math.Ordering[Array[Byte]] {
    def compare(x: Array[Byte], y: Array[Byte]): Int = {
      assert(x.size == y.size, "Two byte arrays must have equal size.")

      for (i <- 0 until x.size) {
        if (x(i) != y(i))
          return (x(i).toShort & 0xFF) - (y(i).toShort & 0xFF)
      }

      return 0
    }
  }
}

package object network {
  val ipPattern = """^(\d{1,3}\.){3}\d{1,3}$""".r
  val addressPattern = """^(\d{1,3}\.){3}\d{1,3}:\d{1,5}$""".r
  def thisIp = java.net.InetAddress.getLocalHost.getHostAddress
}

package proto {
  trait ProtoMessage {
    def unpacked: Any
  }

  package object worker {
    import _root_.proto.worker._
    import _root_.common.{LoadedRecords, Record}
    import utils.proto.common.RecordMessageOps

    implicit class AllWorkersOps(message: AllWorkers) extends ProtoMessage {
      import _root_.common.Worker

      def unpacked: (Int, Seq[Worker]) = {
        val workers = for (id <- 0 until message.count) yield {
          val worker = message.workers(id)
          Worker(id, worker.ip, worker.port)
        }

        (message.count, workers)
      }
    }

    implicit class SampleRecordsOps(message: SampleRecords)
        extends ProtoMessage {
      def unpacked: Seq[Array[Byte]] = message.sample.toSeq.map(_.unpacked)
    }

    implicit class PartitionedRecordsOps(message: PartitionedRecords)
        extends ProtoMessage {
      def unpacked: (Int, LoadedRecords) = {
        val (size, partition) = (message.size, message.partition)
        val records = partition.map { recordMessage =>
          Record(recordMessage.unpacked)
        }.toArray
        val loaded = LoadedRecords(records)

        (size, loaded)
      }
    }
  }

  package object common {
    import _root_.proto.common._

    implicit class RecordMessageOps(message: RecordMessage)
        extends ProtoMessage {
      def unpacked: Array[Byte] = {
        assert(message.record.toByteArray.size == 100)
        message.record.toByteArray()
      }

      def toRecord: _root_.common.Record = _root_.common.Record(unpacked)
    }
  }
}

package object grpc {
  import io.grpc._
  import _root_.proto._

  def makeServer(service: ServerServiceDefinition) =
    ServerBuilder.forPort(0).addService(service).build

  def makeMasterStub(ip: String, port: Int) =
    master.MasterServiceGrpc.stub(makeChannel(ip, port))

  def makeWorkerStub(ip: String, port: Int) =
    worker.WorkerServiceGrpc.stub(makeChannel(ip, port))

  private def makeChannel(ip: String, port: Int) =
    ManagedChannelBuilder.forAddress(ip, port).usePlaintext().build
}

package object concurrent {
  implicit val global: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  implicit class PromiseCompanionOps(companion: scala.concurrent.Promise.type) {
    import scala.concurrent.Promise

    def withCallback[T](callback: T => Unit): Promise[T] = {
      val promise = Promise[T]()
      promise.future.foreach(callback)
      promise
    }

    def withCallback(callback: => Unit): Promise[Unit] = {
      val promise = Promise[Unit]()
      promise.future.foreach(_ => callback)
      promise
    }
  }

  implicit class FutureCompanionOps(companion: scala.concurrent.Future.type) {
    import scala.concurrent.Future

    def forall[T](futures: Seq[Future[T]]): Future[Unit] =
      companion.sequence(futures).map { _ => () }
  }

  implicit class UnitPromiseOps(promise: scala.concurrent.Promise[Unit]) {
    def fulfill(): Unit = promise.success(())
  }

  implicit class UnitFutureOps(future: scala.concurrent.Future[Unit]) {
    import scala.concurrent.Future

    def after[T](callback: => T): Future[T] = future.map { _ => callback }
  }

  implicit class PromiseOps[T](promise: scala.concurrent.Promise[T]) {
    def value: T = promise.future.value.get.get
  }

  class SafeBuffer[T](source: List[T])
      extends scala.collection.mutable.AbstractSeq[T] {
    import scala.collection.mutable.ListBuffer

    val buffer = ListBuffer.from(source)

    def apply(i: Int): T = synchronized(buffer(i))
    def +=(elem: T): Unit = synchronized(buffer += elem)
    override def toSeq: Seq[T] = synchronized(buffer.toSeq)
    def update(i: Int, elem: T): Unit = synchronized(buffer.update(i, elem))
    def length: Int = synchronized(buffer.size)
    def iterator: Iterator[T] = toSeq.iterator
  }

  object SafeBuffer {
    def apply[T]() = new SafeBuffer[T](Nil)
  }

  class SafeCounter(value: Int) {
    var counter = value

    def increment(): Int = synchronized {
      val result = counter
      counter += 1
      result
    }
  }

  object SafeCounter {
    def apply(value: Int) = new SafeCounter(value)
  }

  class SafeQueue[T](source: Seq[T]) {
    val queue = scala.collection.mutable.Queue.from(source)

    def enqueue(elem: T): Unit = synchronized(queue.enqueue(elem))
    def dequeue(): T = synchronized(queue.dequeue())
    def size: Int = synchronized(queue.size)
  }

  object SafeQueue {
    def apply[T](
        source: Seq[T]
    ): SafeQueue[T] =
      new SafeQueue(source)
  }
}
