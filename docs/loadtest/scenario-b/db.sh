#!/usr/bin/env bash
# 로컬 MySQL(기본 bukang-db 컨테이너, 측정 컨테이너는 DB_CONTAINER=cashload-mysql)에 SQL을 보낸다. 표준 입력의 SQL을 그대로 실행한다
# 예(backend/에서): ./loadtest/scenario-b/db.sh < loadtest/scenario-b/sql/01-seed.sql
# 비밀번호는 compose.yml의 MYSQL_ROOT_PASSWORD (로컬 전용 값). performance_schema를 읽으려고 root로 접속한다
set -euo pipefail
docker exec -i "${DB_CONTAINER:-bukang-db}" mysql -uroot -pbukang-admin --default-character-set=utf8mb4 --table bukang-db "$@" 2>&1 \
	| { grep -v "Using a password on the command line" || true; }
