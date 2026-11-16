#!/usr/bin/env bash
# 돈 정합성 확인과 락 통계. 실행(backend/에서): ./loadtest/scenario-b/verify.sh <RUN>
set -euo pipefail
cd "$(dirname "$0")"
RUN="${1:?RUN 번호를 넣어 주세요. 예: ./verify.sh 1}"
{ echo "SET @run = ${RUN};"; cat sql/10-verify.sql; } | ./db.sh
./db.sh < sql/11-lock-stats.sql
