#!/usr/bin/env bash
# 예치금 부하 측정 한 바퀴 (docs/measurements/2026-11-16-예치금-부하-판정기준.md)
# 빈 DB로 띄우기 → 시드 → 시나리오마다 k6 → 돈 확인 → 끝에 락 통계·그래프 → 내리기
# 시나리오마다 <이름>.lock-waits.tsv(5초마다 잠금 대기 스냅샷)와 <이름>.lock-sql.txt(SQL별 잠금 대기 합)도 남긴다
# 사용: LABEL=a REP=1 OUT=/tmp/cashload ./run.sh
#   LABEL     : 이미지 이름표 (cashload-cash:<LABEL>). 없으면 지금 코드로 빌드한다. 기본 local
#   REP       : 반복 번호 (결과 폴더 이름). 기본 1
#   OUT       : 결과를 모을 폴더. 결과는 $OUT/<LABEL>/rep<REP>/ 에 쌓인다
#   SCENARIOS : 돌릴 시나리오 (공백 구분). 기본 "warmup b0 b1 b2 b3 b4 b5 b5-limit"
#               B-1 한계(b1-limit, 약 11분, 판정 안 함)는 넣을 때만 돈다 (워크플로는 모든 회차에 넣음)
#               2차(docs/measurements/2026-11-18-예치금-부하-2차-판정기준.md) 순서:
#               "warmup b0 b2 b0-again b0-split pg4 b6 proxy b7 b8 direct b1-limit"
#               예치금만 다시 띄우고 예열한다: pg4 = PG 승인 4초(B-6 앞), proxy = PG 1초 + MySQL·Redis를 지연 컨테이너로(B-7·B-8 앞),
#               direct = PG 1초 + 바로(처음과 같음, B-1 한계 앞)
#   QUICK=1   : 확인용으로 시간을 줄인다 (단계 15초, B-4는 100명만, 한계 몰림 200명)
#   PYTHON    : 그래프를 그릴 python (matplotlib 필요). 없으면 그래프만 건너뛴다
# 필요한 것: docker(compose v2), curl, python3. k6는 컨테이너(grafana/k6)로 돈다
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SB="$(dirname "$HERE")"
LABEL=${LABEL:-local}
REP=${REP:-1}
OUT=${OUT:-/tmp/cashload}
RUN_DIR="$OUT/$LABEL/rep$REP"
SCENARIOS=${SCENARIOS:-warmup b0 b1 b2 b3 b4 b5 b5-limit}
PYTHON=${PYTHON:-python3}
K6_IMAGE=grafana/k6:1.0.0
# compose.load.yml의 측정 전용 값과 같아야 한다
INTERNAL_KEY=cashload-internal-key

STAGE_SEC=120; PHASE_SEC=60; DURATION_SEC=600; WARMUP_SEC=30; B4_SIZES="100 1000 10000"; LIMIT_VUS=1000; EXT_STAGE_SEC=180
if [ "${QUICK:-0}" = 1 ]; then
	STAGE_SEC=15; PHASE_SEC=15; DURATION_SEC=20; WARMUP_SEC=10; B4_SIZES=100; LIMIT_VUS=200; EXT_STAGE_SEC=15
fi
# B-7·B-8에서 toxiproxy가 응답마다 더하는 지연 (2차 판정 기준 2장, 추천값)
REDIS_DELAY_MS=100; MYSQL_DELAY_MS=100
TOXI_URL="http://127.0.0.1:${TOXI_PORT:-18474}"

# 시드·확인·B-3·B-4 스크립트가 이 컨테이너와 주소를 쓴다 (db.sh, verify.sh, seed-*.sh, b4-notice.sh)
export LABEL DB_CONTAINER=cashload-mysql CASH_URL="http://127.0.0.1:${CASH_PORT:-18181}"
# 예치금 지표는 따로 연 포트로 받는다 (compose.load.yml MANAGEMENT_SERVER_PORT). b4-notice.sh가 쓴다
export CASH_METRICS_URL="http://127.0.0.1:${CASH_METRICS_PORT:-18191}"
export PROM_URL="http://127.0.0.1:${PROMETHEUS_PORT:-19095}" PROM_WINDOW=5s
export STAGE_SEC PHASE_SEC DURATION_SEC EXT_STAGE_SEC
# 예치금 JVM이 JFR·GC 로그를 여기에 쓴다 (compose.load.yml). 앱 계정(spring)이 쓸 수 있게 연다
export JVM_DIR="$RUN_DIR/jvm"
compose() { docker compose -f "$HERE/compose.load.yml" "$@"; }
log() { echo "[$(date +%H:%M:%S)] $LABEL rep$REP $*"; }

mkdir -p "$RUN_DIR" "$JVM_DIR"
chmod 777 "$JVM_DIR"
: > "$RUN_DIR/windows.tsv"
# 결과 정리(report.py)가 처리량을 계산할 때 쓴다
printf 'STAGE_SEC=%s\nPHASE_SEC=%s\nDURATION_SEC=%s\nEXT_STAGE_SEC=%s\n' \
	"$STAGE_SEC" "$PHASE_SEC" "$DURATION_SEC" "$EXT_STAGE_SEC" > "$RUN_DIR/params.txt"

log "환경 띄우기 (빈 DB)"
compose down -v --remove-orphans >/dev/null 2>&1 || true
compose up -d --wait

# 앱 밖에서 잰 CPU(cAdvisor)는 컨테이너 ID로만 나와서, graph.py가 이 파일로 예치금·MySQL을 고른다
# 예치금을 다시 띄우면(pg4·proxy·direct) 새 ID가 한 줄 더 붙는다
printf 'cash\t%s\nmysql\t%s\ntoxiproxy\t%s\n' "$(compose ps -q cash)" "$(compose ps -q mysql)" "$(compose ps -q toxiproxy)" \
	> "$RUN_DIR/container-ids.txt"

# 가짜 PG 승인 지연과 MySQL·Redis 길을 바꿔 예치금만 다시 띄운다. DB·Kafka는 그대로다. 그만둔 JVM은 JFR을 파일로 남기고 내린다
VIA_PROXY=0
restart_cash() { # restart_cash <PG 승인 지연, 예: 4s> <direct|proxy>
	log "예치금 다시 띄우기 (PG 승인 $1, MySQL·Redis $2)"
	if [ "$2" = proxy ]; then VIA_PROXY=1; db=toxiproxy; cache=toxiproxy; else VIA_PROXY=0; db=mysql; cache=redis; fi
	PG_CONFIRM_DELAY=$1 CASH_MYSQL_HOST=$db CASH_REDIS_HOST=$cache compose up -d --wait cash
	printf 'cash\t%s\n' "$(compose ps -q cash)" >> "$RUN_DIR/container-ids.txt"
}

# toxiproxy로 예치금 → Redis·MySQL 응답마다 지연을 넣고 뺀다. 예치금이 지연 컨테이너를 거칠 때(proxy 뒤)만 뜻이 있다
delay_on() { # delay_on <redis|mysql> <ms>
	[ "$VIA_PROXY" = 1 ] || { echo "B-7·B-8은 proxy 뒤에 둔다 (예치금이 지연 컨테이너를 거치지 않음)" >&2; exit 1; }
	curl -sf -X POST "$TOXI_URL/proxies/$1/toxics" -H 'Content-Type: application/json' \
		-d "{\"name\":\"delay\",\"type\":\"latency\",\"stream\":\"downstream\",\"attributes\":{\"latency\":$2}}" > /dev/null
	log "$1 지연 ${2}ms 넣음"
}
delay_off() { curl -sf -X DELETE "$TOXI_URL/proxies/$1/toxics/delay" > /dev/null || true; }

# B-6~B-8 충전 회원(940001~970000)을 한 바퀴에 한 번만 만든다. 세 시나리오가 돌려 쓴다
seed_charge_once() {
	[ -f "$RUN_DIR/charge-seed.txt" ] || "$SB/seed-charge.sh" > "$RUN_DIR/charge-seed.txt" 2>&1
}

log "데이터 넣기"
"$SB/db.sh" < "$SB/sql/01-seed.sql" > "$RUN_DIR/seed.txt"

# 행 잠금을 기다리는 것을 5초마다 찍는다 (sql/14-lock-waits-now.sql). k6가 끝나면 멈춘다
lock_watch() { # lock_watch <남길 파일>
	printf '시각\t테이블\t인덱스\t기다린_잠금\t기다리는_수\t가장_오래_초\t기다린_SQL\t막은_SQL\n' > "$1"
	while :; do
		# db.sh의 --table을 꺼야 탭으로 나뉜 줄이 된다
		"$SB/db.sh" --skip-table --batch --skip-column-names < "$SB/sql/14-lock-waits-now.sql" >> "$1" 2>/dev/null || true
		sleep 5
	done
}

# 주문·정산 번호가 실행끼리 겹치지 않게 k6를 부를 때마다 RUN을 하나씩 올린다 (verify.sh에도 같은 RUN)
RUN=0
k6() { # k6 <결과 이름> <스크립트> [k6 옵션...]
	local name=$1 script=$2; shift 2
	RUN=$((RUN + 1))
	# SQL별 잠금 대기 합을 이 시나리오 몫만 남기려고 문장 요약을 비운다 (끝나면 sql/15-lock-time-by-sql.sql로 읽음)
	echo "TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;" | "$SB/db.sh" > /dev/null
	# 가장 오래 기다린 행 락은 MySQL 8.4에서 0으로 되돌릴 수 없다(innodb_monitor_reset이 듣지 않음, 11/18 확인).
	# 앞뒤로 읽어 이 시나리오에서 올랐는지 본다. 이 시나리오만의 값은 lock-sql.txt의 「가장 오래」로 본다
	"$SB/db.sh" < "$SB/sql/11-lock-stats.sql" > "$RUN_DIR/$name.lock-before.txt" 2>&1 || true
	lock_watch "$RUN_DIR/$name.lock-waits.tsv" &
	local watcher=$!
	local start
	start=$(date +%s)
	date -r "$start" '+%F %T' 2>/dev/null > "$RUN_DIR/$name.start" || date -d "@$start" '+%F %T' > "$RUN_DIR/$name.start"
	# 기준을 넘으면 k6가 99로 끝난다. 판정은 결과 정리에서 하므로 다음 시나리오로 넘어간다
	docker run --rm --network cashload_default --cpus 1 --user "$(id -u):$(id -g)" \
		-v "$SB:/scripts:ro" -v "$RUN_DIR:/out" "$K6_IMAGE" run --quiet --summary-export "/out/$name.json" \
		-e CASH_URL=http://cash:8081 -e INTERNAL_API_KEY="$INTERNAL_KEY" -e RUN="$RUN" "$@" "/scripts/$script" \
		> "$RUN_DIR/$name.txt" 2>&1 || log "$name: k6가 0이 아닌 값으로 끝남 (기준 넘음이거나 실패, $name.txt)"
	printf '%s\t%s\t%s\t%s\n' "$name" "$RUN" "$start" "$(date +%s)" >> "$RUN_DIR/windows.tsv"
	kill "$watcher" 2>/dev/null || true
	wait "$watcher" 2>/dev/null || true
	"$SB/db.sh" < "$SB/sql/15-lock-time-by-sql.sql" > "$RUN_DIR/$name.lock-sql.txt" 2>&1 || log "$name: 잠금 대기 SQL 실패"
	"$SB/db.sh" < "$SB/sql/11-lock-stats.sql" > "$RUN_DIR/$name.lock-after.txt" 2>&1 || true
	[ -f "$RUN_DIR/$name.json" ] || echo "$name" >> "$RUN_DIR/failed.txt"
	"$SB/verify.sh" "$RUN" > "$RUN_DIR/$name.verify.txt" 2>&1 || log "$name: 돈 확인 SQL 실패"
	log "$name 끝"
	sleep 10 # 앞 구간 요청이 가라앉고 그래프 구간이 섞이지 않게
}

for s in $SCENARIOS; do
	case $s in
		warmup) k6 warmup b1-payment-concurrency.js -e STAGES=2 -e STAGE_SEC="$WARMUP_SEC" ;;
		b0) k6 b0 b0-integrity.js ;;
		b1) k6 b1 b1-payment-concurrency.js -e STAGES=4,8 -e STAGE_SEC="$STAGE_SEC" ;;
		b1-limit) k6 b1-limit b1-payment-ramp.js -e STAGE_SEC="$STAGE_SEC" ;;
		b2) k6 b2 b2-payment-with-payout.js -e PAY_VUS=4 -e PHASE_SEC="$PHASE_SEC" ;;
		b3)
			"$SB/seed-withdrawal.sh" > "$RUN_DIR/b3-seed.txt" 2>&1
			k6 r-b3-1 b3-withdrawal.js ;;
		b4)
			for n in $B4_SIZES; do
				log "B-4 대상 ${n}명 (배치가 매분 0초에 돈다)"
				"$SB/b4-notice.sh" "$n" > "$RUN_DIR/r-b4-$n-1.txt" 2>&1 || log "B-4 ${n}명 실패"
			done
			# 배치가 매분 돌므로 대상이 남으면 뒤 시나리오와 겹친다 (기준선 측정에서 B-5·한계가 매분 1만 명 배치와 겹침)
			"$SB/db.sh" < "$SB/sql/13-cleanup-notice.sql" > "$RUN_DIR/b4-cleanup.txt" ;;
		b5)
			"$SB/seed-charge.sh" > "$RUN_DIR/b5-seed.txt" 2>&1
			k6 b5 b5-charge.js -e MODE=flow -e DURATION_SEC="$DURATION_SEC" ;;
		b5-limit) k6 b5-limit b5-charge.js -e MODE=limit -e LIMIT_VUS="$LIMIT_VUS" ;;
		# 2차: B-0 섞인 구간 다시, 요청 종류 나누기 (P8 묶음)
		b0-again) k6 b0-again b0-integrity.js -e MODE=again -e MIX_SEC="$PHASE_SEC" ;;
		b0-split) k6 b0-split b0-integrity.js -e MODE=split -e MIX_SEC="$PHASE_SEC" ;;
		# 2차: 예치금을 다시 띄운 뒤 예열 (예열 숫자는 버림)
		pg4|proxy|direct)
			case $s in pg4) restart_cash 4s direct ;; proxy) restart_cash 1s proxy ;; direct) restart_cash 1s direct ;; esac
			k6 "warmup-$s" b1-payment-concurrency.js -e STAGES=2 -e STAGE_SEC="$WARMUP_SEC" ;;
		# 2차: 바깥이 느릴 때. B-6은 pg4(PG 승인 4초) 뒤, B-7·B-8은 proxy 뒤에 돈다
		b6) seed_charge_once; k6 b6 b6-external.js -e PREFIX=b6 -e STAGE_SEC="$EXT_STAGE_SEC" ;;
		b7)
			seed_charge_once; delay_on redis "$REDIS_DELAY_MS"
			k6 b7 b6-external.js -e PREFIX=b7 -e STAGE_SEC="$EXT_STAGE_SEC"
			delay_off redis ;;
		b8)
			seed_charge_once; delay_on mysql "$MYSQL_DELAY_MS"
			k6 b8 b6-external.js -e PREFIX=b8 -e STAGE_SEC="$EXT_STAGE_SEC"
			delay_off mysql ;;
		*) echo "모르는 시나리오: $s" >&2; exit 1 ;;
	esac
done

log "락 통계 (데드락·락 대기 시간 초과는 0이어야 함, 이번 바퀴 전체)"
"$SB/db.sh" < "$SB/sql/11-lock-stats.sql" > "$RUN_DIR/lock-stats.txt"

log "그래프 (Prometheus가 떠 있는 동안)"
# 돌리지 않은 시나리오(시작 시각 파일이 없음)는 건너뛴다
graph() { [ -f "$RUN_DIR/$2.start" ] || return 0; "$PYTHON" "$SB/graph.py" "$1" "$(cat "$RUN_DIR/$2.start")" "$RUN_DIR" >/dev/null; }
if "$PYTHON" -c 'import matplotlib' 2>/dev/null; then
	graph b0 b0 || log "B-0 그래프 실패"
	graph b1c b1 || log "B-1 그래프 실패"
	graph b1 b1-limit || log "B-1 한계 그래프 실패"
	graph b2 b2 || log "B-2 그래프 실패"
	graph b5 b5 || log "B-5 그래프 실패"
	graph b0again b0-again || log "B-0 다시 그래프 실패"
	graph b0split b0-split || log "B-0 나누기 그래프 실패"
	for s in b6 b7 b8; do graph "$s" "$s" || log "$s 그래프 실패"; done
	if [ -f "$RUN_DIR/r-b3-1.json" ]; then "$PYTHON" "$SB/graph.py" b3 "$RUN_DIR/r" "$RUN_DIR" >/dev/null || log "B-3 그래프 실패"; fi
	if ls "$RUN_DIR"/r-b4-*.txt >/dev/null 2>&1; then "$PYTHON" "$SB/graph.py" b4 "$RUN_DIR/r" "$RUN_DIR" >/dev/null || log "B-4 그래프 실패"; fi
else
	log "matplotlib이 없어 그래프를 건너뜀 (PYTHON=$PYTHON)"
fi

compose logs --no-color cash > "$RUN_DIR/cash.log" 2>&1 || true
gzip -f "$RUN_DIR/cash.log"
# Prometheus 데이터를 통째로 남긴다. 끝난 뒤 로컬에서 다시 띄워 아무 구간이나 다시 본다 (README 「끝난 뒤 지표 다시 보기」)
# 멈춘 뒤 복사해서 쓰다 만 파일이 없다. 다시 띄우면 Prometheus가 WAL을 읽어 마지막까지 살린다
compose stop prometheus >/dev/null 2>&1 || true
if docker cp "$(compose ps -aq prometheus)":/prometheus "$RUN_DIR/prometheus" >/dev/null 2>&1; then
	tar -czf "$RUN_DIR/prometheus-data.tar.gz" -C "$RUN_DIR" prometheus && rm -rf "$RUN_DIR/prometheus"
else
	log "Prometheus 데이터를 남기지 못함 (그래프 PNG는 그대로)"
fi
compose down -v --remove-orphans >/dev/null 2>&1
# 내리면서 JVM이 JFR을 파일로 남긴다. 아티팩트 크기를 줄인다
gzip -f "$JVM_DIR"/*.jfr "$JVM_DIR"/*.log 2>/dev/null || true
log "끝: $RUN_DIR"
