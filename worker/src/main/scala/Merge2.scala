package worker

class Merger2(
               sortedFiles: Seq[common.DiskRecords], // DiskRecords 입력
               outputDir: os.Path,
               thisId: Int
             ) {
  import scala.concurrent.Future
  import scala.collection.mutable.{PriorityQueue, ListBuffer}
  import common.Record
  import utils.concurrent.global

  private val logger = utils.logger.LoggerFactoryUtil.getLogger("worker")

  def run(): Future[Seq[common.DiskRecords]] = Future {
    logger.info(s"[${thisId}] Merging ${sortedFiles.size} files...")

    // 각 DiskRecords의 이터레이터 생성
    val iterators = sortedFiles.map(f => new MergeIterator(f))

    // PriorityQueue를 사용해 병합
    val recordsPriorityQueue = PriorityQueue[(Record, Int)]()(
      Ordering.by[(Record, Int), Record](_._1).reverse
    )

    // 각 이터레이터의 첫 번째 레코드 초기화
    iterators.zipWithIndex.foreach {
      case (it, idx) if it.hasNext => recordsPriorityQueue.enqueue((it.next(), idx))
      case _ => // 이터레이터가 비어 있는 경우 건너뜀
    }

    val mergedFiles = ListBuffer[common.DiskRecords]()
    val recordsBuffer = new ListBuffer[Record]()
    var postfix = 0

    while (recordsPriorityQueue.nonEmpty) {
      val (record, idx) = recordsPriorityQueue.dequeue()
      recordsBuffer += record

      // 버퍼가 임계 크기 이상이면 디스크에 기록
      if (recordsBuffer.size * Record.length >= utils.general.maxPartitionSize) {
        mergedFiles += writePartition(recordsBuffer, postfix)
        recordsBuffer.clear()
        postfix += 1
      }

      // 이터레이터에서 다음 레코드 가져오기
      if (iterators(idx).hasNext) {
        recordsPriorityQueue.enqueue((iterators(idx).next(), idx))
      }
    }

    // 남은 데이터 처리
    if (recordsBuffer.nonEmpty) {
      mergedFiles += writePartition(recordsBuffer, postfix)
    }

    // 이터레이터 리소스 정리
    iterators.foreach(_.close())

    mergedFiles.toSeq
  }

  private def writePartition(buffer: ListBuffer[Record], postfix: Int): common.DiskRecords = {
    val path = outputDir / s"partition.${postfix}"
    val outputStream = os.write.outputStream(path, createFolders = true)
    try {
      buffer.foreach(record => outputStream.write(record.serialized))
    } finally {
      outputStream.close()
    }
    common.DiskRecords(path)
  }
}