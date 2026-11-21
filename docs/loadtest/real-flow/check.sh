#!/usr/bin/env bash
# real-flow 정합성 확인 (check.sql). 대회 ID는 rush.js가 준비 끝에 찍은 줄이나 결과 파일에서 가져온다
# 사용: real-flow/check.sh 15,16,17,18            (로컬: bukang-db 컨테이너, DB 하나)
#       DB=prod real-flow/check.sh 15,16 | 운영 보조 서버에서 mysql로 돌린다 (README 참고)
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
races=${1:?대회 ID 목록 (쉼표로)}
if [ "${DB:-local}" = prod ]; then
	sed -e "s/@ORDER@/crewrun_order/g; s/@CASH@/crewrun_cash/g; s/@PRODUCT@/crewrun_product/g; s/@RACES@/$races/g" "$HERE/check.sql"
	exit 0
fi
sed -e "s/@ORDER@/\`bukang-db\`/g; s/@CASH@/\`bukang-db\`/g; s/@PRODUCT@/\`bukang-db\`/g; s/@RACES@/$races/g" "$HERE/check.sql" \
	| docker exec -i bukang-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -t 2>/dev/null'
