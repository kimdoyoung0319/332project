POSTECH CSED332 Team Project
---
Let's implement distrobuted sorting!

## Milestones

### Week 1
* Learn about libraries such as [gRPC](https://grpc.io/docs/languages/go/basics/), [Protobuf](https://protobuf.dev), and [Future class](https://docs.scala-lang.org/overviews/core/futures.html) of Scala.
* Plan overall design of the program.
  - What classes, objects, functions, enums to introduce?
  - How should master and worker machine communicate?
* Set up Git repository.

<details>
<summary> Milestones for next weeks </summary>

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
* Start to code - not necessarily.
  - If the design is not complete yet, i.e. we don't have (at least) specified interfaces, or there's some ambiguity on it, delay to code.
* **Important**: Start difficult part early if we decided to start implementing.
  - This might be 'shuffle' part...
* Make some more unit tests according to the design, revise them if it became old.

### Week 4
* No matter how late we are, we should start implementing in this week. 
* Introduce new unit tests.
* Test classes, functions, methods, objects and debug them as implementing them.

### Week 5
* Keep implementing and coding...
* How should we do integrated testing?
* Discuss about problems we face while working on it.

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
### Week 1
* Set Git repository up.
* Done some of documenting, such as writing down milestones.
  - Not sure this will go as we planned...
* How to communicate/store temporal documents about the project?
  - Notion? In-repo markdown? Kakaotalk? Or some other method?
* Planned to have regular meeting on Saturday.
* Expected problems:
  - How to serve/receive records in parallel manner?
  - Index file might be shared smong threads on a machine. How should we ensure consistency of this data structure?
  - How to exploit parallelism while merging locally?

<details>
<summary> Progresses for next weeks </summary>

### Week 2
### Week 3
### Week 4
### Week 5
### Week 6
### Week 7
### Week 8

</details>