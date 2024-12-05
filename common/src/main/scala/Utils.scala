package utils

package object general {
  val maxPartitionSize = 32 * 1000 * 1000

  implicit object ByteArrayOrdering extends math.Ordering[Array[Byte]] {
    def compare(x: Array[Byte], y: Array[Byte]): Int = {
      assert(x.size == y.size, "Two byte arrays must have equal size.")

      for (i <- 0 until x.size) {
        if (x(i) != y(i))
          return (x(i).toShort & 0xff) - (y(i).toShort & 0xff)
      }

      return 0
    }
  }

  implicit class ByteOps(byte: Byte) {
    def toHexString: String = String.format("%02X", byte & 0xff)
  }

  implicit class ByteStringOps(bytes: com.google.protobuf.ByteString) {
    def toHexString: String =
      bytes.toByteArray.map(_.toHexString).mkString
  }

  implicit class ByteArrayOps(bytes: Array[Byte]) {
    def toHexString: String = bytes.map(_.toHexString).mkString
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
      def unpacked: Option[LoadedRecords] = {
        if (message.partition.nonEmpty) {
          val records = message.partition.map { recordMessage =>
            Record(recordMessage.unpacked)
          }.toArray
          val loaded = LoadedRecords(records)
          Some(loaded)
        } else {
          None
        }
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
  import general.maxPartitionSize

  def makeServer(service: ServerServiceDefinition) =
    ServerBuilder.forPort(0).addService(service).build

  def makeMasterStub(ip: String, port: Int) =
    master.MasterServiceGrpc.stub(makeChannel(ip, port))

  def makeWorkerStub(ip: String, port: Int) =
    worker.WorkerServiceGrpc.stub(makeBigChannel(ip, port))

  private def makeChannel(ip: String, port: Int) =
    ManagedChannelBuilder.forAddress(ip, port).usePlaintext().build

  private def makeBigChannel(ip: String, port: Int) =
    ManagedChannelBuilder
      .forAddress(ip, port)
      .usePlaintext()
      .maxInboundMessageSize(maxPartitionSize)
      .build
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

    def repeat(callback: => Future[Boolean]): Future[Unit] =
      callback.flatMap {
        case true  => repeat(callback)
        case false => Future.unit
      }
  }

  implicit class UnitPromiseOps(promise: scala.concurrent.Promise[Unit]) {
    def fulfill(): Unit = promise.trySuccess(())
  }

  implicit class UnitFutureOps(future: scala.concurrent.Future[Unit]) {
    import scala.concurrent.Future

    def after[T](callback: => T): Future[T] = future.map { _ => callback }
    def flatAfter[T](callback: => Future[T]): Future[T] =
      future.flatMap { _ => callback }
  }

  implicit class PromiseOps[T](promise: scala.concurrent.Promise[T]) {
    def value: T = promise.future.value.get.get
  }

  implicit class FutureOps[T](future: scala.concurrent.Future[T]) {
    import scala.util.{Success, Failure}

    def after[U](callback: T => U): Unit = future.onComplete {
      case Success(value)     => callback(value)
      case Failure(exception) => throw exception
    }
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

  import scala.jdk.CollectionConverters.SeqHasAsJava
  import scala.jdk.javaapi.CollectionConverters

  class SafeQueue[T](source: Seq[T])
      extends java.util.concurrent.ConcurrentLinkedQueue[T](source.asJava) {
    def enqueue(elem: T): Unit = super.add(elem)
    def dequeue(): T = super.poll()
    def tryDequeueTwo(): Option[(T, T)] = synchronized {
      if (super.size >= 2) {
        val first = dequeue()
        val second = dequeue()
        Some((first, second))
      } else
        None
    }
  }

  object SafeQueue {
    def apply[T](source: Iterable[T]) = new SafeQueue[T](source.toSeq)
  }
}
