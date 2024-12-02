package master

class Server(count: Int) {
  import utils.concurrent.PromiseCompanionOps

  private val finished = scala.concurrent.Promise.withCallback(stop)
  private val logger = utils.logger.LoggerFactoryUtil.getLogger("master")
  private val service = new Service(count, finished)
  private val server = utils.grpc.makeServer(service.binded)

  def start(): Unit = {
    server.start()
    logger.info(s"Master server listening to ${server.getPort()} started.")
    println(s"${utils.network.thisIp}:${server.getPort()}")
  }

  def await(): Unit = server.awaitTermination()

  private def stop: Unit = {
    logger.info("Shutting down master server...")
    server.shutdown()
  }
}
