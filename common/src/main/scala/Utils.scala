package object utils {
  /* IP address of this machine. Convenient for retreiving address in
     shorthand. */
  val thisIp: String = java.net.InetAddress.getLocalHost.getHostAddress

  /* ByteString for minimum value of a key. Looks braindead, but could not come
     up with better solution. */
  val minKeyString: com.google.protobuf.ByteString = {
    val zero = 0.toByte
    com.google.protobuf.ByteString.copyFrom(
      Array(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero)
    )
  }

  /* ByteString for maximum value of a key. */
  val maxKeyString: com.google.protobuf.ByteString = {
    val ff = (-1).toByte
    com.google.protobuf.ByteString.copyFrom(
      Array(ff, ff, ff, ff, ff, ff, ff, ff, ff, ff)
    )
  }

  /* Global execution context to be used conveniently. */
  implicit val globalContext: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  /* Range for records. */
  implicit class RecordRange(range: (Vector[Byte], Vector[Byte])) {
    import common.Record

    private val from = range._1
    private val to = range._2

    assert(from.size == 10, to.size == 10)

    def contains(record: Record): Boolean =
      (record.key.compare(from) >= 0) && (to.compare(record.key) > 0)
  }

  /* Auxiliary methods for arrays of bytes. */
  implicit class RecordsArrayExtended(recordsArray: Array[common.Record]) {
    def serialized: Array[Byte] =
      for (record <- recordsArray; byte <- record.toVector()) yield byte
  }

  /* Auxiliary methods for Byte. */
  implicit class ByteExtended(byte: Byte) {
    def toHexString(): String = String.format("%02X", byte & 0xFF)
  }

  /* Auxiliary methods for vectors of bytes. */
  implicit class ByteVectorExtended(bytes: Vector[Byte])
      extends Ordered[Vector[Byte]] {
    def toHexString(): String = bytes.map(_.toHexString()).mkString

    def toByteString(): com.google.protobuf.ByteString =
      com.google.protobuf.ByteString.copyFrom(bytes.toArray)

    /* Compare two vectors of bytes, and returns some positive value if x is
       greater than y in lexicographical order. */
    def compare(that: Vector[Byte]): Int = {
      require(bytes.size == that.size)

      (bytes, that) match {
        case (xh +: xt, yh +: yt) if xh == yh => xt.compare(yt)
        case (xh +: _, yh +: _) => (xh.toShort & 0xFF) - (yh.toShort & 0xFF)
        case (_, _) => 0
      }
    }

    def until(that: Vector[Byte]): (Vector[Byte], Vector[Byte]) = {
      assert(bytes.size == that.size)
      (bytes, that)
    }
  }

  /* Auxiliary methods for ByteString. */
  implicit class ByteStringExtended(bytes: com.google.protobuf.ByteString) {
    def toHexString: String = toByteVector.toHexString()

    def toByteVector: Vector[Byte] = bytes.toByteArray.toVector
  }

  /* Auxiliary methods for scala.collection.mutable.Buffer[common.Record]. */
  implicit class RecordsBufferExtended(
      recordsBuffer: collection.mutable.Buffer[common.Record]
  ) {
    def toByteArray(): Array[Byte] = recordsBuffer.toArray.serialized

    def writeIntoDisk(path: os.Path): common.Block = {
      os.write(path, recordsBuffer.toByteArray())
      recordsBuffer.clear()
      common.Block(path)
    }
  }

  /* Auxiliary methods for Promises. */
  implicit class PromiseExtended[T](promise: scala.concurrent.Promise[T]) {
    def apply(): T = {
      import scala.util.{Success, Failure}

      promise.future.value match {
        case None | Some(Failure(_)) => throw new IllegalStateException
        case Some(Success(value)) => value
      }
    }
  }

  /* Converts string into os.Path regardless of whether it is absolute or
     relative. */
  def stringToPath(str: String): os.Path = {
    import os.{FilePath, RelPath, SubPath, Path, pwd}

    FilePath(str) match {
      case p: Path => p
      case p: RelPath => pwd / p
      case p: SubPath => pwd / p
    }
  }

  /* Small wrapper over gRPC's ManagedChannelBuilder. */
  def makeStub[T](ip: String, port: Int)(
      createStubWith: io.grpc.ManagedChannel => T
  ): T = {
    val channel =
      io.grpc.ManagedChannelBuilder.forAddress(ip, port).usePlaintext().build
    createStubWith(channel)
  }

  /* Small wrapper over gRPC's ServerBuilder. */
  def makeServer[T](serviceImpl: T)(
      createServiceWith: (
          T,
          scala.concurrent.ExecutionContext
      ) => io.grpc.ServerServiceDefinition
  ) = {
    val service = createServiceWith(serviceImpl, globalContext)
    io.grpc.ServerBuilder.forPort(0).addService(service).build.start
  }

  /* Leaves a log about the contents of the sequence of records with the
     logger, with the description about the records desc. */
  def logRecords(
      logger: com.typesafe.scalalogging.Logger,
      records: Seq[common.Record],
      desc: String
  ): Unit = {

    logger.info(s"Contents of ${desc} are....")
    for (record <- records)
      logger.info(record.toString)
    logger.info("------------------------------")
  }

  /* Cleans up given directory, making one if it does not exists, deleting if
     a file with same name exists. */
  def cleanDirectory(dir: os.Path): Unit = {
    if (os.exists(dir) && os.isDir(dir)) {
      for (entry <- os.list(dir)) os.remove.all(entry)
    } else if (os.exists(dir) && os.isFile(dir)) {
      os.remove(dir)
      os.makeDir(dir)
    } else {
      os.makeDir(dir)
    }
  }

  /* Thread-safe allocator for filenames under path with base filename. */
  class ThreadSafeNameAllocator(path: os.Path, base: String) {
    var counter = 0

    def allocate(): os.Path = this.synchronized {
      val result = path / s"${base}.${counter}"
      counter += 1
      result
    }
  }

  /* Thread-safe mutable list. */
  class ThreadSafeMutableList[T](seq: Seq[T]) {
    import scala.collection.mutable.ListBuffer

    private val mutableList = ListBuffer.from(seq)

    def append(elem: T): Unit = mutableList.synchronized {
      mutableList += (elem)
    }

    def +=(elem: T): Unit = append(elem)

    def toSeq: Seq[T] = mutableList.synchronized(mutableList.toSeq)
  }

  object ThreadSafeMutableList {
    def apply[T]() = new ThreadSafeMutableList[T](Seq.empty)
  }

  /* Prints the key of each records, wating action between each records.
     Convenient for reading the file contents generated by gensort. */
  def printKeys(path: os.Path, action: => Unit): Unit = {
    val blockToPrint = common.Block(path)

    for (record <- blockToPrint.contents) {
      println(record.key.toHexString())
      action
    }
  }

  /* Prints the key of each records, wating newline (pressing Return) between
     each records. Convenient for reading the file contents generated by
     gensort. */
  def printKeysWithEnter(path: os.Path): Unit =
    printKeys(path, scala.io.StdIn.readLine())
}

package utils.test {
  /* Wrapper over gensort to be conveniently used in tests. */
  object Gensort {
    final class GensortFailedException extends Exception {}

    var count = 0
    val gensort = os.pwd / "bin" / "gensort"
    val temp = os.temp.dir()

    require(
      os.exists(gensort),
      """
        Tests involving gensort requires gensort executable to be in bin/
        directory. Compile and put it if you do not have one.
      """
    )

    def makeBinaryAt(n: Int, file: os.Path): os.Path = {
      val cmd = (gensort, n, file)

      os.call(cmd = cmd, check = false).exitCode match {
        case 0 => file
        case _ => throw new GensortFailedException
      }
    }

    def makeAsciiAt(n: Int, file: os.Path): os.Path = {
      val cmd = (gensort, "-a", n, file)

      os.call(cmd = cmd, check = false).exitCode match {
        case 0 => file
        case _ => throw new GensortFailedException
      }
    }

    def makeBinary(n: Int): os.Path = makeBinaryAt(n, temp / s"temp.$count")
    def makeAscii(n: Int): os.Path = makeAsciiAt(n, temp / s"temp.$count")
  }

  /* Wrapper over valsort to be conveniently used in tests. */
  object Valsort {
    val valsort = os.pwd / "bin" / "valsort"

    require(
      os.exists(valsort),
      """
        Tests involving valsort requires valsort executable to be in bin/
        directory. Compile and put it if you do not have one.
      """
    )

    def validate(file: os.Path): Boolean = {
      require(os.exists(file))

      val cmd = (valsort, file)
      os.call(cmd = cmd, check = false).exitCode match {
        case 0 => true
        case _ => false
      }
    }
  }

}
