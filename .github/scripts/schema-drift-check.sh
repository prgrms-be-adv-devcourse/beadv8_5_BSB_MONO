#!/usr/bin/env bash
# 배포 뒤 스키마 드리프트 검사 (인덱스·컬럼·FK). 알림만 하고 배포는 막지 않는다.
#   k8s-cd.yml의 맨 끝(배포·게이트웨이2가 끝난 뒤)에서 돈다.
#
# 왜: Flyway의 validate는 "적용된 마이그레이션이 파일과 체크섬이 맞나"만 본다. 라이브 DB의 실제 스키마가
#   코드대로인지는 안 본다. 그래서 「create table if not exists 안의 인덱스가 먼저 생긴 표에선 조용히 skip」 같은
#   누락이나 손으로 건드린 드리프트를 못 잡는다. 이 스크립트가 그 틈을 메운다.
#
# 방법:
#   1) 정답: 러너에서 빈 MySQL 8.4(운영과 같은 문자셋)에 서비스별 Flyway를 돌려 "코드대로면 이래야 한다"를 만든다.
#      운영은 서비스마다 DB가 따로다(crewrun_<서비스>, BUK-238)라 DB별로 돌린다. 운영 ddl-auto=validate라 Flyway가 유일한 소스.
#   2) 라이브: 러너는 프라이빗 VPC의 DB에 직접 못 닿으므로, SSM으로 data-node EC2에서 bukang-db를 읽어 받아온다
#      (gzip+base64로 SSM 출력 24000자 제한을 피한다).
#   3) 양쪽 facts를 정렬해 diff. 다르면 Slack 알림(SLACK_WEBHOOK_URL). 배포는 계속한다(이 스크립트는 항상 0으로 끝난다).
#
# 필요(워크플로가 준다): docker(정답 빌드), AWS 자격 증명 OIDC(SSM), 환경변수 DATA_INSTANCE_ID, (선택) SLACK_WEBHOOK_URL
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="$HERE/schema-facts.sql"

# 운영에서 DB를 쓰는 서비스(=crewrun_<서비스> DB가 있는 서비스). gateway·ai-service는 DB가 없어 뺀다.
# infra/data-node/docker/mysql/initdb/service-dbs.sh의 CREWRUN_SERVICE_DBS와 같아야 한다.
SERVICES=(member cash payout file order product queue notification inquiry board)

MYSQL_IMAGE="mysql:8.4"
FLYWAY_IMAGE="flyway/flyway:11-alpine"
DB_CONTAINER="bukang-db"   # data-node의 MySQL 컨테이너 이름

# ── 라이브(운영)에서 사실을 긁어 오는 원격 명령을 만든다 ──────────────────────────────
# data-node EC2에서 root로 돈다(SSM). 컨테이너 안에서 root 비밀번호(컨테이너 env)로 조회해 gzip+base64로 한 줄로 찍는다.
# 그 한 줄 앞에 DRIFTB64: 표시를 붙여, ssm-run.sh의 로그 사이에서 집어낸다.
build_remote_command() {
	local sql
	sql="$(cat "$SQL_FILE")"
	cat <<REMOTE
set -e
docker exec -i ${DB_CONTAINER} sh -c 'exec mysql -uroot -p"\$MYSQL_ROOT_PASSWORD" -N -B' <<'SQL' | gzip | base64 -w0 | sed 's/^/DRIFTB64:/'
${sql}
SQL
echo
REMOTE
}

# ── 정답 DB를 러너에서 만든다 ────────────────────────────────────────────────────
build_reference() {
	local out="$1" net="drift-net-$$" pw="drift-root-pw" svc
	docker network create "$net" >/dev/null
	# MYSQL_ROOT_HOST=%: flyway 컨테이너가 네트워크로 root 접속할 수 있게 한다
	docker run -d --name drift-mysql --network "$net" \
		-e MYSQL_ROOT_PASSWORD="$pw" -e MYSQL_ROOT_HOST='%' "$MYSQL_IMAGE" \
		--character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
	trap 'docker rm -f drift-mysql >/dev/null 2>&1 || true; docker network rm "'"$net"'" >/dev/null 2>&1 || true' RETURN

	local i
	for i in $(seq 1 60); do
		docker exec drift-mysql mysqladmin ping -uroot -p"$pw" --silent >/dev/null 2>&1 && break
		sleep 2
	done

	for svc in "${SERVICES[@]}"; do
		local dir="$HERE/../../backend/${svc}-service/src/main/resources/db/migration"
		[ -d "$dir" ] || { echo "::warning::마이그레이션 폴더 없음: $dir (건너뜀)"; continue; }
		docker exec drift-mysql mysql -uroot -p"$pw" -e \
			"CREATE DATABASE IF NOT EXISTS \`crewrun_${svc}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
		docker run --rm --network "$net" -v "$(cd "$dir" && pwd):/flyway/sql:ro" "$FLYWAY_IMAGE" \
			-url="jdbc:mysql://drift-mysql:3306/crewrun_${svc}" -user=root -password="$pw" \
			-connectRetries=30 -locations=filesystem:/flyway/sql migrate >/dev/null
	done

	docker exec -i drift-mysql mysql -uroot -p"$pw" -N -B < "$SQL_FILE" | LC_ALL=C sort > "$out"
}

# ── 라이브 facts를 SSM으로 받아온다 ──────────────────────────────────────────────
fetch_live() {
	local out="$1" raw
	raw="$(bash "$HERE/ssm-run.sh" "${DATA_INSTANCE_ID:?DATA_INSTANCE_ID 필요}" 180 "$(build_remote_command)")"
	# 로그 사이에서 DRIFTB64: 줄만 집어 디코드
	printf '%s\n' "$raw" | grep '^DRIFTB64:' | head -1 | sed 's/^DRIFTB64://' | base64 -d | gzip -d | LC_ALL=C sort > "$out"
	[ -s "$out" ] || { echo "::error::라이브 스키마를 받지 못했습니다(출력 비어 있음). SSM/DB 접속을 확인하세요"; return 1; }
}

# ── 비교·알림 (Docker·AWS 없이 돌아가는 순수 로직. 자가검증 대상) ──────────────────────
# 쓰임: compare_and_report <정답파일> <라이브파일>  → 드리프트 요약을 stdout에 찍고, 있으면 0이 아닌 값을 THINK... 아니다:
#   반환값으로 드리프트 유무를 알린다(0=같음, 10=드리프트). 알림/종료는 호출부가 판단한다.
compare_and_report() {
	local ref="$1" live="$2"
	# comm: 정답에만 있음(=라이브에 빠짐)과 라이브에만 있음을 가른다. 양쪽 다 정렬돼 있어야 한다.
	local missing extra
	missing="$(comm -23 "$ref" "$live")"   # 코드엔 있는데 라이브엔 없음 (인덱스 반영 누락 등)
	extra="$(comm -13 "$ref" "$live")"     # 라이브에만 있음 (손으로 넣음 등)
	if [ -z "$missing" ] && [ -z "$extra" ]; then
		echo "스키마 드리프트 없음 (인덱스·컬럼·FK가 코드와 일치)."
		return 0
	fi
	echo "스키마 드리프트 발견 — 라이브 DB가 배포된 코드와 다릅니다."
	if [ -n "$missing" ]; then
		echo
		echo "[코드엔 있는데 라이브엔 없음] (마이그레이션 누락 의심)"
		printf '%s\n' "$missing" | sed 's/^/  - /'
	fi
	if [ -n "$extra" ]; then
		echo
		echo "[라이브에만 있음] (손으로 변경했거나 롤백 누락 의심)"
		printf '%s\n' "$extra" | sed 's/^/  - /'
	fi
	return 10
}

# ── Slack 알림 (웹훅 없으면 로그로 대체) ─────────────────────────────────────────
notify() {
	local text="$1"
	if [ -n "${SLACK_WEBHOOK_URL:-}" ]; then
		# 텍스트는 JSON 값이라 jq로 안전하게 감싼다
		curl -sS -X POST -H 'Content-Type: application/json' \
			--data "$(jq -n --arg t "$text" '{text: $t}')" "$SLACK_WEBHOOK_URL" >/dev/null \
			&& echo "[drift] Slack 알림 보냄" || echo "::warning::Slack 전송 실패(웹훅 확인)"
	else
		echo "::warning::SLACK_WEBHOOK_URL이 없어 Slack 대신 로그로만 알립니다"
	fi
	# Actions 로그·요약에도 남긴다
	printf '%s\n' "$text"
}

main() {
	local work ref live report
	work="$(mktemp -d)"; ref="$work/reference.txt"; live="$work/live.txt"
	echo "[drift] 1/3 정답 스키마 생성(서비스 ${#SERVICES[@]}개, MySQL 8.4)"
	build_reference "$ref"
	echo "[drift] 2/3 라이브 스키마 조회(SSM → data-node)"
	fetch_live "$live"
	echo "[drift] 3/3 비교"
	if report="$(compare_and_report "$ref" "$live")"; then
		echo "$report"
	else
		notify ":rotating_light: [스키마 드리프트] 배포 뒤 라이브 DB가 코드와 다릅니다 (커밋 ${GITHUB_SHA:-?}).

$report

확인: 운영 반영이 누락된 인덱스/컬럼이 있을 수 있습니다. k8s-cd 로그의 drift-check를 보세요."
	fi
	# 알림만 한다. 드리프트가 있어도 배포를 되돌리지 않으므로 항상 성공으로 끝낸다.
	return 0
}

# __compare-test: Docker·AWS 없이 비교 로직만 자가검증한다 (CI·로컬 공용)
case "${1:-}" in
	__compare-test)
		t="$(mktemp -d)"
		printf 'COL\tcrewrun_member\tmember_member\temail\tvarchar(255)\tNO\nIDX\tcrewrun_order\trace\tidx_race_seller\t0\tBTREE\tseller_id\n' | LC_ALL=C sort > "$t/ref"
		# 라이브: idx_race_seller가 빠지고(누락), 손으로 만든 idx_hand가 추가됨
		printf 'COL\tcrewrun_member\tmember_member\temail\tvarchar(255)\tNO\nIDX\tcrewrun_order\trace\tidx_hand\t1\tBTREE\ttitle\n' | LC_ALL=C sort > "$t/live"
		out="$(compare_and_report "$t/ref" "$t/live")" && rc=0 || rc=$?
		echo "$out"
		echo "--- 검증 ---"
		[ "$rc" = 10 ] && echo "$out" | grep -q 'idx_race_seller' && echo "$out" | grep -q 'idx_hand' \
			&& echo "PASS: 누락·추가 모두 잡음(반환 $rc)" || { echo "FAIL(반환 $rc)"; exit 1; }
		;;
	*)
		main
		;;
esac
