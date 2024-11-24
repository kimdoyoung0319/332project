package worker

/* Disk-based merge sorting!
     Input: A sequence of input files, target directory for output.
     Output:
      (In-memory) Unit
      (In-disk) Sorted partitions named partition.1, partition.2, ...
                Input directory should be removed.
 */

/* 1. Sort each blocks in the block list.
     - This procedure must involve some level of parallelism.
   2. Put each sorted blocks in the queue.
   3. Assign two (or, k) blocks to each thread, or Future block.
     - Each thread will perform two-way merging, by taking the minimum among
       two blocks, and write it back to the buffer. When the buffer become
       full, i.e. become size of 32 MiB, write the buffer back to the disk.
     - After the two blocks are merged into a sequence of blocks, put them
       into the queue again. The sequence of the files should be treated as
       a single block.
     - If the future is completed, pop two blocks from the queue again and
       merge them according to above procedure again.
     - Synchronization on accessing the queue must be ensured. i.e. The
       procedure of pushing / popping must be atomic & each chunks must not
       have any pair of duplicated blocks.
   4. If the queue has only one chunk left in it and there's no Future
      performing the merge process, the procedure ends.
 */

/* Instance that performs external sorting among input blocks and writes them
   back to the directory specified by target. The level of parallelism is
   controlled by the parameter level. */
class Sorter(inputs: Seq[common.Block], target: os.Path, level: Int) {
  import scala.concurrent.Future

  assert(os.isDir(target))

  def run(): Future[Unit] = {
    import utils.globalContext

    val allocator = new utils.FileNameAllocator(target / "temp", "sorted")

    val blocks = Future.sequence {
      for (input <- inputs) yield Future {
        val sorted = input.sorted(allocator.allocate())
        input.remove()
        sorted
      }
    }

    ???
  }

  def merge(first: Chunk, second: Chunk): Chunk = {
    import scala.collection.mutable.Buffer

    val buffer = Buffer[Record]()

    ???
  }
}

/* A set of records that may not fit in the memory. */
class Chunk(files: Seq[os.Path]) {
  assert(files.forall(os.isFile(_)))

  val contents: geny.Generator[common.Record] =
    files.foldLeft(geny.Generator[common.Record]()) { case (accum, path) =>
      accum ++ common.Block(path).contents
    }
}

/* TODO: 1. Define Chunk type, which represents a set of files that does not
            fit in the memory. It should has contents() method, which returns
            a generator for records in the set of files.
         2. Define merge() method, which takes two chunks as the arguments and
            performs merging two files and returns new chunk as the result.
         3. Implement the routine which performs merging until there lefts just
            a single file.
 */
