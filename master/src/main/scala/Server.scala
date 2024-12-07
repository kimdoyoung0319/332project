package master

class Server(count: Int) {
  import utils.concurrent.PromiseCompanionOps
  import os._

  private val finished = scala.concurrent.Promise.withCallback(stop)
  private val logger = utils.logger.LoggerFactoryUtil.getLogger("master")
  private val service = new Service(count, finished)
  private val server = utils.grpc.makeServer(service.binded)

  def start(inputPath: String): Unit = {
    server.start()
    logger.info(s"Master server listening to ${server.getPort()} started.")
    println(s"${utils.network.thisIp}:${server.getPort()}")
    executeScript(inputPath)
  }

  def await(): Unit = server.awaitTermination()

  private def stop: Unit = {
    logger.info("Shutting down master server...")
    server.shutdown()
  }

  private def executeScript(inputPath: String): Unit = {
    val scriptName = ".deploy_worker.sh"
    val scriptPath = os.pwd / "scripts" / scriptName
    try {
      val MASTER_IP = utils.network.thisIp
      val MASTER_PORT = server.getPort().toString
      val result = os.proc(scriptPath, MASTER_IP, MASTER_PORT, inputPath, count.toString).call()
      logger.info(s"Executed $scriptName with port $MASTER_PORT: ${result.out.text().trim}")
    } catch {
      case e: os.SubprocessException =>
        logger.error(s"Failed to execute $scriptName: ${e.getMessage}")
    }
  }
}
