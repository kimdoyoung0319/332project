package object common {

  /* A record whose size is hundred of bytes. */
  class Record(val key: Vector[Byte], val value: Vector[Byte]) {
    import proto.common.RecordMessage

    def toVector(): Vector[Byte] = key ++ value

    def toArray(): Array[Byte] = (key ++ value).toArray

    def toMessage(): RecordMessage = {
      import com.google.protobuf.ByteString

      RecordMessage(content = ByteString.copyFrom(toArray()))
    }

    def until(that: Record): (Vector[Byte], Vector[Byte]) = (this.key, that.key)

    override def toString(): String = {
      import utils.ByteVectorExtended
      key.toHexString() ++ " " ++ value.toHexString()
    }
  }

  /* The companion object for Record. */
  object Record {
    import proto.common.RecordMessage

    val length = 100

    /* Make a Record object from an array of bytes. */
    def apply(arr: Array[Byte]): Record = {
      assert(arr.size == length)

      val (key, value) = arr.splitAt(10)
      new Record(key.toVector, value.toVector)
    }

    /* Make a Record object from a protobuf message. */
    def fromMessage(message: RecordMessage): Record =
      this(message.content.toByteArray)

    implicit object Ordering extends Ordering[Record] {
      def compare(x: Record, y: Record): Int = {
        import utils.ByteVectorExtended
        x.key.compare(y.key)
      }
    }
  }

  /* A file contains sequence of records, and fit in the memory. */
  class Block(val path: os.Path) {
    import geny.Generator
    import scala.concurrent.Promise

    assert(os.isFile(path))

    val removed = Promise[Unit]()
    var pos = 0

    if (!os.exists(path))
      removed.success(())

    val contents: Generator[Record] = {
      import os.read.chunks

      assert(!removed.isCompleted)
      chunks(path, Record.length).drop(pos).map { case (arr, _) => Record(arr) }
    }

    def load(): Seq[Record] = contents.toSeq

    def sorted(path: os.Path): Block = Block.fromSeq(load().sorted, path)

    def read(): Generator[String] = contents.map(_.toString)

    def sample(): Record = contents.head

    def advance(): Record = {
      val result = contents.head
      pos += 1
      result
    }

    def exhausted(): Boolean = pos >= contents.count()

    /* Warning! Invoking this method on wrong block may have severe impact. */
    def remove(): Unit = {
      removed.success(())
      os.remove(path)
    }
  }

  /* Companion object for Block. */
  object Block {
    import os.{Path, temp, write, exists}

    val size = 32 * 1024 * 1024

    /* Writes the sequence of records into the file refered by path and returns
       new Block object that refers to it. */
    def fromSeq(seq: Seq[Record], path: Path): Block = {
      val src = seq.flatMap(_.toVector()).toArray
      write(path, src)
      new Block(path)
    }

    /* Writes the array of records into the file refered by path and returns new
       Block object that refers to it. */
    def fromArr(arr: Array[Record], path: Path): Block = {
      val src = arr.flatMap(_.toVector()).toArray
      write(path, src)
      new Block(path)
    }

    /* Make a new Block object from a path to a file that already exists. The
     path must exist in the disk. */
    def apply(path: Path): Block = {
      assert(exists(path))
      new Block(path)
    }
  }

  /* Class to manage worker information. */
  case class Worker(val id: Int, val ip: String, val port: Int) {
    import proto.worker._
    import proto.worker.ShuffleRequest.WorkerMessage
    import com.google.protobuf.ByteString

    val stub = utils.makeStub(ip, port)(WorkerServiceGrpc.stub)

    def toMapping(start: ByteString, end: ByteString): (Int, WorkerMessage) =
      (id, WorkerMessage(ip = ip, port = port, start = start, end = end))
  }
}
