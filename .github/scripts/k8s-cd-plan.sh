#!/usr/bin/env bash
# k8s-cd.yml의 plan job이 부른다. 이번 커밋에서 이미지를 새로 빌드할 앱과, 태그만 붙일 앱을 나눈다 (CRU-31)
#   빌드: 직전 main 커밋 이후 소스가 바뀐 앱 (판단 규칙은 infra/main-node/changed-apps.sh)
#   태그만 붙이기: 바뀌지 않은 앱. 직전 커밋 이미지에 이번 커밋 SHA 태그를 하나 더 붙인다 (GHCR 안에서 몇 초, 빌드 없음)
#     → 배포한 모든 커밋에 앱 13개 이미지가 다 있게 된다. 되돌리기(예전 커밋으로 배포)가 그대로 동작한다
#     직전 커밋 이미지가 GHCR에 없으면(그 커밋의 빌드가 실패한 경우 등) 빌드로 옮긴다
#
# 사용: k8s-cd-plan.sh <직전 커밋> <이번 커밋>
#   직전 커밋이 비었으면(손으로 실행) 또는 0000…이면 13개 모두 빌드한다
# 출력 (GITHUB_OUTPUT이 있으면 거기에, 없으면 화면에)
#   matrix: build job의 strategy.matrix JSON. 예: {"include":[{"name":"product-service","context":"backend","module":"product-service"}]}
#   build:  빌드할 앱 이름 (공백 구분, 없으면 빈 값)
#   retag:  태그만 붙일 앱 이름 (공백 구분, 없으면 빈 값)
# 필요: git(전체 이력), jq, docker buildx (GHCR 로그인된 상태)
set -euo pipefail

BEFORE="${1-}"
SHA="${2:?이번 커밋을 주세요}"
REGISTRY="${REGISTRY:-ghcr.io/prgrms-be-adv-devcourse}"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"
APPS=(gateway member-service cash-service payout-service file-service order-service
	product-service queue-service notification-service inquiry-service board-service ai-service web)

declare -A CHANGED=()
while IFS= read -r app; do
	[ -n "$app" ] && CHANGED[$app]=1
done < <(bash "$(dirname "$0")/../../infra/main-node/changed-apps.sh" "$BEFORE" "$SHA")

BUILD=()
RETAG=()
for app in "${APPS[@]}"; do
	if [ -n "${CHANGED[$app]-}" ]; then
		BUILD+=("$app")
	elif docker buildx imagetools inspect "$REGISTRY/bukang-$app:$BEFORE" >/dev/null 2>&1; then
		RETAG+=("$app")
	else
		echo "[plan] $app: 직전 커밋 이미지(${BEFORE:0:8})가 없어 빌드합니다" >&2
		BUILD+=("$app")
	fi
done

# web은 frontend 폴더에서, 나머지는 backend 폴더에서 MODULE 인자로 빌드한다 (backend/Dockerfile)
MATRIX=$(printf '%s\n' "${BUILD[@]}" | jq -R -s -c '
	split("\n") | map(select(length > 0))
	| map(if . == "web" then {name: ., context: "frontend", module: ""} else {name: ., context: "backend", module: .} end)
	| {include: .}')

{
	echo "matrix=$MATRIX"
	echo "build=${BUILD[*]}"
	echo "retag=${RETAG[*]}"
} >> "$OUT"

echo "[plan] 빌드 ${#BUILD[@]}개: ${BUILD[*]:-없음}" >&2
echo "[plan] 태그만 붙이기 ${#RETAG[@]}개: ${RETAG[*]:-없음}" >&2
