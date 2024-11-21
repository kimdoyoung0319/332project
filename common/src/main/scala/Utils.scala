package object utils {
  /* IP address of this machine. Convenient for retreiving address in
     shorthand. */
  val thisIp: String = java.net.InetAddress.getLocalHost.getHostAddress

  /* Global execution context to be used conveniently. */
  implicit val globalContext: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

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
    val channel = io.grpc.ManagedChannelBuilder.forAddress(ip, port).build
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
}
