#!/bin/bash

# Worker IP 리스트 (고정)
WORKER_IPS=("2.2.2.101" "2.2.2.102" "2.2.2.103" "2.2.2.104" "2.2.2.105" \
            "2.2.2.106" "2.2.2.107" "2.2.2.108" "2.2.2.109" "2.2.2.110")

# Worker 수 (고정)
n=${#WORKER_IPS[@]}

# size 별 설정 (순회할 값들)
declare -A size_map=(["small"]=2 ["big"]=10 ["large"]=100)
size_keys=("small" "big" "large")


# Aux function
function distribute_bashrc {
  # .bashrc 파일 경로
  local BASHRC_PATH="$HOME/.bashrc"

  # .bashrc 파일 존재 여부 확인
  if [[ ! -f $BASHRC_PATH ]]; then
    echo "Error: $BASHRC_PATH does not exist. Please check the file path."
    return 1
  fi

  echo "Distributing .bashrc file to all workers..."

  # Worker IP 리스트 순회
  for WORKER_IP in "${WORKER_IPS[@]}"; do
    echo "Sending .bashrc to $WORKER_IP..."
    scp "$BASHRC_PATH" "blue@$WORKER_IP:/home/blue/.bashrc"
  done

  echo "All workers have been processed."
}

function distribute_gensort_valsort {
  # gensort 디렉토리 초기화 및 파일 복사 함수
  echo "Initializing and copying files to all workers..."

  # gensort 및 valsort 실행 파일의 로컬 경로
  GENSORT_PATH="$HOME/deployment/gensort"
  VALSORT_PATH="$HOME/deployment/valsort"

  for WORKER_IP in "${WORKER_IPS[@]}"; do
    echo "Processing worker: $WORKER_IP"
    ssh -n blue@$WORKER_IP "rm -rf ~/gensort && mkdir -p ~/gensort"
    scp $GENSORT_PATH blue@$WORKER_IP:~/gensort/
    scp $VALSORT_PATH blue@$WORKER_IP:~/gensort/
    echo "Files copied to $WORKER_IP:~/gensort"
  done

  echo "Initialization and file copying completed."
}

function generate_inputdata {
  local size_key=$1
  local size=${size_map[$size_key]}

  echo "Running gensort for size: $size_key ($size), with $n workers."

  for worker_ip in "${WORKER_IPS[@]}"; do
    local ip_suffix=${worker_ip##*.}  # IP 끝자리 추출
    local offset_argument=$(( (ip_suffix - 101) * size ))

    ssh -n blue@$worker_ip "
      rm -rf ~/dataset/$size_key &&
      mkdir -p ~/dataset/$size_key &&
      cd ~/gensort &&
      for ((j = 0; j < $size; j++)); do
        offset=\$((320000 * ($offset_argument + j)))
        ./gensort -b\$offset -a 320000 ~/dataset/$size_key/input\$(($offset_argument + j))
      done
    "
    echo "Worker $worker_ip: Completed $size_key data generation  with offset base $offset_argument."
  done

  echo "Completed gensort tasks for size: $size_key."
}

function reset_worker_output {
  echo "Resetting ~/output directory on all workers..."

  for WORKER_IP in "${WORKER_IPS[@]}"; do
    echo "Processing worker: $WORKER_IP"
    ssh -n blue@$WORKER_IP "
      rm -rf ~/output &&
      mkdir -p ~/output
    " && echo "Worker $WORKER_IP: ~/output directory reset completed." || \
         echo "Worker $WORKER_IP: Failed to reset ~/output directory."
  done

  echo "All workers' ~/output directories have been reset."
}

function update_git_repos {
  local BRANCH="main"

  echo "Starting Git update on all workers for branch: $BRANCH"

  for WORKER_IP in "${WORKER_IPS[@]}"; do
    echo "Updating Git repository on $WORKER_IP..."

    ssh -n blue@"$WORKER_IP" "
      set -e
      cd ~/332project
      git fetch --all
      git checkout $BRANCH
      git pull origin $BRANCH
      echo 'Git update completed on $WORKER_IP.'
    " && echo "Successfully updated Git repository on $WORKER_IP." || \
       echo "Failed to update Git repository on $WORKER_IP."
  done

  echo "Git update process completed on all workers."
}

# Main function
function check_status {
  # Worker Ping 상태 확인
  for ip in "${WORKER_IPS[@]}"; do
    echo "Checking status of Worker $ip..."
    ping -c 1 $ip &> /dev/null
    if [ $? -eq 0 ]; then
      echo "Worker $ip is reachable."
    else
      echo "Worker $ip is not reachable."
    fi
  done
}

function init_worker_machine {
  local REPO_URL="https://github.com/kimdoyoung0319/332project.git"
  local BRANCH="main"

  echo "Starting Git repository reset and output directory setup on all workers..."

  for WORKER_IP in "${WORKER_IPS[@]}"; do
    echo "Resetting Git repository and output directory on $WORKER_IP..."

    ssh -n blue@"$WORKER_IP" "
      set -e
      echo 'Removing existing 332project directory...'
      rm -rf ~/332project

      echo 'Cloning fresh repository...'
      git clone $REPO_URL
      cd ~/332project
      git checkout $BRANCH
      echo 'Repository reset completed on $WORKER_IP.'

      echo 'Resetting output directory...'
      rm -rf ~/output
      mkdir -p ~/output
      echo 'Output directory reset completed on $WORKER_IP.'
    " && echo "Worker $WORKER_IP successfully initialized." || \
       echo "Failed to initialize worker $WORKER_IP. Please check logs."
  done

  echo "Initialization process completed on all workers."
}

function start_master_process {
  local valid_count=false
  local valid_path=false

  while [ "$valid_count" = false ]; do
    read -p "Enter the number of worker machines to operate (1-10): " COUNT
    if [[ "$COUNT" =~ ^[1-9]$|^10$ ]]; then
      valid_count=true
    else
      echo "Invalid worker count. Please enter a number between 1 and 10."
    fi
  done

  while [ "$valid_path" = false ]; do
    read -p "Enter the input path (absolute path required): " INPUT_PATH
    if [[ "$INPUT_PATH" =~ ^/ ]]; then
      valid_path=true
    else
      echo "Invalid input path. Please provide an absolute path starting with '/'."
    fi
  done

  run_master $COUNT $INPUT_PATH
}

function validate_sorted_data {
  WORKER_FILE="$HOME/332project/scripts/.worker_id_map.txt"
  RESULT_FILE="$HOME/output/whole"
  VALSORT="$HOME/gensort/valsort"

  echo "Starting validation process for all workers..."

  # Read workers ip, id is worker_id_map.txt
  if [[ -f "$WORKER_FILE" ]]; then
    while IFS=, read -r id ip || [[ -n "$id" ]]; do
      echo
      echo "Processing worker ID: $id with IP: $ip"

      ssh -n blue@"$ip" "
        FILE_COUNT=\$(ls ~/output/partition.* 2>/dev/null | wc -l)
        echo \"Number of partition files: \$FILE_COUNT\"

        if [ \"\$FILE_COUNT\" -eq 0 ]; then
          echo 'No partition files found. Skipping worker.'
          exit 1
        fi

        > \"$RESULT_FILE\"
        for i in \$(seq 0 \$((FILE_COUNT - 1))); do
          if [[ -f ~/output/partition.\$i ]]; then
            cat ~/output/partition.\$i >> \"$RESULT_FILE\"
          fi
        done

        \"$VALSORT\" \"$RESULT_FILE\"
        head -n 2 \"$RESULT_FILE\"
        tail -n 2 \"$RESULT_FILE\"
      "
    done < "$WORKER_FILE"
  else
    echo "Error: Worker file not found at $WORKER_FILE"
    exit 1
  fi

  echo
  echo "Validation process completed for all workers."
}

function run_master {
  local COUNT=$1
  local INPUT_PATH=$2

  echo "Starting Master process with $COUNT workers and input path $INPUT_PATH..."

  # Master 실행 디렉토리 이동 및 실행
  cd ~/332project || { echo "Failed to navigate to ~/332project. Exiting."; return 1; }
  sbt "runMain master.Main $COUNT $INPUT_PATH"
  echo "Master started with $COUNT workers and input path $INPUT_PATH."

  echo "Run master command completed."
}

function show_menu {
  echo
  echo "==================== MENU ===================="
  echo "1. Check Worker Status"
  echo "2. Init worker environment"
  echo "3. Start Master Process"
  echo "4. Validate Sorted Data"
  echo "5. Reset ~/output Directory"
  echo "6. (AUX) Developer Menu"
  echo "0. Exit"
  echo "=============================================="
}

function aux_menu {
  echo
  echo "==================== Developer Menu ===================="
  echo "1. Distribute .bashrc"
  echo "2. Distribute gensort,valsort"
  echo "3. Generate gensort data for all sizes (Disable)"
  echo "4. Update Git Repos"
  echo "0. Exit"
  echo "========================================================"
}

# 메뉴 기반 실행
while true; do
  show_menu
  read -p "Select an option: " choice
  case $choice in
    1)
      check_status
      ;;
    2)
      init_worker_machine
      ;;
    3)
      start_master_process
      ;;
    4)
      validate_sorted_data
      ;;
    5)
      reset_worker_output
      ;;
    6)
      while true; do
        aux_menu
        read -p "Select an option in Developer Menu: " aux_choice
        case $aux_choice in
          1)
            distribute_bashrc
            ;;
          2)
            distribute_gensort_valsort
            ;;
          3)
            echo "Disable (uncomment if you want to use it)"
#            echo "Running gensort for all sizes..."
#            for size_key in "${size_keys[@]}"; do
#              generate_inputdata $size_key
#            done
#            echo "All gensort tasks completed for all sizes."
            ;;
          4)
            update_git_repos
            ;;
          0)
            echo "Exiting Developer Menu."
            break
            ;;
          *)
            echo "Invalid option in Developer Menu. Please try again."
            ;;
        esac
      done
      ;;
    0)
      echo "Exiting."
      break
      ;;
    *)
      echo "Invalid option. Please try again."
      ;;
  esac
done








