#!/usr/bin/env bash
# nginx-open.sh 전으로 되돌린다. 사용: ssh team05 'bash -s' < nginx-close.sh
set -euo pipefail
K="sudo k3s kubectl -n crewrun"
test -f ~/nginx-config-backup.yaml || { echo "백업 파일이 없어요"; exit 1; }
grep -v -E '^\s*(resourceVersion|uid|creationTimestamp):' ~/nginx-config-backup.yaml > /tmp/nginx-config-restore.yaml
$K apply -f /tmp/nginx-config-restore.yaml
$K rollout restart deploy/bukang-nginx
$K rollout status deploy/bukang-nginx --timeout=120s
# 다음 nginx-open.sh가 새로 백업하도록 지운다. 남겨 두면 나중에 옛 설정으로 되돌릴 수 있다
rm -f ~/nginx-config-backup.yaml /tmp/nginx-config-restore.yaml
echo "되돌림 끝"
