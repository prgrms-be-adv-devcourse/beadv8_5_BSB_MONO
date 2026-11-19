#!/usr/bin/env bash
# 두 커밋 사이에 바뀐 파일을 보고, 이미지를 다시 만들어 교체해야 하는 앱 이름을 한 줄에 하나씩 찍는다 (CRU-31)
#   k8s-cd.yml의 plan job(무엇을 빌드할지)과 deploy.sh(무엇을 교체할지)가 함께 쓴다
#   판단 규칙을 이 파일 한 곳에만 두어, 빌드하는 쪽과 교체하는 쪽이 "바뀐 앱"을 다르게 보지 않게 한다
#
# 사용: changed-apps.sh <이전 커밋> <새 커밋>
#   예: changed-apps.sh 3f2df51a HEAD   →   product-service
#   이전 커밋이 비었거나 0000…(새 브랜치 push)이거나 이 저장소에 없는 커밋이면 앱 13개를 모두 찍는다
#   (무엇이 바뀌었는지 모를 때는 전부 다시 만드는 쪽이 안전하다)
#
# 규칙 (backend/Dockerfile이 이미지에 넣는 폴더 기준)
#   backend/<앱>/**                                    → 그 앱
#   backend/common/**, backend/jwt/**, backend/gradle/** → 백엔드 12개 전부 (모든 앱 jar에 들어가거나 빌드 도구라서)
#   backend/build.gradle.kts, settings.gradle.kts, gradlew, Dockerfile, .dockerignore → 백엔드 12개 전부
#   frontend/**                                        → web
#   빼는 것: 문서(*.md), 테스트 코드(src/test·src/testFixtures, jar에 들어가지 않음),
#            observability-dev(로컬 bootRun 전용, jar에 들어가지 않음), 그 밖의 폴더(k8s·infra·docs 등은 이미지와 상관없음)
set -euo pipefail

FROM="${1-}"
TO="${2:?새 커밋을 주세요. 예: changed-apps.sh <이전 커밋> HEAD}"

BACKEND_APPS=(gateway member-service cash-service payout-service file-service order-service
	product-service queue-service notification-service inquiry-service board-service ai-service)
APPS=("${BACKEND_APPS[@]}" web)

print_all() { printf '%s\n' "${APPS[@]}"; }

if [ -z "$FROM" ] || [[ "$FROM" =~ ^0+$ ]] || ! git rev-parse --verify --quiet "${FROM}^{commit}" >/dev/null; then
	print_all
	exit 0
fi
git rev-parse --verify --quiet "${TO}^{commit}" >/dev/null || { echo "[changed-apps] 없는 커밋입니다: $TO" >&2; exit 1; }

declare -A CHANGED=()
while IFS= read -r path; do
	case "$path" in
		*.md | */src/test/* | */src/testFixtures/*) continue ;;
	esac
	case "$path" in
		backend/common/* | backend/jwt/* | backend/gradle/* \
			| backend/build.gradle.kts | backend/settings.gradle.kts | backend/gradlew \
			| backend/Dockerfile | backend/.dockerignore)
			for app in "${BACKEND_APPS[@]}"; do CHANGED[$app]=1; done
			;;
		backend/*/*)
			module="${path#backend/}"
			module="${module%%/*}"
			for app in "${BACKEND_APPS[@]}"; do
				if [ "$app" = "$module" ]; then
					CHANGED[$app]=1
				fi
			done
			;;
		frontend/*)
			CHANGED[web]=1
			;;
	esac
done < <(git diff --name-only "$FROM" "$TO")

# APPS 순서로 찍는다 (빌드 목록·교체 순서가 매번 같게)
for app in "${APPS[@]}"; do
	if [ -n "${CHANGED[$app]-}" ]; then
		echo "$app"
	fi
done
