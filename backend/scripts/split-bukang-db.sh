#!/usr/bin/env bash
# 옛 공용 DB(bukang-db)의 표를 서비스별 DB(crewrun_<서비스>)로 옮긴다 (BUK-238)
#
# 운영 보조 EC2의 MySQL에서 한 번 돌린다 (로컬은 bukang-db 하나를 그대로 쓰므로 돌리지 않는다). 빈 볼륨으로 새로 만든 MySQL은 initdb가 빈 DB를 만들어 필요 없다
#   보조 EC2: bash backend/scripts/split-bukang-db.sh            (서버의 저장소 루트에서. 절차는 infra/data-node/README.md)
#   미리 보기: bash backend/scripts/split-bukang-db.sh --dry-run  (옮길 표와 갈 DB만 보여 주고 아무것도 바꾸지 않는다)
#
# 하는 일 (MySQL 컨테이너 안에서 root로)
#   1. 서비스별 DB를 만들고 앱 계정(컨테이너의 MYSQL_USER)에 권한을 준다 (docker/mysql/initdb/service-dbs.sh와 같은 코드)
#   2. bukang-db의 표마다 주인 서비스를 정해 RENAME TABLE 한 문장으로 한꺼번에 옮긴다 (하나라도 실패하면 아무것도 안 옮겨진다)
#      표 주인: 이름의 첫 단어(member_member → member), flyway_schema_history_<서비스> → 그 서비스,
#               BATCH_* → file(Spring Batch 실행 기록), race·race_course·participation_hold → product
#   주인을 모르는 표가 하나라도 있으면 아무것도 옮기지 않고 멈춘다
#
# 옮기는 동안 그 표를 쓰는 앱이 돌면 안 된다. k3s의 앱 Pod를 먼저 멈춘다
# RENAME은 파일을 복사하지 않고 이름만 바꿔 표가 많아도 바로 끝난다. 되돌리기는 같은 RENAME을 거꾸로 한다 (infra/data-node/README.md)
set -euo pipefail

CONTAINER="${MYSQL_CONTAINER:-bukang-db}"
DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then
	DRY_RUN=1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DBS_SH="${SCRIPT_DIR}/../docker/mysql/initdb/service-dbs.sh"

# 컨테이너 안에서 돌릴 코드. 앞에 service-dbs.sh를 붙여 DB 목록과 docker_process_sql을 함께 넘긴다
MOVE_SH=$(cat <<'EOF'

OLD_DB='bukang-db'
root_sql() { MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot -N -s "$@"; }

if [[ -z "$(root_sql -e "SHOW DATABASES LIKE '${OLD_DB}'")" ]]; then
	echo "[split] ${OLD_DB}가 없어 옮길 표가 없습니다"
	exit 0
fi

owner_of() {
	local table="$1" lower="${1,,}"
	case "${lower}" in
		flyway_schema_history_*) echo "${lower#flyway_schema_history_}" ;;
		batch_*) echo file ;;
		race | race_course | participation_hold) echo product ;;
		*) echo "${lower%%_*}" ;;
	esac
}

renames=()
unknown=()
while IFS= read -r table; do
	[[ -z "${table}" ]] && continue
	owner="$(owner_of "${table}")"
	if [[ " ${CREWRUN_SERVICE_DBS[*]} " != *" crewrun_${owner} "* ]]; then
		unknown+=("${table}")
		continue
	fi
	renames+=("\`${OLD_DB}\`.\`${table}\` TO \`crewrun_${owner}\`.\`${table}\`")
	printf '[split] %-45s → crewrun_%s\n' "${table}" "${owner}"
done < <(root_sql -e "SELECT table_name FROM information_schema.tables WHERE table_schema = '${OLD_DB}' ORDER BY table_name")

# 옮길 자리에 같은 이름의 표가 이미 있으면 RENAME 전체가 실패하므로 먼저 알려 준다 (옮기기 전에 서비스를 띄워 Flyway가 빈 표를 만든 경우)
clash="$(root_sql -e "SELECT CONCAT(n.table_schema, '.', n.table_name) FROM information_schema.tables o
	JOIN information_schema.tables n ON n.table_name = o.table_name AND n.table_schema LIKE 'crewrun\\_%'
	WHERE o.table_schema = '${OLD_DB}'")"
if [[ -n "${clash}" ]]; then
	echo "[split] 옮길 자리에 이미 같은 이름의 표가 있어 아무것도 옮기지 않습니다:" >&2
	echo "${clash}" | sed 's/^/  /' >&2
	echo "[split] 서비스를 먼저 띄워 생긴 빈 표라면 그 DB를 지우고(DROP DATABASE crewrun_<서비스>) 다시 돌립니다" >&2
	exit 1
fi

if (( ${#unknown[@]} > 0 )); then
	echo "[split] 주인 서비스를 모르는 표가 있어 아무것도 옮기지 않습니다: ${unknown[*]}" >&2
	echo "[split] backend/scripts/split-bukang-db.sh의 owner_of에 규칙을 더한 뒤 다시 돌립니다" >&2
	exit 1
fi
if (( ${#renames[@]} == 0 )); then
	echo "[split] ${OLD_DB}에 표가 없습니다. 이미 옮겼습니다"
	exit 0
fi
if [[ "${DRY_RUN}" == 1 ]]; then
	echo "[split] 미리 보기: 표 ${#renames[@]}개를 옮길 예정입니다 (바꾼 것 없음)"
	exit 0
fi

sql="RENAME TABLE $(IFS=,; echo "${renames[*]}");"
echo "${sql}" | MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot
left="$(root_sql -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${OLD_DB}'")"
if [[ "${left}" != 0 ]]; then
	echo "[split] 옮기지 못했습니다. ${OLD_DB}에 표 ${left}개가 남아 있습니다" >&2
	exit 1
fi
echo "[split] 표 ${#renames[@]}개를 옮겼습니다. ${OLD_DB}는 비어 있습니다 (DB는 지우지 않고 둡니다)"
EOF
)

if [[ "${DRY_RUN}" == 1 ]]; then
	# 미리 보기는 DB를 만들지도 않는다: service-dbs.sh 끝의 실행 줄을 빼고 목록만 넘긴다
	PREFIX="$(grep -v '^crewrun_create_service_dbs$' "${SERVICE_DBS_SH}")"
else
	PREFIX="$(cat "${SERVICE_DBS_SH}")"
fi

printf '%s\nDRY_RUN=%s\n%s\n' "${PREFIX}" "${DRY_RUN}" "${MOVE_SH}" | docker exec -i "${CONTAINER}" bash -s
