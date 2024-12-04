package utils.memory

object MemoryManager {
  import scala.concurrent.{Future, Promise}
  import utils.concurrent.{global, UnitPromiseOps, UnitFutureOps}

  private val logger = utils.logger.LoggerFactoryUtil.getLogger("worker")
  private val runtime = Runtime.getRuntime
  private val percentage = 0.8
  private val maxMemory = (runtime.maxMemory * percentage).toLong
  private val pendingRequests =
    collection.mutable.Queue[(Long, Promise[Unit])]()
  private var availableMemory = maxMemory

  /*
  logger.info(
    s"[Memory] The current maximum memory is ${runtime.maxMemory()} B."
  )
  logger.info(
    s"[Memory] The current total memory is ${runtime.totalMemory()} B."
  )
  logger.info(
    s"[Memory] The total memory that can be requested is ${maxMemory} B."
  )
   */

  def request(requestedMemory: Long): Future[Unit] = synchronized {
    assert(
      requestedMemory <= maxMemory,
      "The requested memory cannot exceed maximum memory available for JVM."
    )

    /*
    logger.info(
      s"[Memory] Requesting ${requestedMemory} B of memory with available memory of ${availableMemory} B."
    )
    logger.info(
      s"[Memory] Current memory left for JVM is ${runtime.freeMemory()} B."
    )
     */

    if (requestedMemory < availableMemory) {
      /*
      logger.info(
        s"[Memory] Succeeded requesting ${requestedMemory} B. The remaining is ${availableMemory} B."
      )
       */
      availableMemory -= requestedMemory
      Future(())
    } else {
      val pendingRequest = (requestedMemory, Promise[Unit]())
      pendingRequests.enqueue(pendingRequest)
      pendingRequest._2.future.andThen { _ =>
        /*
        logger.info(
          s"[Memory] Succeeded requesting ${requestedMemory} B. The remaining is ${availableMemory} B."
        )
         */
        ()
      }
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
