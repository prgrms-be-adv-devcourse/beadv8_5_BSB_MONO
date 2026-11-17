#!/usr/bin/env bash
# 점심 로컬 E2E (11/17). 각자 노트북의 로컬 인프라와 bootJar 앱으로 Playwright 시나리오를 돌린다.
# 전달문: handoff/2026-11-17-점심-e2e.md
#
#   ./scripts/e2e-lunch.sh prepare <이름>   점심 전. 내 서비스 bootJar 빌드
#   ./scripts/e2e-lunch.sh pretest <이름>   12:50. 10분 안에 환경 확인. 통과하면 앱을 켜 둔 채 끝난다
#   ./scripts/e2e-lunch.sh run <이름> [번호] 내 시나리오 실행 → e2e-<이름>.json, 끝나면 앱·컨테이너 정리
#                                          번호를 주면 그 시나리오만 다시 돌린다. 예: run dawit "25|37"
#   ./scripts/e2e-lunch.sh stop             켜 둔 앱·프론트·컨테이너 정리
#
# 이름: jihoon dawit youngho sohee hobin. 운영 서버에는 아무것도 보내지 않는다(모두 localhost).
set -u

FRONT=$(cd "$(dirname "$0")/.." && pwd)
BACK=$(cd "$FRONT/../backend" && pwd)
STATE=$FRONT/.e2e-lunch
FRONT_PORT=3177
INFRA="bukang-db bukang-kafka bukang-redis bukang-elasticsearch"
SEED_PASSWORD=12345678910
mkdir -p "$STATE/pids" "$STATE/logs"

say() { echo "[$(date '+%H:%M:%S')] $*"; }
fail() { echo "[$(date '+%H:%M:%S')] 실패: $*" >&2; exit 1; }

# 이름별 서비스 묶음 (게이트웨이는 따로 함께 띄운다). 순서대로 띄운다. 회원이 먼저다
bundle() {
	case $1 in
		jihoon) echo member cash order product inquiry board ;;
		dawit) echo member product queue order cash ;;
		youngho) echo member product order cash payout ;;
		sohee) echo member cash order product ;;
		hobin) echo member product file queue order ;;
		*) return 1 ;;
	esac
}

port_of() {
	case $1 in
		gateway) echo 8000 ;; member) echo 8080 ;; cash) echo 8081 ;; payout) echo 8082 ;; file) echo 8083 ;;
		order) echo 8084 ;; product) echo 8085 ;; queue) echo 8086 ;; inquiry) echo 8088 ;; board) echo 8089 ;;
	esac
}

module_of() { [ "$1" = gateway ] && echo gateway || echo "$1-service"; }

jar_of() { ls "$BACK/$(module_of "$1")/build/libs/"*.jar 2>/dev/null | grep -v plain | head -1; }

# 앱은 Java 25로 빌드된다. 시스템 java가 낮을 수 있어 Gradle이 받아 둔 JDK 25를 먼저 찾는다
find_java() {
	local j
	for j in "${JAVA25:-}" ~/.gradle/jdks/*25*/Contents/Home/bin/java ~/.gradle/jdks/*25*/*/Contents/Home/bin/java \
		~/.gradle/jdks/*25*/bin/java "$(command -v java)"; do
		[ -x "$j" ] && "$j" -version 2>&1 | grep -q 'version "25' && { echo "$j"; return 0; }
	done
	return 1
}

env_value() { grep -E "^$1=" "$BACK/.env" 2>/dev/null | tail -1 | cut -d= -f2-; }

# 200이나 503이면 앱은 뜬 것이다. 503은 쓰지 않는 인프라 하나가 DOWN일 때도 나온다
healthy() {
	local code
	code=$(curl -s -o /dev/null -m 3 -w '%{http_code}' "http://localhost:$(port_of "$1")/actuator/health")
	[ "$code" = 200 ] || [ "$code" = 503 ]
}

start_app() {
	local name=$1 extra=""
	case $name in
		inquiry) extra="INQUIRY_CLIENTS_MEMBER_MODE=http INQUIRY_CLIENTS_PRODUCT_MODE=http INQUIRY_CLIENTS_ORDER_MODE=http" ;;
		board) extra="BOARD_CLIENTS_MEMBER_MODE=http" ;;
	esac
	: >"$STATE/logs/$name.log"
	# backend/에서 띄운다. 앱이 이 폴더의 .env를 읽는다
	# 오픈뱅킹 키·소셜 로그인 이동 주소는 빈 값으로 .env보다 앞세워 가짜 확인·기본 주소를 쓰게 한다
	(cd "$BACK" || exit 1
		env SPRING_PROFILES_ACTIVE=dev SPRING_DOCKER_COMPOSE_ENABLED=false \
		INTERNAL_API_KEY="$INTERNAL_KEY" MARKETING_UNSUBSCRIBE_KEY="${MARKETING_UNSUBSCRIBE_KEY:-lunch-local-unsubscribe}" \
		OPENBANKING_CLIENT_ID= OPENBANKING_CLIENT_SECRET= OAUTH2_FRONT_BASE_URL= $extra \
		nohup "$JAVA" -Xmx512m -jar "$(jar_of "$name")" </dev/null >>"$STATE/logs/$name.log" 2>&1 &
		echo $! >"$STATE/pids/$name")
}

# 앱이 3분 안에 안 뜨면 로그에서 원인 줄을 보여 주고 멈춘다
wait_app() {
	local name=$1 i
	for i in $(seq 36); do
		healthy "$name" && { say "$name 뜸 (포트 $(port_of "$name"))"; return 0; }
		kill -0 "$(cat "$STATE/pids/$name")" 2>/dev/null || break
		sleep 5
	done
	echo "---- $name 로그의 원인 줄 ($STATE/logs/$name.log) ----" >&2
	grep -E 'checksum mismatch|Validate failed|Migration .* failed|Unknown column|missing (column|table)|doesn.t exist|Address already in use|already in use|APPLICATION FAILED TO START|Caused by:' \
		"$STATE/logs/$name.log" | tail -8 >&2
	echo "---- 마지막 10줄 ----" >&2
	tail -10 "$STATE/logs/$name.log" >&2
	fail "$name이(가) 3분 안에 뜨지 않았어요. 위 원인 줄을 확인해 주세요"
}

stop_all() {
	local f pids="" i alive
	for f in "$STATE"/pids/*; do
		[ -f "$f" ] || continue
		pids="$pids $(cat "$f")"
		rm -f "$f"
	done
	# pnpm dev가 띄운 next 자식 프로세스까지 정리한다
	pids="$pids $(lsof -ti tcp:$FRONT_PORT 2>/dev/null)"
	[ -n "${pids// /}" ] || return 0
	kill $pids 2>/dev/null
	# 앱이 Kafka 연결을 닫느라 몇 초 걸린다. 30초 안에 안 꺼지면 강제로 끈다
	for i in $(seq 30); do
		alive=""
		for f in $pids; do kill -0 "$f" 2>/dev/null && alive="$alive $f"; done
		[ -z "$alive" ] && return 0
		sleep 1
	done
	kill -9 $pids 2>/dev/null
}

free_gb() {
	if command -v vm_stat >/dev/null; then
		vm_stat | awk '/page size of/ {ps=$8} /Pages free|Pages inactive|Pages speculative/ {gsub("\\.","",$NF); p+=$NF} END {printf "%d", p*ps/1073741824}'
	else
		awk '/MemAvailable/ {printf "%d", $2/1048576}' /proc/meminfo
	fi
}

sql() { docker exec bukang-db mysql --default-character-set=utf8mb4 -ubukang2 -pbukang2 bukang-db -N -B -e "$1" 2>/dev/null; }

login_code() {
	curl -s -o "$STATE/login.json" -m 10 -w '%{http_code}' -H 'Content-Type: application/json' \
		-d "{\"email\":\"$2\",\"password\":\"$SEED_PASSWORD\"}" "$1/api/v1/auth/login"
}

cmd=${1:-}
name=${2:-}
if [ "$cmd" != stop ]; then
	apps=$(bundle "$name") || { echo "사용법: $0 prepare|pretest|run <jihoon|dawit|youngho|sohee|hobin> 또는 $0 stop" >&2; exit 2; }
fi

case $cmd in
	prepare)
		[ -f "$BACK/.env" ] || fail "backend/.env가 없어요. 슬랙으로 받은 .env를 $BACK/.env에 저장해 주세요"
		say "빌드: gateway $apps (workers 2)"
		(cd "$BACK" && ./gradlew :gateway:bootJar $(for a in $apps; do printf ':%s-service:bootJar ' "$a"; done) --max-workers=2) \
			|| fail "bootJar 빌드 실패. Java 버전 오류면 JDK 25를 설치하고 다시 실행해 주세요"
		(cd "$BACK" && ./gradlew --stop >/dev/null 2>&1)
		say "준비 끝. 12:50에 ./scripts/e2e-lunch.sh pretest $name"
		;;

	pretest)
		START=$(date +%s)
		left() { echo $((600 - ($(date +%s) - START))); }
		need_infra=$INFRA
		echo " $apps " | grep -q ' file ' && need_infra="$need_infra bukang-s3mock"

		say "1/7 Docker · 포트 · 메모리"
		docker info >/dev/null 2>&1 || fail "Docker가 꺼져 있어요. Docker Desktop을 켜 주세요"
		stop_all
		(cd "$BACK" && docker compose down -v --remove-orphans >/dev/null 2>&1)
		busy=""
		for p in 3306 6379 9092 9200 $FRONT_PORT $(port_of gateway) $(for a in $apps; do port_of "$a"; done); do
			lsof -nP -iTCP:$p -sTCP:LISTEN >/dev/null 2>&1 && busy="$busy $p"
		done
		[ -z "$busy" ] || fail "이미 쓰는 포트:$busy. IntelliJ 앱·다른 컨테이너를 끄고 다시 실행해 주세요"
		mem=$(free_gb)
		[ "${mem:-0}" -ge 3 ] || fail "빈 메모리가 ${mem}GB예요. 3GB 이상 필요해요. 브라우저 탭·다른 앱을 닫아 주세요"
		say "빈 메모리 약 ${mem}GB"

		say "2/7 .env · 앱 파일"
		[ -f "$BACK/.env" ] || fail "backend/.env가 없어요"
		JAVA=$(find_java) || fail "JDK 25를 찾지 못했어요. prepare를 먼저 실행하거나 JDK 25를 설치해 주세요"
		missing=""
		for a in gateway $apps; do [ -n "$(jar_of "$a")" ] || missing="$missing $(module_of "$a")"; done
		[ -z "$missing" ] || fail "빌드한 앱 파일이 없어요:$missing. ./scripts/e2e-lunch.sh prepare $name을 먼저 실행해 주세요"
		# 부르는 쪽과 받는 쪽이 같은 키를 써야 하므로 모든 앱에 같은 값을 넘긴다
		INTERNAL_KEY=$(env_value INTERNAL_API_KEY)
		INTERNAL_KEY=${INTERNAL_KEY:-lunch-local-internal-key}
		# 충전(35)의 토스 테스트 결제창에 쓰는 공개 키. 지훈 님이 나눠 준 backend/.env에 들어 있다
		TOSS_KEY=${NEXT_PUBLIC_TOSS_CLIENT_KEY:-$(env_value NEXT_PUBLIC_TOSS_CLIENT_KEY)}
		[ -n "$TOSS_KEY" ] || say "주의: backend/.env에 NEXT_PUBLIC_TOSS_CLIENT_KEY가 없어 충전(35)에서 결제창이 열리지 않아요"

		say "3/7 로컬 DB를 새로 만들고 인프라 준비 ($need_infra)"
		(cd "$BACK" && docker compose up -d $need_infra >"$STATE/logs/compose.log" 2>&1) \
			|| { tail -5 "$STATE/logs/compose.log" >&2; fail "인프라를 띄우지 못했어요"; }
		for i in $(seq 48); do
			notready=""
			for c in $INFRA; do
				[ "$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null)" = healthy ] || notready="$notready $c"
			done
			[ -z "$notready" ] && break
			sleep 5
		done
		[ -z "$notready" ] || fail "4분 안에 준비되지 않은 인프라:$notready (Docker 메모리 4GB 이상인지 확인)"

		say "4/7 앱 띄우기 (앱마다 3분 안, 남은 시간 $(left)초)"
		first=${apps%% *}
		start_app "$first"
		wait_app "$first"
		for a in gateway ${apps#* }; do start_app "$a"; done
		for a in gateway ${apps#* }; do wait_app "$a"; done

		say "5/7 게이트웨이(8000)로 시드 계정 로그인"
		for email in user1@crewrun.com seller1@crewrun.com admin@crewrun.com; do
			code=""
			for i in $(seq 12); do
				code=$(login_code http://localhost:8000 "$email")
				[ "$code" = 200 ] && break
				sleep 5
			done
			[ "$code" = 200 ] || fail "$email 로그인 응답 $code: $(head -c 300 "$STATE/login.json")"
		done
		# 더미 대회 판매 시작을 지금으로 당긴다. 판매 상태 배치(5분 주기)가 본 테스트 전에 판매 중으로 바꾼다
		echo " $apps " | grep -q ' product ' &&
			sql "update race set sale_open_date = curdate(), individual_open_at = now() - interval 1 minute where title like '[더미]%'"

		say "6/7 프론트를 실제 백엔드 모드로 띄우기 (localhost:$FRONT_PORT → 게이트웨이 8000)"
		(cd "$FRONT" || exit 1
			API_MOCKING=off BACKEND_URL=http://localhost:8000 NEXT_PUBLIC_TOSS_CLIENT_KEY="$TOSS_KEY" nohup pnpm dev --port $FRONT_PORT </dev/null \
			>"$STATE/logs/front.log" 2>&1 & echo $! >"$STATE/pids/front")
		for i in $(seq 36); do
			[ "$(curl -s -o /dev/null -m 30 -w '%{http_code}' http://localhost:$FRONT_PORT/login)" = 200 ] && break
			sleep 5
		done
		code=$(login_code http://localhost:$FRONT_PORT user1@crewrun.com)
		db_id=$(sql "select member_id from member_member where email = 'user1@crewrun.com'")
		grep -q "\"id\":$db_id[,}]" "$STATE/login.json" && [ "$code" = 200 ] \
			|| fail "프론트가 내 백엔드를 부르지 않아요 (응답 $code, DB 회원 ID $db_id). frontend/.e2e-lunch/logs/front.log를 확인해 주세요"
		say "프론트 → http://localhost:8000 연결 확인"

		say "7/7 Playwright로 로그인 화면 하나 (남은 시간 $(left)초)"
		(cd "$FRONT" && E2E_BASE_URL=http://localhost:$FRONT_PORT E2E_JSON="$STATE/pretest.json" \
			pnpm exec playwright test -c playwright.lunch.config.ts --grep @pretest) \
			|| fail "로그인 화면 테스트 실패. 스크린샷은 frontend/test-results/ 안에 있어요"
		echo "$name" >"$STATE/name"
		say "프리테스트 통과 ($(( $(date +%s) - START ))초). 앱을 켜 둔 채 끝나요. 다음: ./scripts/e2e-lunch.sh run $name"
		;;

	run)
		healthy gateway || fail "게이트웨이가 꺼져 있어요. pretest를 먼저 통과시켜 주세요"
		for a in $apps; do healthy "$a" || fail "$a이(가) 꺼져 있어요. pretest를 다시 실행해 주세요"; done
		# 13:50이면 끝낸다. 이미 지났으면(미리 돌려 볼 때) 지금부터 60분
		deadline=$(date -j -f '%H:%M:%S' '13:50:00' +%s 2>/dev/null || date -d '13:50' +%s)
		[ "$deadline" -gt "$(date +%s)" ] || deadline=$(( $(date +%s) + 3600 ))
		deadline=${E2E_DEADLINE:-$deadline}
		# 세 번째 인자로 시나리오 번호를 주면 그 시나리오만 다시 돌린다. 예: run dawit "25|37"
		grep="@$name"
		[ -n "${3:-}" ] && grep="^(?=.*@$name)(?=.*(${3}) )"
		say "시나리오 실행: $grep (마감 $(date -r "$deadline" '+%H:%M' 2>/dev/null || date -d "@$deadline" '+%H:%M'))"
		(cd "$FRONT" && E2E_BASE_URL=http://localhost:$FRONT_PORT E2E_DEADLINE=$deadline E2E_JSON="$FRONT/e2e-$name.json" \
			pnpm exec playwright test -c playwright.lunch.config.ts --grep "$grep")
		rc=$?
		say "결과 파일: frontend/e2e-$name.json (실패 화면은 frontend/test-results/)"
		say "앱 · 프론트 · 컨테이너 정리"
		stop_all
		(cd "$BACK" && docker compose down >/dev/null 2>&1)
		if [ "$rc" != 0 ]; then
			echo
			echo "실패한 시나리오가 있어요. 아래 명령을 실행하면 Claude가 원인을 나누고 다시 돌려요."
			echo "  claude \"frontend/e2e/FIX.md대로 e2e-$name.json의 실패를 고치고 다시 돌려 줘\""
		fi
		exit $rc
		;;

	stop)
		stop_all
		(cd "$BACK" && docker compose down >/dev/null 2>&1)
		say "정리 끝"
		;;

	*)
		echo "사용법: $0 prepare|pretest|run <이름> 또는 $0 stop" >&2
		exit 2
		;;
esac
