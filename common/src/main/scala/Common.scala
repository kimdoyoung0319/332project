package common

import org.scalactic.Bool

class Worker(val id: Int, val ip: String, val port: Int) {
  val stub = utils.grpc.makeWorkerStub(ip, port)
}

object Worker {
  def apply(id: Int, ip: String, port: Int) = new Worker(id, ip, port)
}

case class Record(key: Array[Byte], value: Array[Byte])
    extends Ordered[Record] {
  assert(key.size + value.size == Record.length)

  def serialized = key ++ value
  def toMessage = {
    import com.google.protobuf.ByteString
    import proto.common.RecordMessage

    RecordMessage(ByteString.copyFrom(serialized))
  }
  def compare(that: Record): Int = Record.Ordering.compare(this, that)
}

object Record {
  val length = 100

  object Key { val length = 10 }

  def apply(source: Array[Byte]): Record = {
    assert(source.size == length)

    new Record(source.slice(0, Key.length), source.slice(Key.length, length))
  }

  implicit object Ordering extends scala.Ordering[Record] {
    def compare(x: Record, y: Record): Int =
      utils.general.ByteArrayOrdering.compare(x.key, y.key)
  }
}

object Key {
  val length = 10
}

class LoadedRecords(val contents: collection.mutable.ArrayBuffer[Record]) {
  /* This methods creates new directory when there's no such directory as
     specified in path. Also, notice that it discards existing contents in
     path. */
  def writeInto(path: os.Path): DiskRecords = {
    val serialized = Array.from(contents.flatMap(_.serialized))
    os.write.over(
      target = path,
      data = serialized,
      createFolders = true
    )
    DiskRecords(path)
  }

  def writeIntoAndClear(path: os.Path): DiskRecords = {
    val result = writeInto(path)
    contents.clear()
    result
  }

  def sort(): Unit = contents.sortInPlace()
  def append(elem: Record): Unit = contents += elem
  def +=(elem: Record): Unit = append(elem)
  def sizeInByte: Long = contents.size * Record.length
  def isEmpty: Boolean = contents.isEmpty
  def nonEmpty: Boolean = !isEmpty
}

object LoadedRecords {
  import scala.collection.mutable.ArrayBuffer

  def apply(): LoadedRecords =
    new LoadedRecords(ArrayBuffer())

  def apply(source: Array[Record]): LoadedRecords =
    new LoadedRecords(ArrayBuffer.from(source))

  def fromBytes(source: Array[Byte]): LoadedRecords = {
    assert(
      source.size % Record.length == 0,
      "The input source's length must be divisible evenly by the length of records"
    )
    val grouped = source.grouped(Record.length).map(Record(_)).toArray

    this(grouped)
  }
}

class DiskRecords(path: os.Path) {
  def load(recordOffset: Int, recordCount: Int): LoadedRecords = {
    assert(recordOffset > 0, "Starting index must be larger than 0.")
    assert(recordCount > 0, "The number of records must be larger than 0.")

    val offset = recordOffset * Record.length
    val count = recordCount * Record.length
    val contents = os.read.bytes(path, offset, count)

    LoadedRecords.fromBytes(contents)
  }

  def loadAll(): LoadedRecords = LoadedRecords.fromBytes(os.read.bytes(path))

  def loadAt(index: Int): Record = {
    val offset = index * Record.length

    assert(index >= 0, "Index to be loaded must be positive or zero.")
    assert(offset < sizeInByte, "Index must be within the file size.")

    val contents = os.read.bytes(path, offset, Record.length)

    Record(contents)
  }

  def grabSample(count: Int): Array[Record] =
    Array.from(load(0, count).contents)

  def sizeInByte: Long = os.size(path)
}

object DiskRecords {
  def apply(path: os.Path): DiskRecords = new DiskRecords(path)
}
