#!/usr/bin/env bash
# 보조 EC2(데이터 서버)에 인프라(MySQL·Redis·Redpanda·Elasticsearch)를 띄우거나 바꾼다 (S3는 실제 AWS S3라 띄우지 않는다)
#   1. 저장소를 받은 커밋으로 맞춘다 (git fetch → checkout). main에 들어간 커밋만 받는다
#      운영 DB 서버라 리뷰를 거치지 않은 브랜치(dev, PR 브랜치)의 설정이 올라가지 않게 한다
#   2. compose로 바뀐 서비스만 다시 만든다 (설정이 같으면 그대로 둔다. 데이터는 이름 있는 볼륨에 남는다)
#   3. 모두 healthy가 될 때까지 기다린다
# 앱 배포(메인 k3s)와 따로 돈다. 인프라는 자주 바뀌지 않으므로 data-node-cd.yml을 손으로 실행할 때만 돈다
#
# 사용 (보조 EC2에서, root)
#   sudo bash /opt/crewrun/infra/data-node/deploy.sh main        # main 최신 커밋
#   sudo bash /opt/crewrun/infra/data-node/deploy.sh <커밋 SHA>  # main에 들어간 커밋 (Actions가 쓰는 방식)
#
# 이 스크립트는 1단계에서 git checkout으로 자기 파일을 바꾼다 (infra/main-node/deploy.sh와 같은 처리)
#   - 본문을 main() 함수로 감싸 맨 끝에서 부른다 → 파일을 끝까지 읽은 뒤 실행해 도중에 바뀌어도 꼬이지 않는다
#   - checkout 뒤 이 파일이 바뀌었으면 배포할 커밋의 deploy.sh로 한 번 다시 실행한다 → 새로 더한 서비스(exporter 등)가 첫 배포부터 뜬다
set -euo pipefail

SELF="$(readlink -f "${BASH_SOURCE[0]}")"
# checkout 전 이 파일의 내용 지문. 1단계 끝에서 바뀌었는지 비교한다
SELF_SUM="$(sha256sum "$SELF" | cut -d' ' -f1)"

main() {
REF="${1:?배포할 main 또는 main에 들어간 커밋 SHA를 주세요. 예: deploy.sh main}"
APP_DIR="${APP_DIR:-/opt/crewrun}"
# 뒤의 4개는 지표 exporter (BUK-257). 메인 k3s의 수집기가 가져간다
SERVICES=(bukang-db bukang-redis bukang-kafka bukang-elasticsearch
	redis-exporter mysqld-exporter elasticsearch-exporter node-exporter)

log() { echo "[data-deploy] $*"; }

cd "$APP_DIR"

if [ ! -f infra/data-node/.env ]; then
	echo "[data-deploy] infra/data-node/.env 가 없습니다. setup.sh를 먼저 실행하고 값을 채우세요" >&2
	exit 1
fi

# 1. 커밋 맞추기. 서버에서 고친 파일이 있으면 덮어쓰지 않고 멈춘다 (.env는 git 제외라 상관없다)
log "저장소를 $REF 로 맞추기"
git fetch --prune origin
if ! git diff --quiet || ! git diff --cached --quiet; then
	echo "[data-deploy] 서버 저장소에 고친 파일이 있어 멈춥니다: git status 로 확인하세요" >&2
	exit 1
fi
# 받은 값을 커밋으로 바꾼다. 브랜치 이름은 main만 받는다 (dev 등 다른 브랜치 이름은 거부)
#   main은 서버에 남은 옛 로컬 main이 아니라 방금 받은 origin/main으로 본다
if [ "$REF" = "main" ]; then
	TARGET=$(git rev-parse --verify "origin/main^{commit}")
elif [[ "$REF" =~ ^[0-9a-f]{7,40}$ ]] && TARGET=$(git rev-parse --verify --quiet "${REF}^{commit}"); then
	:
else
	echo "[data-deploy] main 또는 커밋 SHA만 받습니다 (없는 커밋이거나 다른 브랜치 이름): $REF" >&2
	exit 1
fi
# 그 커밋이 origin/main 기록 안에 있을 때만 배포한다 (main 최신이거나 main에 머지된 커밋)
if ! git merge-base --is-ancestor "$TARGET" origin/main; then
	echo "[data-deploy] main에 들어간 커밋만 배포합니다. 받은 값: $REF ($TARGET)" >&2
	exit 1
fi
git checkout --detach "$TARGET"
SHA=$(git rev-parse HEAD)
log "지금 커밋: ${SHA:0:8} $(git log -1 --format=%s)"

# 배포할 커밋에서 deploy.sh가 바뀌었으면 그 deploy.sh로 다시 실행한다 (커밋 SHA로 넘겨 한 번만)
if [ "${DEPLOY_REEXEC:-}" != "$SHA" ] && [ "$(sha256sum "$SELF" | cut -d' ' -f1)" != "$SELF_SUM" ]; then
	log "deploy.sh가 바뀌어 ${SHA:0:8}의 deploy.sh로 다시 실행"
	DEPLOY_REEXEC="$SHA" exec bash "$SELF" "$SHA"
fi

# 2·3. 띄우기. ES 이미지(nori)는 여기서 빌드한다. --wait는 healthy가 될 때까지 기다리고 실패하면 0이 아닌 값으로 끝난다
COMPOSE=(docker compose -f backend/compose.yml -f infra/data-node/compose.data.yml --env-file infra/data-node/.env)
log "인프라 띄우기: ${SERVICES[*]}"
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 300 "${SERVICES[@]}"

"${COMPOSE[@]}" ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}'
log "완료"
}

# 파일을 끝까지 읽은 뒤 실행한다 (위 설명). exit로 끝내 이 뒤에 무엇이 붙어도 읽지 않는다
main "$@"
exit
