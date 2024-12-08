package worker

import common.Record
import os.Path
import java.io.InputStream

class MergeIterator(diskRecords: common.DiskRecords) extends Iterator[common.Record] {
  private val inputStream = os.read.inputStream(diskRecords.path)
  private val buffer = new Array[Byte](common.Record.length)
  private var nextRecord: Option[common.Record] = fetchNext()

  private def fetchNext(): Option[common.Record] = {
    val bytesRead = inputStream.read(buffer)
    if (bytesRead == -1) None else Some(common.Record(buffer.clone()))
  }

  override def hasNext: Boolean = nextRecord.isDefined

  override def next(): common.Record = {
    if (!hasNext) throw new NoSuchElementException("No more records")
    val current = nextRecord.get
    nextRecord = fetchNext()
    current
  }

  def close(): Unit = inputStream.close()
}