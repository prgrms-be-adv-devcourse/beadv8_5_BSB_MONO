#!/usr/bin/env bash
# 로컬 k3d에 k8s 설정을 한 번에 적용한다 (overlays/local)
#   kustomize로 base + overlays/local을 합친 결과를 kubectl apply로 보낸다
#   kubectl apply -k 를 쓰지 않는 이유: nginx 설정을 k8s 폴더 밖(backend/docker/nginx)에서 읽어서
#   --load-restrictor LoadRestrictionsNone 이 필요한데, apply -k 에는 이 옵션을 붙일 수 없다
#
# 사용 (저장소 루트에서)
#   k8s/scripts/local-apply.sh            # 적용
#   k8s/scripts/local-apply.sh --wait     # 적용한 뒤 Pod가 모두 뜰 때까지 기다린다 (최대 10분)
# app-config.yaml·secret.yaml을 고쳤다면 적용한 뒤 앱을 다시 띄운다 (환경변수는 시작할 때만 읽는다)
#   kubectl rollout restart -n crewrun deploy
set -euo pipefail

cd "$(dirname "$0")/../.."

if [ ! -f k8s/overlays/local/secret.yaml ]; then
	echo "[local-apply] k8s/overlays/local/secret.yaml 이 없습니다. secret.yaml.example을 복사해 값을 채우세요" >&2
	exit 1
fi

kubectl kustomize --load-restrictor LoadRestrictionsNone k8s/overlays/local | kubectl apply -f -

if [ "${1:-}" = "--wait" ]; then
	kubectl rollout status -n crewrun deploy --timeout=600s
fi
