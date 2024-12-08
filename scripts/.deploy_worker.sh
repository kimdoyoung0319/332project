#!/bin/bash

WORKER_IPS=("2.2.2.101" "2.2.2.102" "2.2.2.103" "2.2.2.104" "2.2.2.105" \
            "2.2.2.106" "2.2.2.107" "2.2.2.108" "2.2.2.109" "2.2.2.110")

# Worker IP 리스트
#n=${#WORKER_IPS[@]}

MASTER_IP=$1
MASTER_PORT=$2
INPUT_PATH=$3
COUNT=$4

echo
for((i=0; i < COUNT; i+=1)); do
  worker_ip=${WORKER_IPS[i]}

  echo "Connecting to worker at $worker_ip..."

  ssh blue@"$worker_ip" "
      JAVA_PATH=\$(which java) && \
      SBT_PATH=\$(which sbt) && \
      export PATH=\$(dirname \$JAVA_PATH):\$PATH && \
      cd ~/332project && \
      nohup \$SBT_PATH \
        -J-Xmx14G -J-Xms14G -J-XX:+UseG1GC \
        \"runMain worker.Main $MASTER_IP:$MASTER_PORT -I $INPUT_PATH -O /home/blue/output/\" \
        > worker_ssh.log 2>&1 & \
      echo 'Worker started successfully on $worker_ip.'
  " &
done

#runMain worker.Main 10.1.25.21:46456 -I /home/blue/dataset/big -O /home/blue/output

#  ssh blue@"$worker_ip" "
#    export PATH=/home/cse/openlogic-openjdk-8u422-b05-linux-x64/bin:\$PATH && \
#    cd ~/332project && \
#    nohup /home/cse/sbt/bin/sbt \
#      \"runMain worker.Main $MASTER_IP:$MASTER_PORT -I /home/blue/dataset/$SIZE -O /home/blue/output/\" \
#      > worker_ssh.log 2>&1 & \
#    echo 'Worker started successfully on $worker_ip.'
#  " &