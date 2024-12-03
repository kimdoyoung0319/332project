package utils.memory

class MemoryManager {
  import scala.concurrent.{Future, Promise}
  import utils.concurrent.{global, UnitPromiseOps, UnitFutureOps}

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
    availableMemory += size

    while (
      pendingRequests.nonEmpty && pendingRequests.head._1 < availableMemory
    ) {
      val (requestedMemory, promise) = pendingRequests.dequeue()
      availableMemory -= requestedMemory
      promise.fulfill()
    }
  }

  def ensured[T](requestedMemory: Long)(block: => T): Future[T] =
    request(requestedMemory).after {
      val result = block
      release(requestedMemory)
      result
    }

  def requiring[T](requestedMemory: Long)(block: => Future[T]): Future[T] =
    request(requestedMemory).flatAfter {
      val result = block
      result.onComplete { case _ => release(requestedMemory) }
      result
    }
}
