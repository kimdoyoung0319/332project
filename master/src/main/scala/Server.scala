package master

import proto.master._

class MasterService(workerCount: Int) extends MasterServiceGrpc.MasterService {
  import proto.common.Empty
  import scala.concurrent.{Future, Promise}
  import utils.globalContext

  /* TODO: Is it really best to place it here as a mutable variable? */
  var workers: Seq[proto.worker.WorkerServiceGrpc.WorkerServiceStub] = Nil

  /* TODO: Is it really best to have Promise[Unit] type? */
  val registration = Promise[Unit]()
  val sampling = Promise[Unit]()

  /* TODO: Add logging. */
  override def register(request: RegisterRequest): Future[Empty] = Future {
    import proto.worker.WorkerServiceGrpc

    val (ip, port) = (request.ip, request.port)
    val stub = utils.makeStub(ip, port)(WorkerServiceGrpc.stub)

    if (workers.size < workerCount)
      workers = workers.appended(stub)
    else
      throw new IllegalStateException

    if (workers.size == workerCount)
      registration.success(())

    Empty()
  }
}

class MasterServer(workerCount: Int) {
  val service = new MasterService(workerCount)
  val server = utils.makeServer(service)(MasterServiceGrpc.bindService)

  println(s"${utils.thisIp}:${server.getPort}")

  def await(): Unit = server.awaitTermination()
}
