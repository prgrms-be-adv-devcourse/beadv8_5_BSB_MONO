#!/usr/bin/env bash
# ghcr-cleanup.yml이 부른다. 앱마다 GHCR 이미지 버전을 정리해 저장 용량이 계속 늘지 않게 한다 (CRU-125)
#
# 남기는 것 (앱마다)
#   1. 최근 이미지 1 + KEEP_OLD개 (기본 3): 지금 운영 중인 이미지 + 되돌리기용 옛 이미지 3개
#      "이미지 하나"는 GHCR 버전 하나다. 안 바뀐 앱은 배포 때 태그만 더 붙으므로(k8s-cd retag) 버전이 늘지 않는다
#      → 앱마다 최근 4번의 "다시 빌드한" 이미지가 남는다. 버전은 만들어진 순서(created_at)로 고른다
#   2. buildcache 태그 버전 (이미지 빌드 캐시, k8s-cd의 cache-to)
#   3. 위 버전에 딸린 태그 없는 하위 버전 (manifest list 안의 플랫폼 이미지·빌드 증명). 지우면 남긴 이미지가 깨진다
#   4. 만든 지 MIN_AGE_MIN분(기본 60)이 안 된 버전. 지금 돌고 있는 빌드가 올린 것일 수 있다
# 지우는 것: 그 밖의 모든 버전 (오래된 SHA 이미지와 그 하위 버전, 덮어써져 태그가 빠진 옛 buildcache)
#
# 사용: ghcr-cleanup.sh
#   DRY_RUN=true(기본)면 지울 목록만 보여 주고 지우지 않는다. false여야 실제로 지운다
#   GH_TOKEN: 패키지 목록 읽기·버전 지우기 권한 (워크플로의 GITHUB_TOKEN, packages: write)
# 필요: gh, jq, docker buildx (GHCR 로그인된 상태)
set -euo pipefail

ORG="${ORG:-prgrms-be-adv-devcourse}"
REGISTRY="ghcr.io/$ORG"
KEEP_OLD="${KEEP_OLD:-3}"
MIN_AGE_MIN="${MIN_AGE_MIN:-60}"
DRY_RUN="${DRY_RUN:-true}"
APPS=(gateway member-service cash-service payout-service file-service order-service
	product-service queue-service notification-service inquiry-service board-service ai-service web)

keep_images=$((KEEP_OLD + 1))
cutoff=$(date -u -d "-${MIN_AGE_MIN} minutes" +%Y-%m-%dT%H:%M:%SZ)
total_deleted=0
failed=0

echo "[cleanup] 앱마다 최근 이미지 ${keep_images}개(운영 중 1 + 옛 ${KEEP_OLD})와 buildcache를 남깁니다. DRY_RUN=$DRY_RUN"

for app in "${APPS[@]}"; do
	pkg="bukang-$app"
	# 버전 목록: [{id, name(digest), created_at, tags}]
	if ! versions=$(gh api --paginate "/orgs/$ORG/packages/container/$pkg/versions?per_page=100" \
		--jq '.[] | {id, name, created_at, tags: .metadata.container.tags}' | jq -s '.'); then
		echo "[cleanup] $app: 버전 목록을 읽지 못해 건너뜁니다" >&2
		failed=1
		continue
	fi

	# 남길 태그 버전: buildcache + SHA 태그가 붙은 버전 중 최근 keep_images개
	keep_tagged=$(jq -r --argjson n "$keep_images" '
		(map(select(.tags | index("buildcache"))) | map(.name))
		+ (map(select((.tags | length) > 0 and (.tags | index("buildcache") | not)))
			| sort_by(.created_at) | reverse | .[:$n] | map(.name))
		| .[]' <<<"$versions")

	# 남길 버전의 하위 버전(digest)도 남긴다. 하나라도 못 읽으면 이 앱은 지우지 않는다 (남긴 이미지를 깨뜨리지 않으려고)
	keep_all="$keep_tagged"
	inspect_ok=true
	while IFS= read -r digest; do
		[ -z "$digest" ] && continue
		if ! raw=$(docker buildx imagetools inspect --raw "$REGISTRY/$pkg@$digest"); then
			inspect_ok=false
			break
		fi
		children=$(jq -r '.manifests[]?.digest' <<<"$raw")
		[ -n "$children" ] && keep_all+=$'\n'"$children"
	done <<<"$keep_tagged"
	if [ "$inspect_ok" != true ]; then
		echo "[cleanup] $app: 남길 이미지의 구성을 읽지 못해 이 앱은 건너뜁니다" >&2
		failed=1
		continue
	fi

	keep_json=$(jq -R -s -c 'split("\n") | map(select(length > 0))' <<<"$keep_all")
	# 지울 버전: 남길 목록에 없고, 만든 지 MIN_AGE_MIN분이 지난 것
	to_delete=$(jq -r --argjson keep "$keep_json" --arg cutoff "$cutoff" '
		map(select((.name as $d | $keep | index($d) | not) and .created_at < $cutoff))
		| .[] | "\(.id)\t\(.name)\t\(.created_at)\t\(.tags | join(","))"' <<<"$versions")

	total=$(jq 'length' <<<"$versions")
	count=$(grep -c . <<<"$to_delete" || true)
	echo "[cleanup] $app: 전체 ${total}개 중 ${count}개 지움 (남김: 태그 버전 $(grep -c . <<<"$keep_tagged"), 하위 포함 $(jq 'length' <<<"$keep_json"))"

	while IFS=$'\t' read -r id digest created tags; do
		[ -z "$id" ] && continue
		echo "  - ${digest:7:12} ${created} [${tags:-태그 없음}]"
		if [ "$DRY_RUN" = false ]; then
			if gh api -X DELETE "/orgs/$ORG/packages/container/$pkg/versions/$id" >/dev/null; then
				total_deleted=$((total_deleted + 1))
			else
				echo "    지우지 못했습니다 (id $id)" >&2
				failed=1
			fi
		fi
	done <<<"$to_delete"
done

echo "[cleanup] 끝. 지운 버전 ${total_deleted}개 (DRY_RUN=$DRY_RUN)"
exit "$failed"
