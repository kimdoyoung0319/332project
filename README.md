If you want to check this week's progress, please read [PROGRESS.md](https://github.com/kimdoyoung0319/332project/blob/main/PROGRESS.md)!

## 공통 (셸 스크립트 또는 수동 실행)
분산 시스템을 실행하기 위해 Shell script로 간단한 UI를 설계했다. Menu를 차례대로 실행하면 원하는 동작을 수행할 수 있다. 
문제 상황이 발생할 것을 고려하여 수동 실행 방법도 함께 설명해두었다. (하단 토글 참고)

### 0. Master Machine에 Project를 불러온다. (공통)
```
git clone https://github.com/kimdoyoung0319/332project.git
```    
&nbsp;   

## SHELL SCRIPT를 사용해서 자동 실행

### 1. 프로젝트 내부 scripts 디렉토리로 이동
(절대경로 예시: /home/blue/332project/scripts/)로 이동한다.

    cd ~/332project/scripts

### 2. 명령어를 통해 manage_workers 스크립트 실행
./manage_worker.sh로도 실행 가능하다. 

    bash manage_workers.sh

### 3. 스크립트 실행 이후 메뉴 확인
    ==================== MENU ====================
    1. Check Worker Status
    2. Init worker environment
    3. Start Master Process
    4. Validate Sorted Data
    5. Reset ~/output Directory
    0. Exit
    ==============================================
    Select an option:

각 Menu 항목의 기능은 다음과 같다.
   - 1 ) 현재 Master Machine에서 Worker Machine 간의 통신이 원할한지 확인 (Ping check)
   - 2 ) 각 Worker Machine(2.2.2.101~2.2.2.110)을 순회하면서 project폴더를 git clone하고, output 디렉토리를 생성
   - 3 ) [**MAIN**] Master Machine을 시작 (정렬 시작)
   - 4 ) 각 Worker Machine의 output 폴더에 출력된 결과에 대해 정렬 상태를 확인
   - 5 ) Master Machine 재실행을 위한 output directory 초기화

### 4. 마스터머신과 워커머신의 연결 상태 확인
(1)을 실행한다. 각 Worker Machine에 대해 Ping을 check하고, ssh 명령어를 통해 remote 실행이 가능한지 확인한다.
    
    Select an option: 1

    Checking status of Worker $ip...
    Worker $ip is reachable.
    ~~~

### 5. 각 worker machine에 대한 Git clone 및 초기 환경 세팅
(2)를 실행한다. worker machine에 "~/project332"와 "~/output"이 생성되고, 실행을 위한 초기 환경이 구성된다. 

    Select an option: 2 

    Starting Git repository reset and output directory setup on all workers...
    Resetting Git repository and output directory on $WORKER_IP...
    Removing existing 332project directory...
    Cloning fresh repository...
    ~~~

### 6. [MAIN] 분산정렬 시작 
(3)을 실행한다. Master Machine의 Server를 동작하고, 분산 정렬을 위한 모든 동작이 차례대로 수행된다. 
이 때 Shell에서 2개의 인자를 입력받는다. 
- 첫번째 인자는 사용한 Worker개수, 두번째 인자는 input_data의 **절대경로**를 넣어주어야 한다. 
  - First argument(count) : **10** 으로 고정
  - Second argument(inputPath) : {Test_input_direcory_Absolute_Path}

만약 10보다 작은 값 n을 넣게 되면 worker machine은 2.2.2.101~2.2.2.10n 에 해당하는 n개의 머신을 기준으로 동작한다. 
이어지는 테스트 예시에서 Input Path는 /home/blue/dataset/{small, big, large}라고 가정한다. 

inputPath가 주어지면, Master의 함수 내부에서 자체적으로 shell script를 통해 각 Worker 머신을 ssh 명령어로 실행한다. 
 
    Select an option: 3

    Enter the number of worker machines to operate (1-10): 10 
    Enter the input path (absolute path required): /home/blue/dataset/big
    Starting Master process with 10 workers and input path /home/blue/dataset/big...
    
    ~~~
    (Master Machine Execution)
    (Worker# Machines Execution)
    ~~~
    
    Results example:
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

### 7. (선택) 분산 정렬 완료 후, 결과 확인
Menu의 (4)번을 실행하면, 각 Worker들이 정렬 과정에서 Master Machine으로부터 할당받은 ID순서대로 valsort를 사용하여 결과를 출력한다.

위 출력 결과를 통해 분산정렬이 제대로 동작했는지 확인할 수 있다.

    Select an option: 4

    Starting validation process for all workers...
    Processing worker ID: 0 with IP: 2.2.2.101
    ~~~

### 8. (선택) 재실행을 위한 output directory 초기화
한 번의 분산 정렬 실행 된 이후, 새로 분산 정렬을 돌리기 위해서는 기존의 output 결과를 제거해주어야 한다. 
Menu에서 (5)를 실행하면 각 Worker머신의 ouput 디렉토리를 초기화 해준다. 

    ==================== MENU ====================
    1. Check Worker Status
    2. Init worker environment
    3. Start Master Process
    4. Validate Sorted Data
    5. Reset ~/output Directory
    0. Exit
    ==============================================
    Select an option: 5

.

    [Result Example]
    Resetting ~/output directory on all workers...
    Processing worker: 2.2.2.101
    Worker 2.2.2.101: ~/output directory reset completed.
    ~~~

    All workers' ~/output directories have been reset.


&nbsp;

## 셸이 동작안하는경우(수동 실행)
만약 Shell script가 동작하지 않는 경우를 대비하여, 각 Machine에서 직접 코드를 동작하는 방법을 설명한다. 

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
