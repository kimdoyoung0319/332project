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

    /* Compare two vectors of bytes, and returns some positive value if x is
       greater than y in lexicographical order. */
    @scala.annotation.tailrec
    def compareBytes(x: Vector[Byte], y: Vector[Byte]): Int = {
      assert(x.size == y.size)

      (x, y) match {
        case (xh +: xt, yh +: yt) if xh == yh => compareBytes(xt, yt)
        case (xh +: _, yh +: _) => (xh.toShort & 0xFF) - (yh.toShort & 0xFF)
        case (_, _) => 0
      }
    }

    implicit object Ordering extends Ordering[Record] {
      def compare(x: Record, y: Record): Int = compareBytes(x.key, y.key)
    }
  }

  /* A file contains sequence of records, and fit in the memory. */
  class Block(val path: os.Path) {
    import geny.Generator
    import scala.concurrent.Promise

    assert(os.isFile(path))

    val removed = Promise[Unit]()

    val contents: Generator[Record] = {
      import os.read.chunks

      assert(!removed.isCompleted)
      chunks(path, Record.length).map { case (arr, _) => Record(arr) }
    }

    def load(): Seq[Record] = contents.toSeq

    def sorted(path: os.Path): Block = Block.fromSeq(load().sorted, path)

    def read(): Generator[String] = Block(path).contents.map(_.toString)

    def sample(): Record = contents.head

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
