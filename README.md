
SHELL 스크립트를 통해 마스터 워커 머신의 동작을 알아보자.


### 공통
1. 프로젝트를 마스터머신에 불러온다.
```
git clone https://github.com/kimdoyoung0319/332project.git
```
<SHELL SCRIPT를 사용해서 자동 실행>   

### 초기세팅
#### 1. root를 기준으로 프로젝트 내부 scripts 디렉토리로 이동한다.(절대경로: /home/blue/332project/scripts/)로 이동한다.
```
cd /home/blue/332project/scripts
```
#### 2. 명령어를 통해 manage_workers 스크립트를 실행시킨다.
```
bash manage_workers.sh
```
#### 3. 스크립트를 실행시키면 1~5번의 선택지를 확인할 수 있다.
    ==================== MENU ====================
    1. Check Worker Status
    2. Init worker environment
    3. Start Master Process
    4. (AUX) Developer Menu
    0. Exit
    ==============================================
    Select an option:
   - 1번: 현재 마스터머신에서 워커머신으로 통신이 원할한지 확인한다.
   - 2번: 소스파일이 포함된 project폴더를 git clone해준다.
   - 3번: 마스터머신과 워커머신간 분산정렬 시스템을 실행한다.
   - 4번: valsort를 통해서 각 output파일이 잘 정렬되었는지를 확인한다.
   - 5번: 추가기능을 사용한다. (gensort, output디렉토리 초기화 등)
#### 4. 먼저 1번을 실행해 마스터머신과 워커머신의 연결이 원할한지를 확인한다.(ssh가 가능한지 확인)
    Select an option: 1
    Checking status of Worker $ip...
    Worker $ip is reachable.
    ~~~
#### 5. 2번을 실행해 각 워커머신에 프로젝트를 깃으로 부터 불러온다.
    Select an option: 2    
    Starting Git repository reset and output directory setup on all workers...
    Resetting Git repository and output directory on $WORKER_IP...
    Removing existing 332project directory...
    Cloning fresh repository...
    ~~~
#### 6. 3번을 실행해 분산정렬을 시작하면된다. input path는 /home/blue/dataset/{small, big, large}라고 가정한다.
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
    
#### 7. (분산 정렬 후) 분산정렬이 끝나고 새로 분산정렬을 돌리기 위해서 output파일을 비워준다.(4번에서 실행)
    ==================== MENU ====================
    1. Check Worker Status
    2. Init worker environment
    3. Start Master Process
    4. (AUX) Developer Menu
    0. Exit
    ==============================================
    Select an option: 4
4번을 실행하고, output directory를 리셋한다.

    ==================== Developer Menu ====================
    1. Distribute .bashrc
    2. Distribute gensort,valsort
    3. Generate gensort data for all sizes
    4. Reset ~/output Directory
    5. Update Git Repos
    0. Exit
    ========================================================
    Select an option: 4

실행화면:

    Resetting ~/output directory on all workers...
    Processing worker: 2.2.2.101
    Worker 2.2.2.101: ~/output directory reset completed.
    ~~~
    All workers' ~/output directories have been reset.


### 셸이 동작안하는경우(수동 실행)

