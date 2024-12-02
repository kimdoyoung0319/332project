package utils.memory

class MemoryManager {
  import scala.concurrent.{Future, Promise}
  import utils.concurrent.global

  private val runtime = Runtime.getRuntime
  private val percentage = 0.8
  private val maxMemory = (runtime.maxMemory * percentage).toLong
  private val pendingRequests =
    collection.mutable.Queue[(Long, Promise[Unit])]()
  private var availableMemory = maxMemory

  def request(requestedMemory: Long): Future[Unit] = synchronized {
    assert(
      requestedMemory <= maxMemory,
      "The requested memory cannot exceed maximum memory available for JVM."
    )

    if (requestedMemory < availableMemory) {
      availableMemory -= requestedMemory
      Future(())
    } else {
      val pendingRequest = (requestedMemory, Promise[Unit]())
      pendingRequests.enqueue(pendingRequest)
      pendingRequest._2.future
    }
  }

  def release(size: Long): Unit = synchronized {
    import utils.concurrent.UnitPromiseOps

    availableMemory += size

    while (
      pendingRequests.nonEmpty && pendingRequests.head._1 < availableMemory
    ) {
      val (requestedMemory, promise) = pendingRequests.dequeue()
      availableMemory -= requestedMemory
      promise.fulfill()
    }
  }

  def ensured[T](requestedMemory: Long)(block: => T): Future[T] = {
    import utils.concurrent.UnitFutureOps

    request(requestedMemory).after {
      val result = block
      release(requestedMemory)
      result
    }
  }
}
