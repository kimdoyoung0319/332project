package worker

class Server(
    masterIp: String,
    masterPort: Int,
    inputs: Seq[common.DiskRecords],
    outputDir: os.Path
) {
  import utils.concurrent.PromiseCompanionOps
  assert(
    utils.network.ipPattern.matches(masterIp),
    "Master IP should be in legal form."
  )

  assert(masterPort > 0, "The port number should be a positive integer.")

  private val finished = scala.concurrent.Promise.withCallback(stop)
  private val logger = utils.logger.LoggerFactoryUtil.getLogger("worker")
  private val service =
    new Service(inputs, outputDir, masterIp, masterPort, finished)
  private val server = utils.grpc.makeServer(service.binded)

  def start(): Unit = {
    server.start()
    service.registerToMaster(server.getPort())
    logger.info(s"Worker server listening to port ${server.getPort} started.")
  }
  def await(): Unit = server.awaitTermination()

  private def stop: Unit = {
    logger.info("Shutting down worker server...")
    server.shutdown()
  }
}
