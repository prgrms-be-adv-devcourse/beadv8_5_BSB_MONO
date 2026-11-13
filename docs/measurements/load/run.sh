#!/usr/bin/env bash
# 결제 잠금 부하 측정 한 바퀴: 빈 DB로 띄우기 → 데이터 넣기 → 시나리오 → 정확성 SQL → 내리기
# 사용: LABEL=a REP=1 OUT=/tmp/orderload ./run.sh
#   LABEL     : 이미지 이름표 (orderload-order:<LABEL>). 없으면 지금 코드로 빌드한다. 기본 local
#   REP       : 반복 번호 (결과 폴더 이름). 기본 1
#   OUT       : 결과를 모을 폴더. 결과는 $OUT/<LABEL>/rep<REP>/ 에 쌓인다
#   SCENARIOS : 돌릴 시나리오 (공백 구분). 기본 전체. 예: SCENARIOS="warmup s1-1 s2"
#   QUICK=1   : 확인용으로 시간을 줄인다 (흐름 20초, 몰림 2번)
# 필요한 것: docker(compose v2), curl, python3. k6는 컨테이너(grafana/k6)로 돈다
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
LABEL=${LABEL:-local}
REP=${REP:-1}
OUT=${OUT:-/tmp/orderload}
RUN_DIR="$OUT/$LABEL/rep$REP"
ALL="warmup s1-1 s1-5 s2 s3 s4-100-flow s4-100-burst s4-300-flow s4-300-burst s4-500-flow s4-500-burst s5-flow s5-burst"
SCENARIOS=${SCENARIOS:-$ALL}
TOXI="http://127.0.0.1:${TOXIPROXY_API_PORT:-18474}"
PROM="http://127.0.0.1:${PROMETHEUS_PORT:-19090}"
K6_IMAGE=grafana/k6:1.0.0
FLOW_DURATION=2m; WARMUP_DURATION=1m; ROUNDS=5
if [ "${QUICK:-0}" = 1 ]; then FLOW_DURATION=20s; WARMUP_DURATION=10s; ROUNDS=2; fi

export LABEL
compose() { docker compose -f "$HERE/compose.load.yml" "$@"; }
sql() { compose exec -T mysql mysql -N -s -ubukang2 -pbukang2 bukang-db 2>/dev/null; }
log() { echo "[$(date +%H:%M:%S)] $LABEL rep$REP $*"; }

mkdir -p "$RUN_DIR"
: > "$RUN_DIR/windows.tsv"

log "환경 띄우기 (빈 DB)"
compose down -v --remove-orphans >/dev/null 2>&1 || true
ORDER_POOL=10 compose up -d --wait

curl -fsS -X POST "$TOXI/proxies" -d '{"name":"cash","listen":"0.0.0.0:18081","upstream":"cash:8081"}' >/dev/null

log "데이터 넣기"
{ echo "set @members = ${MEMBERS:-140000};"; cat "$HERE/seed.sql"; } | sql > "$RUN_DIR/seed.txt"
MONEY_START=$(echo "select sum(balance) from cash_wallet;" | sql)
echo "$MONEY_START" > "$RUN_DIR/money_start.txt"

set_latency() {
	curl -fsS -X DELETE "$TOXI/proxies/cash/toxics/delay" >/dev/null 2>&1 || true
	if [ "$1" -gt 0 ]; then
		curl -fsS -X POST "$TOXI/proxies/cash/toxics" \
			-d "{\"name\":\"delay\",\"type\":\"latency\",\"stream\":\"upstream\",\"attributes\":{\"latency\":$1}}" >/dev/null
	fi
}

set_pool() {
	local current
	current=$(compose exec -T order printenv SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE 2>/dev/null || echo "")
	if [ "$current" != "$1" ]; then
		log "주문 DB 연결 수 $1 로 다시 띄우기"
		ORDER_POOL=$1 compose up -d --wait order
	fi
}

INDEX=0
k6() {
	local name=$1; shift
	local member_from=$((100001 + INDEX * 10000))
	INDEX=$((INDEX + 1))
	local start end
	start=$(date +%s)
	docker run --rm --network orderload_default --cpus 1 --user "$(id -u):$(id -g)" \
		-v "$HERE:/scripts:ro" -v "$RUN_DIR:/out" "$K6_IMAGE" run --quiet \
		-e MEMBER_FROM="$member_from" -e SUMMARY="/out/$name.k6.json" "$@" /scripts/order-pay.js \
		> "$RUN_DIR/$name.k6.log" 2>&1 || log "$name: k6가 0이 아닌 값으로 끝남 (로그 $name.k6.log)"
	end=$(date +%s)
	printf '%s\t%s\t%s\n' "$name" "$start" "$end" >> "$RUN_DIR/windows.tsv"
	log "$name 끝: $(tail -1 "$RUN_DIR/$name.k6.log")"
	sleep 20 # 연결 대기가 가라앉고 구간이 섞이지 않게
}

flow() { k6 "$1" -e MODE=flow -e COURSES="$2" -e RATE_PER_COURSE="$3" -e DURATION="$4"; }
burst() { k6 "$1" -e MODE=burst -e ROUNDS="$ROUNDS" -e SAME_OPTION="$2"; }

for s in $SCENARIOS; do
	case $s in
		warmup) set_pool 10; set_latency 0; flow warmup 1 1 "$WARMUP_DURATION" ;;
		s1-1) set_pool 10; set_latency 0; flow s1-1 1 4 "$FLOW_DURATION" ;;
		s1-5) set_pool 10; set_latency 0; flow s1-5 5 4 "$FLOW_DURATION" ;;
		s2) set_pool 10; set_latency 0; burst s2 0 ;;
		s3) set_pool 10; set_latency 0; burst s3 1 ;;
		s4-*-flow) ms=${s#s4-}; ms=${ms%-flow}; set_pool 10; set_latency "$ms"; flow "$s" 5 4 "$FLOW_DURATION" ;;
		s4-*-burst) ms=${s#s4-}; ms=${ms%-burst}; set_pool 10; set_latency "$ms"; burst "$s" 0 ;;
		s5-flow) set_pool 20; set_latency 300; flow s5-flow 5 4 "$FLOW_DURATION" ;;
		s5-burst) set_pool 20; set_latency 300; burst s5-burst 0 ;;
		*) echo "모르는 시나리오: $s" >&2; exit 1 ;;
	esac
done
set_latency 0

log "정확성 확인"
sleep 5
{ echo "set @money_start = $MONEY_START;"; cat "$HERE/check.sql"; } | sql > "$RUN_DIR/check.txt"
cat "$RUN_DIR/check.txt"

log "Prometheus 구간 지표 받기"
python3 "$HERE/report.py" prom "$PROM" "$RUN_DIR"

compose logs --no-color order cash product > "$RUN_DIR/services.log" 2>&1 || true
gzip -f "$RUN_DIR/services.log"
compose down -v --remove-orphans >/dev/null 2>&1
log "끝: $RUN_DIR"
