#!/usr/bin/env bash
# 예치금 부하 측정 한 바퀴 (docs/measurements/2026-11-16-예치금-부하-판정기준.md)
# 빈 DB로 띄우기 → 시드 → 시나리오마다 k6 → 돈 확인 → 끝에 락 통계·그래프 → 내리기
# 사용: LABEL=a REP=1 OUT=/tmp/cashload ./run.sh
#   LABEL     : 이미지 이름표 (cashload-cash:<LABEL>). 없으면 지금 코드로 빌드한다. 기본 local
#   REP       : 반복 번호 (결과 폴더 이름). 기본 1
#   OUT       : 결과를 모을 폴더. 결과는 $OUT/<LABEL>/rep<REP>/ 에 쌓인다
#   SCENARIOS : 돌릴 시나리오 (공백 구분). 기본 "warmup b0 b1 b2 b3 b4 b5 b5-limit"
#               B-1 한계(b1-limit, 약 11분)는 판정에 쓰지 않아 첫 A에서만 넣는다
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

STAGE_SEC=120; PHASE_SEC=60; DURATION_SEC=600; WARMUP_SEC=30; B4_SIZES="100 1000 10000"; LIMIT_VUS=1000
if [ "${QUICK:-0}" = 1 ]; then
	STAGE_SEC=15; PHASE_SEC=15; DURATION_SEC=20; WARMUP_SEC=10; B4_SIZES=100; LIMIT_VUS=200
fi

# 시드·확인·B-3·B-4 스크립트가 이 컨테이너와 주소를 쓴다 (db.sh, verify.sh, seed-*.sh, b4-notice.sh)
export LABEL DB_CONTAINER=cashload-mysql CASH_URL="http://127.0.0.1:${CASH_PORT:-18181}"
export PROM_URL="http://127.0.0.1:${PROMETHEUS_PORT:-19095}" PROM_WINDOW=5s
export STAGE_SEC PHASE_SEC DURATION_SEC
compose() { docker compose -f "$HERE/compose.load.yml" "$@"; }
log() { echo "[$(date +%H:%M:%S)] $LABEL rep$REP $*"; }

mkdir -p "$RUN_DIR"
: > "$RUN_DIR/windows.tsv"

log "환경 띄우기 (빈 DB)"
compose down -v --remove-orphans >/dev/null 2>&1 || true
compose up -d --wait

log "데이터 넣기"
"$SB/db.sh" < "$SB/sql/01-seed.sql" > "$RUN_DIR/seed.txt"

# 주문·정산 번호가 실행끼리 겹치지 않게 k6를 부를 때마다 RUN을 하나씩 올린다 (verify.sh에도 같은 RUN)
RUN=0
k6() { # k6 <결과 이름> <스크립트> [k6 옵션...]
	local name=$1 script=$2; shift 2
	RUN=$((RUN + 1))
	local start
	start=$(date +%s)
	date -r "$start" '+%F %T' 2>/dev/null > "$RUN_DIR/$name.start" || date -d "@$start" '+%F %T' > "$RUN_DIR/$name.start"
	# 기준을 넘으면 k6가 99로 끝난다. 판정은 결과 정리에서 하므로 다음 시나리오로 넘어간다
	docker run --rm --network cashload_default --cpus 1 --user "$(id -u):$(id -g)" \
		-v "$SB:/scripts:ro" -v "$RUN_DIR:/out" "$K6_IMAGE" run --quiet --summary-export "/out/$name.json" \
		-e CASH_URL=http://cash:8081 -e INTERNAL_API_KEY="$INTERNAL_KEY" -e RUN="$RUN" "$@" "/scripts/$script" \
		> "$RUN_DIR/$name.txt" 2>&1 || log "$name: k6가 0이 아닌 값으로 끝남 (기준 넘음이거나 실패, $name.txt)"
	printf '%s\t%s\t%s\t%s\n' "$name" "$RUN" "$start" "$(date +%s)" >> "$RUN_DIR/windows.tsv"
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
			done ;;
		b5)
			"$SB/seed-charge.sh" > "$RUN_DIR/b5-seed.txt" 2>&1
			k6 b5 b5-charge.js -e MODE=flow -e DURATION_SEC="$DURATION_SEC" ;;
		b5-limit) k6 b5-limit b5-charge.js -e MODE=limit -e LIMIT_VUS="$LIMIT_VUS" ;;
		*) echo "모르는 시나리오: $s" >&2; exit 1 ;;
	esac
done

log "락 통계 (데드락·락 대기 시간 초과는 0이어야 함, 이번 바퀴 전체)"
"$SB/db.sh" < "$SB/sql/11-lock-stats.sql" > "$RUN_DIR/lock-stats.txt"

log "그래프 (Prometheus가 떠 있는 동안)"
# 돌리지 않은 시나리오(시작 시각 파일이 없음)는 건너뛴다
graph() { [ -f "$RUN_DIR/$2.start" ] || return 0; "$PYTHON" "$SB/graph.py" "$1" "$(cat "$RUN_DIR/$2.start")" "$RUN_DIR" >/dev/null; }
if "$PYTHON" -c 'import matplotlib' 2>/dev/null; then
	graph b1c b1 || log "B-1 그래프 실패"
	graph b1 b1-limit || log "B-1 한계 그래프 실패"
	graph b2 b2 || log "B-2 그래프 실패"
	graph b5 b5 || log "B-5 그래프 실패"
	if [ -f "$RUN_DIR/r-b3-1.json" ]; then "$PYTHON" "$SB/graph.py" b3 "$RUN_DIR/r" "$RUN_DIR" >/dev/null || log "B-3 그래프 실패"; fi
	if ls "$RUN_DIR"/r-b4-*.txt >/dev/null 2>&1; then "$PYTHON" "$SB/graph.py" b4 "$RUN_DIR/r" "$RUN_DIR" >/dev/null || log "B-4 그래프 실패"; fi
else
	log "matplotlib이 없어 그래프를 건너뜀 (PYTHON=$PYTHON)"
fi

compose logs --no-color cash > "$RUN_DIR/cash.log" 2>&1 || true
gzip -f "$RUN_DIR/cash.log"
compose down -v --remove-orphans >/dev/null 2>&1
log "끝: $RUN_DIR"
