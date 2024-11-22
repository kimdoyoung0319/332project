package object common {
  implicit class Bytes(bytes: Vector[Byte]) {
    import com.google.protobuf.ByteString

    def toArray(): Array[Byte] = bytes.toArray

    def toByteString(): com.google.protobuf.ByteString =
      ByteString.copyFrom(toArray())
  }

  /* A record whose size is hundred of bytes. */
  class Record(val key: Vector[Byte], val value: Vector[Byte]) {
    import proto.common.RecordMessage

    def toVector(): Vector[Byte] = key ++ value

    def toArray(): Array[Byte] = (key ++ value).toArray

    def toMessage(): RecordMessage = {
      import com.google.protobuf.ByteString

      RecordMessage(content = ByteString.copyFrom(toArray()))
    }

    override def toString(): String = {
      val keyStr = key.map("%02X" format _).mkString
      val valueStr = value.map("%02X" format _).mkString
      keyStr ++ " " ++ valueStr
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

    @scala.annotation.tailrec
    private def compareBytes(x: Vector[Byte], y: Vector[Byte]): Int = {
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

  /* A file contains sequence of records. */
  class Block(val path: os.Path) {
    import geny.Generator
    import os.read.chunks

    val contents: Generator[Record] = {
      chunks(path, Record.length).map { case (arr, _) => Record(arr) }
    }

    def load(): Seq[Record] = contents.toSeq

    def sorted(): Block = Block.from(load().sorted)

    def read(): Generator[String] = Block(path).contents.map(_.toString)

    def sample(): Record = contents.head
  }

  /* Companion object for Block. */
  object Block {
    import os.{Path, temp, write, exists}

    var count = 0
    val target: Path = temp.dir()

    /* Writes the sequence of records into a file and returns new Block object
     that refers to it. */
    def from(s: Seq[Record]): Block = {
      val path = target / s"temp.${count}"
      val src = s.flatMap(_.toVector()).toArray

      count += 1
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
}
