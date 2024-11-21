Design Document for Distrobuted Sorting
---
## Terminologies and Definitions
* **Record**: 
  Each line, which consists of a key and value, in input files which are 
  generated by gensort.
* **Block**: 
  A file that has certain, designated amounts of records.
* **Chunk**:
  A file that has undeterminned amounts of records.
* **Partition**: 
  A certain range of keys, like [begin, end). It can sometimes refer to an block
  that holds the records that are in the partition. For example, when a sentence
  says "A partition [x, y) is sent to another worker machine...", what it 
  actually says is "A block that holds records within partition [x, y) is sent 
  to...".

## Data Structures

### Scala Classes and Objects

#### `class Record`
A record object represents a record in the file produced by gensort. It consists
of two integer values, `key` and `index`. It also has comparator methods (or, 
operators) like `<`, `>`, `<=`, `>=` defined to convenient comparison between
them while sorting. The comparison between records shall be done only by their
keys in lexicographical order. i.e. 
`Record(key = 34, ...) > Record(key = 12, ...)` must return 
`true`. 

#### `class Block`
A block is a file with designated size, which can be sorted. It has private
member that refers to the path to this block. Also, it has functional `sorted()` 
method which sorts this block and returns another block that is sorted and 
copied into the temporary directory. For example, If 
`Block("input/file.1").sorted() == Block("output/tmp/partition.1")`, then
the result of sorting `input/file.1` is stored in `output/tmp/partition.1`.

Also, it has `read()` method that reads records from the file assigned to the 
block, and returns the stream of records (i.e. the return type will be
Stream[Record]) in the file. 

### Protobuf Services and Messages

#### `SortingService`
<!-- TODO: Is an acknowledgement signal needed? -->
In `SortingService` service, there is a remote procedure whose name is 
`startSorting()`. `startSorting()` takes no argument and retuns `done` 
message. `done` is a dummy return value indicating that the sorting 
procedure in each worker machine is finished.

#### `SamplingService`
In `SamplingService` service, there is a remote procedure whose name is 
`requestSamples`. `requestSamples()` takes `sampleRequest` message which holds 
the number of samples as an argument, returning the `sampleResponse` message 
which holds the actual samples.

#### `ShufflingService`
Has a remote procedure `sendChunk()` which sends a chunk and returns 
acknowlegment to ensure the chunk has been sent successfully. 

#### `MergingService`
Has a remote procedure `startMerging()`. Like `startSort()`, it takes no 
argument and returns `done` message. `done` is a dummy return value indicating
the merging procedure in each worker machine is finished.

## Phases
The whole procedure will be..
1. Sort each blocks in each worker machine concurrently.
2. Scan whole records in each worker machine, making the samples.
3. Compute appropriate partition for each worker machines.
4. Determine the partition where each records should go into, and send the 
   chunks corresponding to the partition.
5. Merge each blocks in each worker machines, creating a chunk of whole sorted
   records. 

Hence, we split the whole procedure into phases, which are units of tasks that
depends on one another. Because the phases depends on one another, their orders
must not be changed. In other words, a set of phases must be executed 
sequentially, while each phases can be executed concurrently.

### Sorting Phase
In this sorting phase, each worker machine starts sorting each blocks in its own
input directories. The master sends start signal to each workers as a remote
procedure call and the workers send done signal by the return `Future` of the
remote procedure.

The workers internally sorts each blocks in the input directory. It first has
to recognize all the blocks in the input directory. Then, it assigns the sorting
task to designated number of thread, or `Future` blocks so that each threads
can sort those blocks concurrently. If one thread get its job done, the worker's
main thread should re-assign a sorting task to each `Future`.

If all `Future` got its job done and there's no remaining block to be sorted,
then the sorting phase of the worker is finished. Then it sends (above 
mentioned) done signal back to the master.

The new file name of those sorted block should be `file.sorted` if the original
file name was `file`. If the input file was `data1/input/hello` and the output
directory is `/output/`, then the full path to the sorted file shall be 
`/output/tmp/data1/input/hello.sorted`.

### Sampling Phase
In this sample phase, each worker machine takes a sample of input dataset and 
sends it to master machine. In each blocks, the worker machine takes certain
number of samples and merge them into a chunk of records. Then the worker sends
this chunk. The master machine takes this chunks of each worker machines and 
merge them into a chunk. Finally, the master machine sorts the chunk and divide
the whole key range into ranges assigned to each worker machine.

When taking n samples among N records in a block, the worker takes 0th, 
k th, 2k th, ..., nk th records where k = N / n. The workers only picks the
key of each records since the value is useless when deciding the partitions.

After sorting in the master machine, it decides the whole partitions of the 
worker machines from [Chunk(0), Chunk(n / 10)), 
[Chunk(n / 10), Chunk(2 * n / 10)), ..., [Chunk(9 * n / 10), Chunk(n)) where
we denote n th element of the sorted chunk by Chunk(n).

### Shuffling Phase
The shuffling phase is the phase where the worker machines exchange the records
with one another. By the partition from the sample phase, worker machines split
the records into each partitions and send them to corresponding worker machine.
Each worker machine should also receive partitions from other workers. Hence,
each worker machine needs at least two execution flow to receive records while
sending them to other workers.

<!-- TODO: Instead of splitting blocks into several partitions, how about just
           sending the stream of records to the target worker machine? -->

More specifically, each worker machine should divide each blocks into 
serveral partitions, sending them to the target machine. After sending all 
partitions within a block, the block should get deleted. Those partitions
go directly into the output directory.

Actually, the workers do not divide blocks into several files. Instead, it
keeps in-memory structure per block that is about where a partition starts and 
where it ends. Also, it should keep flags that indicate whether a partition of a 
block is sent or not. After shuffle phase, the `tmp` directory should be 
removed. 

For the consistency of the implementation, the partition corresponding to this
worker machine should be sent as if it were to other machines. In other words,
sending partition to another machine and moving a partition from `tmp` to the
parent directory must be handled with a single remote procedure call.

### Merging Phase
In the merging phase, each worker machine sorts all the records globally and 
merges them into a single sorted set of blocks. First, it makes a queue of 
blocks that contains current list of blocks. Then, it picks first two block in 
the queue and assign them to a `Future`. After the `Future` merged those blocks,
it pushes the merged block into the queue, and again takes first two blocks
in the queue. This process is repeated until there's only one block left in the
queue. 

The procedure of merging two blocks merges them into a single file by general
merge algorithm between two sorted blocks. Internally, the blocks in the queue 
only holds key and index of the record. Each `Future`s merges this sequence of
key and index pair (whose type might be `Seq[(int, int)]`) and push it back
to the block queue. When there's only a single block left in the queue, the main
thread reads all the indices in the final block and writes corresponding record
into the target file.

This phase neither needs the communication between a worker and the master, nor
the communication between workers. Hence, the master only signals worker machine
to start the merging phase by a start signal, and receives done signal from each
workers.