package worker

class WorkerClient(masterIp: String, masterPort: Int) {
  import proto.master._
  import proto.common.Empty
  import scala.concurrent.Future

  private val stub =
    utils.makeStub(masterIp, masterPort)(MasterServiceGrpc.stub)

  def register(port: Int): Future[Empty] =
    stub.register(RegisterRequest(ip = utils.thisIp, port = port))
}
