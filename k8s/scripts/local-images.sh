#!/usr/bin/env bash
# 로컬 k3d용 앱 이미지를 만들어 클러스터에 넣는다
#   이미지는 compose.yml의 build 설정으로 만든다 (Dockerfile·빌드 인자가 compose와 같다. web 빌드 인자는 backend/.env에서 읽는다)
#   k3d 클러스터는 내 PC의 Docker 이미지를 직접 보지 못하므로 k3d image import로 클러스터 안에 복사한다
#
# 사용 (저장소 루트에서)
#   k8s/scripts/local-images.sh                       # 앱 12개 모두
#   k8s/scripts/local-images.sh member-service web    # 고친 것만
# 이미지를 다시 넣은 뒤에는 Pod를 다시 띄운다 (태그가 latest로 같아서 apply만으로는 바뀌지 않는다)
#   kubectl rollout restart -n crewrun deploy/bukang-member-service
set -euo pipefail

cd "$(dirname "$0")/../.."

CLUSTER="${CLUSTER:-crewrun}"
ALL=(gateway member-service cash-service payout-service file-service order-service
	product-service queue-service notification-service board-service ai-service web)

if [ "$#" -gt 0 ]; then
	SERVICES=("$@")
else
	SERVICES=("${ALL[@]}")
fi

echo "[local-images] 빌드: ${SERVICES[*]}"
docker compose -f backend/compose.yml --profile app build "${SERVICES[@]}"

IMAGES=()
for service in "${SERVICES[@]}"; do
	IMAGES+=("bukang-${service}:latest")
done

echo "[local-images] k3d 클러스터 ${CLUSTER}에 넣기"
k3d image import -c "$CLUSTER" "${IMAGES[@]}"
echo "[local-images] 완료"
