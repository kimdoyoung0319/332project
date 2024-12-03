/* External merging implementation. Given the sequence of sorted input files,
   merges them into a set of output files. */
package worker

class Merger(sortedFiles: common.DiskRecords, outputDir: os.Path) {
  private val logger = utils.logger.LoggerFactoryUtil.getLogger("worker")

  def run(): Unit = ???

}
