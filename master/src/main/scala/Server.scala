package master

class Server(count: Int) {
  import utils.concurrent.PromiseCompanionOps
  import os._

  private val finished = scala.concurrent.Promise.withCallback(stop)
  private val logger = utils.logger.LoggerFactoryUtil.getLogger("master")
  private val service = new Service(count, finished)
  private val server = utils.grpc.makeServer(service.binded)

  def start(size: String): Unit = {
    server.start()
    val port = server.getPort()
    logger.info(s"Master server listening to ${server.getPort()} started.")
    println(s"${utils.network.thisIp}:$port")
    executeScript(port, size)
  }

  def await(): Unit = server.awaitTermination()

  private def stop: Unit = {
    logger.info("Shutting down master server...")
    server.shutdown()
  }

  private def executeScript(port: Int, size: String): Unit = {
    val scriptName = "deploy_worker.sh"
    val scriptPath = os.Path(s"/home/blue/scripts/$scriptName")
    try {
      val result = os.proc(scriptPath, port.toString, size).call()
      logger.info(s"Executed $scriptName with port $port: ${result.out.text().trim}")
    } catch {
      case e: os.SubprocessException =>
        logger.error(s"Failed to execute $scriptName: ${e.getMessage}")
    }
  }
}
