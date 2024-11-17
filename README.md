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

</details>

### Week 5
* Running and debugging implementation environment settings and test code
* overall design plan (in progress)
* How do I do the integration test?
* Discuss the issues you faced during your work.

<details>
<summary> Milestones for next weeks </summary>
  
### Week 6
* **Prepare for presentation!**
* If we have not completed the first implementation, do it.
* Start integrated testing.
  - Soundness and completeness do matter.
    * Is there any missing record?
    * Is the ordering maintained on the final result?
    * Is there any edge case that makes code buggy?

### Week 7
* Debug! 
* I hope we've done initial implementing at this week...
  - Anyway we have to if we haven't done yet.

### Week 8
* Another debugging week.
* Prepare for final presentation.

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

</details>

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
- [Design Proposal](https://github.com/kimdoyoung0319/332project/blob/doyoung-example/design-proposal.md)
- **Defined Phases and Protobuf Integration**
  - Defined each phase required for the system and outlined Protobuf services and messages for each phase.
  - In the Sample Phase, Worker machines sort data locally, access indices in strides, and send the sample list to the Master.
  - Further discussions planned to refine the final design.

<details>
<summary> Progresses for next weeks </summary>

### Week 6
### Week 7
### Week 8

</details>
