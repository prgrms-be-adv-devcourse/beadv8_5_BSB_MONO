#!/usr/bin/env bash
# 메인 EC2의 k3s에 앱을 배포한다. GitHub Actions(k8s-cd.yml)가 SSM으로 이 스크립트를 부른다 (손으로 실행해도 된다)
#   1. 저장소를 배포할 커밋으로 맞춘다 (main 또는 main에 머지된 커밋만)
#   2. Parameter Store(/crewrun/prod/*)에서 비밀값을 읽어 secret.yaml을 만든다 (꼭 채움 키가 비면 멈춘다)
#   3. GHCR 이미지를 받을 로그인 정보(Secret ghcr-pull)를 만든다
#   4. 앱 이미지 태그를 그 커밋 SHA로 바꾸고, 설정이 바뀌었으면 Pod가 다시 뜨게 표시를 붙여 적용한다
#   5. 모든 Deployment가 새 Pod로 바뀔 때까지 기다린다 (실패하면 Pod 상태를 찍고 실패로 끝난다)
#   끝나면(실패해도) secret.yaml과 임시 파일을 지운다
#
# 사용 (메인 EC2에서, root)
#   sudo bash /opt/crewrun/infra/main-node/deploy.sh main
#   sudo bash /opt/crewrun/infra/main-node/deploy.sh <커밋 SHA>   # Actions가 쓰는 방식
# 이미지는 GitHub Actions가 커밋 SHA 태그로 GHCR에 먼저 올려 둔다. 그 커밋의 이미지가 없으면 ImagePullBackOff가 난다
set -euo pipefail

REF="${1:?배포할 main 또는 main에 들어간 커밋 SHA를 주세요. 예: deploy.sh main}"
APP_DIR="${APP_DIR:-/opt/crewrun}"
NAMESPACE=crewrun
PARAM_PATH=/crewrun/prod
REGISTRY_PARAM=/crewrun/registry/GHCR_AUTH
REGISTRY=ghcr.io/prgrms-be-adv-devcourse
APPS=(gateway member-service cash-service payout-service file-service order-service
	product-service queue-service notification-service inquiry-service board-service ai-service web)
# 비면 앱이 뜨지 않거나 저장된 데이터를 읽지 못하는 키
REQUIRED=(MYSQL_USER MYSQL_PASS JWT_SECRET CRYPTO_HMAC_KEY CRYPTO_PASSWORD CRYPTO_SALT INTERNAL_API_KEY)
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

log() { echo "[deploy] $*"; }
fail() { echo "[deploy] $*" >&2; exit 1; }

cd "$APP_DIR"
SECRET_FILE="$APP_DIR/k8s/overlays/aws/secret.yaml"
WORK=""
cleanup() { rm -f "$SECRET_FILE"; [ -n "$WORK" ] && rm -rf "$WORK"; }
trap cleanup EXIT

# 1. 커밋 맞추기 (infra/data-node/deploy.sh와 같은 규칙)
log "저장소를 $REF 로 맞추기"
git fetch --prune origin
if ! git diff --quiet || ! git diff --cached --quiet; then
	fail "서버 저장소에 고친 파일이 있어 멈춥니다: git status 로 확인하세요"
fi
if [ "$REF" = "main" ]; then
	TARGET=$(git rev-parse --verify "origin/main^{commit}")
elif [[ "$REF" =~ ^[0-9a-f]{7,40}$ ]] && TARGET=$(git rev-parse --verify --quiet "${REF}^{commit}"); then
	:
else
	fail "main 또는 커밋 SHA만 받습니다 (없는 커밋이거나 다른 브랜치 이름): $REF"
fi
git merge-base --is-ancestor "$TARGET" origin/main || fail "main에 들어간 커밋만 배포합니다: $REF ($TARGET)"
git checkout --quiet --detach "$TARGET"
SHA=$(git rev-parse HEAD)
log "배포 커밋: ${SHA:0:8} $(git log -1 --format=%s)"

# 2. 비밀값 → secret.yaml
#    키 목록은 secret.yaml.example에서 읽는다. Parameter Store에 없는 키는 빈 값으로 넣는다 (앱 기본값이 없는 키도 있어 키 자체는 꼭 있어야 한다)
#    JSON으로 만든다(YAML로 읽힌다). 여러 줄 값(GA4 서비스 계정 키 등)도 그대로 들어간다
mapfile -t KEYS < <(grep -oE '^  [A-Z0-9_]+:' k8s/overlays/aws/secret.yaml.example | tr -d ' :')
log "Parameter Store $PARAM_PATH/ 에서 비밀값 ${#KEYS[@]}개 읽기"
PARAMS=$(aws ssm get-parameters-by-path --path "$PARAM_PATH/" --with-decryption \
	--query 'Parameters[].{k:Name,v:Value}' --output json)
umask 077
jq -n --argjson params "$PARAMS" --argjson keys "$(printf '%s\n' "${KEYS[@]}" | jq -R . | jq -s .)" --arg ns "$NAMESPACE" '
	($params | map({key: (.k | split("/") | last), value: .v}) | from_entries) as $found
	| {apiVersion: "v1", kind: "Secret", type: "Opaque",
	   metadata: {name: "app-secret", namespace: $ns},
	   stringData: ($keys | map({key: ., value: ($found[.] // "")}) | from_entries)}' > "$SECRET_FILE"
for key in "${REQUIRED[@]}"; do
	jq -e --arg k "$key" '.stringData[$k] | length > 0' "$SECRET_FILE" >/dev/null \
		|| fail "꼭 채워야 하는 비밀값이 비었습니다: $PARAM_PATH/$key"
done
EMPTY=$(jq -r '.stringData | to_entries | map(select(.value == "") | .key) | join(" ")' "$SECRET_FILE")
[ -n "$EMPTY" ] && log "빈 값으로 넣은 키(그 기능만 꺼짐): $EMPTY"

# 3. GHCR 로그인 정보 (이미지가 비공개라 필요하다). 값은 "아이디:토큰"을 base64로 바꾼 docker 로그인 형식
WORK=$(mktemp -d "$APP_DIR/k8s/overlays/deploy.XXXXXX")
GHCR_AUTH=$(aws ssm get-parameter --name "$REGISTRY_PARAM" --with-decryption --query Parameter.Value --output text)
jq -n --arg auth "$GHCR_AUTH" '{auths: {"ghcr.io": {auth: $auth}}}' > "$WORK/dockerconfig.json"
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
kubectl -n "$NAMESPACE" create secret generic ghcr-pull --type=kubernetes.io/dockerconfigjson \
	--from-file=.dockerconfigjson="$WORK/dockerconfig.json" --dry-run=client -o yaml | kubectl apply -f -
rm -f "$WORK/dockerconfig.json"

# 4. 이미지 태그와 설정 표시를 덧붙인 임시 kustomization을 만들어 적용한다 (저장소 파일은 고치지 않는다)
#    설정 표시: app-config·Secret이 바뀌면 값이 달라져 Pod가 다시 뜬다 (환경변수는 Pod가 시작할 때만 읽으므로)
CONFIG_HASH=$(cat "$SECRET_FILE" k8s/base/app-config.yaml k8s/overlays/aws/app-config.yaml | sha256sum | cut -c1-16)
{
	echo "apiVersion: kustomize.config.k8s.io/v1beta1"
	echo "kind: Kustomization"
	echo "resources:"
	echo "  - ../aws"
	echo "images:"
	for app in "${APPS[@]}"; do
		echo "  - name: $REGISTRY/bukang-$app"
		echo "    newTag: \"$SHA\""
	done
	echo "patches:"
	echo "  - target:"
	echo "      kind: Deployment"
	echo "    patch: |-"
	echo "      - op: add"
	echo "        path: /spec/template/metadata/annotations"
	echo "        value:"
	echo "          crewrun.io/config-hash: \"$CONFIG_HASH\""
} > "$WORK/kustomization.yaml"

log "적용 (이미지 태그 ${SHA:0:8}, 설정 표시 $CONFIG_HASH)"
kubectl kustomize --load-restrictor LoadRestrictionsNone "$WORK" | kubectl apply -f -

# 5. 새 Pod로 바뀔 때까지 기다리기. Spring 앱은 CPU 2개 서버에서 하나당 2~3분 걸린다
FAILED=()
for deploy in $(kubectl -n "$NAMESPACE" get deploy -o name); do
	kubectl -n "$NAMESPACE" rollout status "$deploy" --timeout=600s || FAILED+=("$deploy")
done
if [ "${#FAILED[@]}" -gt 0 ]; then
	kubectl -n "$NAMESPACE" get pods -o wide >&2
	fail "새 Pod로 바뀌지 못했습니다: ${FAILED[*]} (kubectl -n $NAMESPACE describe/logs 로 원인 확인)"
fi
kubectl -n "$NAMESPACE" get pods
log "완료: ${SHA:0:8}"
