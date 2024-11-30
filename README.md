POSTECH CSED332 Team Project
---
Let's implement distrobuted sorting!

## Milestones

<details>
<summary> Milestones for previous weeks </summary>

### Week 1
* Learn about libraries such as [gRPC](https://grpc.io/docs/languages/go/basics/), [Protobuf](https://protobuf.dev), and [Future class](https://docs.scala-lang.org/overviews/core/futures.html) of Scala.
* Plan overall design of the program.
  - What classes, objects, functions, enums to introduce?
  - How should master and worker machine communicate?
* Set up Git repository.

### Week 2
* Keep studying on important notions and usages of libraries.
* Write down concrete design of the program.
  - What classes to introduce?
  - What will be the interfaces of those classes?
  - In what methods should master and worker machine communicate?
  - How should we exploit parallelism on each machine?
* Make out some of unit test cases based on the interface.
* Survive on the midterm exam (Good Luck!).

### Week 3
* Collect some more ideas, if any.
* Milestone Specification
* ~~Start to code - not necessarily.~~
  - If the design is not complete yet, i.e. we don't have (at least) specified interfaces, or there's some ambiguity on it, delay to code.
* ~~**Important**: Start difficult part early if we decided to start implementing.~~
  - This might be 'shuffle' part...
* ~~Make some more unit tests according to the design, revise them if it became old.~~



### Week 4
* Study the required libraries for implementation and work on individual design components:   
  our team agrees with the premise of the Mythical Man-Month, deciding to allocate significant time to studying and planning, recognizing the importance of thorough preparation in avoiding inefficiencies during implementation.😵
* Share individual design components, and explore better solutions for the project together.
* Design and Implementation Plan Specification.


### Week 5
* Running and debugging implementation environment settings and test code
* overall design plan (in progress)
* How do I do the integration test?
* Discuss the issues you faced during your work.



### Week 6
* **Prepare for presentation!**
* Add *details* to the finalized design.
  - When too much data is concentrated in one partition.
  - When disk overflow occurs on worker machines during shuffling.
  - Defining services for communication between machines using a proto file.
  - Shuffling algorithm
* Distribute coding tasks based on the finalized design. (based on phases)
* Coding!
  - **overall phase**
    1. Master can send phase service.
    2. Each worker can performs different tasks based on the phase flag.


</details>

### Week 7

**Design changed:** So, the implementation plan below differs from our actual implementation.
* Continue coding while commiting your code to your Git branch.
  - Implement the services defined in the proto file on the worker.
  - ~~**sorting phase:**~~
    1. ~~A Worker can sort data in its disk.~~(Design changed)
    2. Workers can send sample data to the master.
  - **sampling phase:**
    1. ~~Master can sort data in its disk too.~~(Design changed)
    2. Master can distribute partitions.
  - **shuffling phase:**
    1. Workers can connect workers
       10 workers communicate each other.   
       A worker server can receive 10 workers client (included itself) request.
    2. Workers can detect capacity overload on their own disks.
    3. handling capacity overload.
  - **merging phase:**
    1. Workers can merge multiple blocks into a single file while maintaining the order based on the keys.
* Execute and debug the code.

<details>
<summary> Milestones for next weeks </summary>

### Week 8
* Another debugging week.
  - Identify edge cases to catch bugs.
* Create test cases to validate overall program.
  - Load Imbalance (uneven distribution of data)
  - Network bottleneck
  - Data consistency
* Prepare for final presentation.
  - Summarize our experience through storytelling
  - Write final report

</details>

## Weekly Progresses

<details>
<summary> Progresses of previous weeks </summary>

### Week 1
* Set Git repository up.
* Done some of documenting, such as writing down milestones.
  - Not sure this will go as we planned...
* How to communicate/store temporal documents about the project?
  - Notion? In-repo markdown? Kakaotalk? Or some other method?
* Planned to have regular meeting on Saturday.
* Expected problems:
  - How to serve/receive records in parallel manner?
    - Readers/Writers problem, Producer/Consumer problem...
    - How can we model the problem as a well-known problem?
  - Index file might be shared smong threads on a machine. How should we ensure consistency of this data structure?
  - How to exploit parallelism while merging locally?

### Week 2
- **Saturday Regular Meeting**
  - Held a regular team meeting on Saturday to discuss progress and clarify next steps.

- **Learned Concepts and Libraries**

  - **1. In-depth Study of gRPC and Protobuf**
    - **Service Definition**: Defined services and message structures in `.proto` files.
    - **gRPC Streaming**: Utilized bidirectional streaming between the master and worker nodes.
    - **Load Balancing**: Discussed how to distribute tasks efficiently when multiple workers are involved.
    - **Error Handling**: Explored gRPC error codes and retry strategies to handle failures gracefully.

  - **2. Scala's Future and Parallel Programming**
    - **Future**: Wrote asynchronous code with callbacks to improve non-blocking execution.
    - **Promise vs Future**: Investigated how `Promise` allows setting values at a specific point in time.
    - **ExecutionContext Setup and Usage**: Optimized thread pools for efficient execution.
    - **Concurrency Issue Resolution**: Applied lock-free mechanisms and used `synchronized` to ensure thread safety.

  - **3. Theoretical Background of Distributed Sorting**
    - **MapReduce Concept**: Studied the MapReduce framework for processing data in a distributed environment.
    - **Parallel Sorting Algorithms**: Examined how to implement Merge Sort and Quick Sort in parallel.
    - **Shuffling Optimization**: Optimized data redistribution among worker nodes to improve efficiency.

- **Preparation for OS Project 2 Presentation**
  - Good luck to everyone on their OS Project 2 presentations! 💪

### Week 3
- **Saturday Regular Meeting**
  - **Commit Convention**
     Our team has agreed to use the following commit format:   

    - **Feat**: Add new features
    - **Fix**: Bug fixes
    - **Docs**: Documentation changes
    - **Style**: Code formatting, missing semicolons, etc., without affecting functionality
    - **Test**: Add or refactor tests (no changes to production code)
    - **Chore**: Update build tasks, configure package manager, etc., without changes to production code

    Examples:
    
    ```
    Feat: Implement sample sorting algorithm
    Fix: Correct partitioning logic in sample sort
    Docs: Add documentation for sample sorting approach
    Style: Reformat sample_sort.cpp for better readability
    Test: Add test cases for sample sort edge cases
    Chore: Update Makefile to include sample_sort tests
    ```

  - **Communication Tools**
     - Decide whether to use Discord for communication.
     - Continue using KakaoTalk and Google Docs for documentation and discussions.

  - **Implementation Strategy**
     - Discuss how to proceed with the overall implementation.
     - Learn how to use required libraries and tools.

  - **Team Roles and Responsibilities**
     - Assign roles for research, study, and idea generation.
     - For this week, everyone will focus on learning library usage and contributing to design ideas.
     - Once the design becomes more specific, roles will be assigned as follows: A will handle XX class, B will work on YY component, C will take care of ZZ, etc.

  - **Weekly Plan Sharing**
     - Create a separate Google Doc each week to discuss progress.
     - Summarize discussions and update the README Progress section every Sunday.
     - Create a new folder named "Software Design Methods" to collect all plans and progress.

  - **Action Items**
    - All Members: Study library usage and propose design ideas by the end of the next week.
      - gRPC and Protobuf Study
        - Follow the Java Quickstart guide for gRPC: [gRPC Java Quickstart](https://grpc.io/docs/languages/java/quickstart/)
        - Study Protobuf using the Java tutorial: [Protobuf Java Tutorial](https://protobuf.dev/getting-started/javatutorial/)
      - Sample Sorting Algorithm
        - Learn about the sample sorting method: [Samplesort on Wikipedia](https://en.wikipedia.org/wiki/Samplesort)
      - Scala Concurrent Programming Libraries
        - Study how to use Scala's Concurrent programming libraries, `Future` and `Promise`: [Futures in Scala](https://docs.scala-lang.org/overviews/core/futures.html)
    - All Member: Cluster Access Permission
    - by **Doyoung**: Set up a Google Doc directory for tracking this week's progress.



### Week 4
- [Meeting Minute of This Week](https://docs.google.com/document/d/1_xKZGVFijjB520F2Ul53MoYmUl4QtC2KAgsxgZ_nGt0/edit?usp=sharing)
- Decided next week's meeting schedule to gather up and start to code.
  - Thursday 9:30 PM, in GSR of school library.
- Decided to make sample program before starting to implementing the actual one.
  - Decided on the concrete interface of it.
    - Two executables: `master` and `worker`
    - `master` and `worker` shall work with same arguments of the actual 
      program.
      - i.e. `master` should be invoked like `master 5`, and `worker` should be
        invoked like `worker -I foo -O bar`
    - However, the operation of them are somewhat different.
      - Instead of actual distrobuted sorting, master sends two random integers
        to the workers, and workers perform random computations on it and send
        it back.
    - The master prints the IP address and port of itself, and prints the 
      ordering of the workers, sorted by the values received.
    - This will help us understand the concrete operation on gRPC and Protobuf,
      and concurrency in Scala.
- Decided whom to take responsibility of designing whole system, and whom to 
  take responsibility of supporting him (by the surgical team model of *the 
  Mythical Man Month*).


### Week 5
- [Meeting Minute of This Week](https://docs.google.com/document/d/1RkFKvAxPYGVAnsNgUA4w1OFz7I9jFjmnB0VoF5iNqqQ/edit?usp=sharing)
- Held a team meeting via Zoom on Saturday.

#### Tasks Completed This Week
- **IntelliJ SSH Connection and Deployment Setup**
  - (Completed) Copied public keys to enable SSH key-based access to each Worker machine.
- **Shell Script for Master to Manage Worker Machines**
  - (Completed) Installed `gensort` on all Worker machines, generated test data, and verified outputs.
- **Test Code for Master-Worker Communication**
  - Successfully implemented and tested Request-Response communication between Master and Worker.
- **Presentation Preparation**
  - Assigned roles for preparing the presentation.
  - Presenter : Doyoung Kim, Materials Prepared By : Duhong Kwon, SoonHo Kim

#### Overall Design of the System
- [Design Proposal](https://github.com/kimdoyoung0319/332project/blob/doyoung/DESIGN.md)
- **Defined Phases and Protobuf Integration**
  - Defined each phase required for the system and outlined Protobuf services and messages for each phase.
  - In the Sample Phase, Worker machines sort data locally, access indices in strides, and send the sample list to the Master.
  - Further discussions planned to refine the final design.



### Week 6
- Held a team meeting via Zoom on Sunday.
- The handling of disk overflow has been decided to be implemented after completing the entire system.
- [The implemented code can be viewed here.](https://github.com/kimdoyoung0319/332project/tree/doyoung)

#### Changes of Overall Design
1. The design, which proceeded in the order of **sorting, sampling, shuffling, and merging** has been revised.   
   -> **Change**: The design now proceeds in the order of **sampling, shuffling, and sorting**.
2. At sampling phase, after sorting the worker's data, one sample from each distribution range was sent to the master.   
   -> **Change**: A random sample from the worker's data is now sent to the master. **(No worker sorting before sampling)**

#### Tasks Completed This Week
1. **Defined proto files for communication between master and worker nodes.**
   - common.proto, master.proto, worker.proto 
     - **common.proto** defines types that are used commonly in both master and worker. 
     - **master.proto** defines the requests that workers send to the master. 
     - **worker.proto** defines requests that the master sends to workers and requests between workers.
2. Worker sends its IP and port to the master, the master responds with an ID.
3. **Implemented the following processes in the master(Tested):**
   - Once all workers are registered, the master initiates sampling.
   - After collecting sample data, partitioning and shuffling are requested. 
   - Once shuffling is complete, sorting is requested. 
   - After sorting, the entire process concludes.
4. **Implemented the sampling phase(Tested):**
   - Each worker extracts a sample and sends it to the master, which then creates partitions (range information) and responds accordingly. 
5. **Implemented the shuffling phase(Not tested yet):**
   - Each worker is assigned an ID, and partitions are mapped to IDs. 
   - Workers split their data into blocks based on partitions and store them in a directory **/temp**. They then send blocks corresponding to their mapped partition to other workers. 
   - StreamObserver is used for handling streaming data. 
   - Each worker requests data from other workers using its ID and receives responses accordingly. 
   - All responses from workers are combined into a single future, marking the completion of shuffling. 
     - Shuffling is implemented asynchronously(**concurrently**) using futures and promises.

#### Next Week's Plan
1. **Debug shuffling phase**
   - Although the shuffling phase has been implemented, it requires testing with proper logging.
2. **Implement sorting phase**
   - The sorting phase still needs to be implemented and will be handled using external disk sorting.
3. **Test entire process**
   - Once sorting is completed, the entire process will be finished.
4. **Add logic of overflow handling**
   - Implement handling for disk overflow and memory overflow scenarios.


</details>

### Week 7

- [The implemented code can be viewed here.](https://github.com/kimdoyoung0319/332project/tree/doyoung)
- [Held a team meeting via Microsoft Teams on Saturday.](https://docs.google.com/document/d/1J1NHS6zjDWwW70XZ3Qp0CNE-8jWbbqu0WN_dxpdDSLY/edit?usp=sharing)

#### Overall Design Summary
1. **Init Phase:**
   - The master is initially executed, and its IP and port are assigned. 
   - Workers are executed, send their IP and port to the master, and receive their IDs. 
2. **Sampling Phase:**
   - Each worker collects the first record from their input blocks (assumed to be 32 MiB each) as samples and sends them to the master. 
   - The master determines the range based on the received sample data and informs the workers. The workers' IDs are mapped to ranges.
3. **Shuffling Phase:**
   - Each input block is sorted before shuffling.
   - The blocks are divided based on the assigned ranges.
   - Based on the mapping of IDs to ranges, the divided data is sent to the corresponding worker IDs.
   - Once all transfers are complete, the workers notify the master.
4. **Merging Phase (refer to meeting notes):**
   - Assign an index to each block.
   - Read one line from each block and insert it into a priority queue, ordering based on the key.
   - While queue is not empty:
     - Dequeue to output the record with the smallest key.
     - Write the dequeued record to the output file.
     - Read another line from the block that contained the dequeued record and insert it into the priority queue.
   - The merging is complete, notify the master.

#### Tasks Completed This Week
1. **Completed the overall system implementation according to the design.**
   - Verified that the output file is correctly sorted using valsort.
   - Performed testing using **64MB, 320MB** input data on each of the three worker machines.
2. Implemented debugging logs that are printed by adding **--debug** to the program execution command.
3. **Automated all processes** using a **shell script**, allowing the master to perform all phases.
   - Generate input data on each worker machine using gensort.
   - After the master executes, each worker is automatically executed.

#### Next Week's Plan
- Make the Merge Phase Concurrent for improved performance
  - Current implementation uses a single thread to handle the entire merge process.
- Enforce mimimum sample size.
  - Currently, the sample size varies according to the number of blocks.
- Change code portion where it loads all the block contents into the memory.
- Clean targetDir/temp and targetDir/sorted directory after the sorting phase.
- Test system stability with 10 worker machine and large data.
- **Prepare final presentation.**
<details>
<summary> Progresses for next weeks </summary>

### Week 8

</details>
