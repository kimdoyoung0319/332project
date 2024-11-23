package object utils {
  /* IP address of this machine. Convenient for retreiving address in
     shorthand. */
  val thisIp: String = java.net.InetAddress.getLocalHost.getHostAddress

  /* Minimum value of a key. Looks braindead, but could not come up with better
     solution. */
  val minKey: Vector[Byte] = {
    val zero = 0.toByte
    Vector(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero)
  }

  /* Maximum value of a key. */
  val maxKey: Vector[Byte] = {
    val ff = (-1).toByte
    Vector(ff, ff, ff, ff, ff, ff, ff, ff, ff, ff)
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

    def contains(record: Record): Boolean = {
      import Record.compareBytes

      (compareBytes(record.key, from) >= 0) &&
      (compareBytes(to, record.key) > 0)
    }
  }

  /* Auxiliary methods for Byte. */
  implicit class ByteExtended(byte: Byte) {
    def toHexString(): String = String.format("%02X", byte & 0xFF)
  }

  /* Auxiliary methods for vectors of bytes. */
  implicit class ByteVectorExtended(bytes: Vector[Byte]) {
    def toArray(): Array[Byte] = bytes.toArray

    def toHexString(): String = bytes.map(_.toHexString()).mkString

    def toByteString(): com.google.protobuf.ByteString =
      com.google.protobuf.ByteString.copyFrom(toArray())
  }

  /* Auxiliary methods for ByteString. */
  implicit class ByteStringExtended(bytes: com.google.protobuf.ByteString) {
    def toHexString(): String = bytes.toByteArray.toVector.toHexString()
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
}
