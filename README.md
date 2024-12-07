If you want to check this week's progress, please read [PROGRESS.md](https://github.com/kimdoyoung0319/332project/blob/main/PROGRESS.md)!
## 공통(셸 스크립트 or 수동실행)

### 0. 프로젝트를 마스터머신에 불러온다.
```
git clone https://github.com/kimdoyoung0319/332project.git
```
---------------

## SHELL SCRIPT를 사용해서 자동 실행

### A. 프로젝트 내부 scripts 디렉토리로 이동한다.(절대경로: /home/blue/332project/scripts/)로 이동한다.

    cd /home/blue/332project/scripts

### B. 명령어를 통해 manage_workers 스크립트를 실행시킨다.

    bash manage_workers.sh

### C. 스크립트를 실행시키면 1~5번의 선택지를 확인할 수 있다.
    ==================== MENU ====================
    1. Check Worker Status
    2. Init worker environment
    3. Start Master Process
    4. Validate Sorted Data
    5. (AUX) Developer Menu
    0. Exit
    ==============================================
    Select an option:
   - 1번: 현재 마스터머신에서 워커머신으로 통신이 원할한지 확인한다.
   - 2번: 소스파일이 포함된 project폴더를 git clone해준다.
   - 3번: 마스터머신과 워커머신간 분산정렬 시스템을 실행한다.
### D. 먼저 1번을 실행해 마스터머신과 워커머신의 연결이 원할한지를 확인한다.(ssh가 가능한지 확인)
    Select an option: 1
    Checking status of Worker $ip...
    Worker $ip is reachable.
    ~~~
### E. 2번을 실행해 각 워커머신에 프로젝트를 Git으로부터 불러온다.
    Select an option: 2    
    Starting Git repository reset and output directory setup on all workers...
    Resetting Git repository and output directory on $WORKER_IP...
    Removing existing 332project directory...
    Cloning fresh repository...
    ~~~
### F. 3번을 실행해 분산정렬을 시작하면된다. (Input Path는 /home/blue/dataset/{small, big, large}라고 가정한다.)
    Select an option: 3
    Enter the number of worker machines to operate (1-10): 10 #(실행할 워커머신 수)
    Enter the input path (absolute path required): /home/blue/dataset/big
    Starting Master process with 10 workers and input path /home/blue/dataset/big...
    ~~~
    (마스터 실행)
    (워커 실행)
    ~~~
    결과 예시:
    36:32 INFO  master - The whole procedure finished. The order of the workers is...
    36:32 INFO  master - [1] 2.2.2.106:36849
    36:32 INFO  master - [0] 2.2.2.107:39431
    36:32 INFO  master - [2] 2.2.2.101:42361
    36:32 INFO  master - [3] 2.2.2.104:38099
    36:32 INFO  master - [4] 2.2.2.110:45263
    36:32 INFO  master - [5] 2.2.2.105:46159
    36:32 INFO  master - [6] 2.2.2.102:43117
    36:32 INFO  master - [7] 2.2.2.108:36053
    36:32 INFO  master - [8] 2.2.2.103:38783
    36:32 INFO  master - [9] 2.2.2.109:44311
    36:32 INFO  master - Shutting down master server...

### (선택) G. 분산 정렬 완료 후 4번을 통해 분산정렬이 제대로 동작했는지를 확인할 수 있다.
    Select an option: 4
    Starting validation process for all workers...

    Processing worker ID: 0 with IP: 2.2.2.101

### (선택) H. (분산 정렬 후) 분산정렬이 끝나고 새로 분산정렬을 돌리기 위해서 output파일을 비워준다. (5번 실행)
    ==================== MENU ====================
    1. Check Worker Status
    2. Init worker environment
    3. Start Master Process
    4. Validate Sorted Data
    5. (AUX) Developer Menu
    0. Exit
    ==============================================
    Select an option: 5
#### 4번을 실행해 output directory를 리셋한다.

    ==================== Developer Menu ====================
    1. Distribute .bashrc
    2. Distribute gensort,valsort
    3. Generate gensort data for all sizes
    4. Reset ~/output Directory
    5. Update Git Repos
    0. Exit
    ========================================================
    Select an option: 4

#### 실행화면:

    Resetting ~/output directory on all workers...
    Processing worker: 2.2.2.101
    Worker 2.2.2.101: ~/output directory reset completed.
    ~~~
    All workers' ~/output directories have been reset.


## 셸이 동작안하는경우(수동 실행)

<details>
  <summary>수동 실행 방법</summary>

### 1. 전처리 작업

--------------
#### 0. 각 워커머신의 blue directory에서 git cloning한다.

---------
### 2. 마스터

---------

#### 2-1. 프로젝트 디렉토리로 이동한다.(절대경로: /home/blue/332project/)로 이동한다.
    cd /home/blue/332project/
#### 2-2. sbt를 실행한다.
    [blue@vm-1-master 332project]$ sbt
#### 2-3. 마스터머신을 실행한다.
    sbt:distrobuted-sorting> runMain master.Main 10
#### grpc용 마스터 IP와 포트를 출력하는 것을 확인할 수 있다.
    [info] running master.Main 10
    07:35 INFO  master - Master server listening to 33632 started.
    10.1.25.21:33632



### 3. 워커 (10개의 워커에 각각 접속해 실행해주어야함.)

---------


#### 3-1. 프로젝트 디렉토리로 이동한다. (절대경로: /home/blue/332project/)로 이동한다.
    cd /home/blue/332project/
#### 3-2. sbt를 실행한다.
    blue@vm01:~/332project$ sbt
#### 3-3. worker머신을 실행한다. 
- 실행 명령어는 "runMain worker.Main [MasterIP]:[MasterPort] -I [Inputdir] -O [Outputdir]"다.
- MasterIP와 MasterPort는 마스터를 실행하면 확인할 수 있다.
- Inputdir과 Outputdir은 절대경로를 입력해야한다.

```
sbt:distrobuted-sorting> 
runMain worker.Main 10.1.25.21:33632 -I /home/blue/dataset/small -O /home/blue/output

16:31 INFO  worker - Worker server listening to port 38069 started.
```
#### 모든 워커머신에 대해 실행이 끝나면 분산정렬 시스템이 가동한다.
</details>