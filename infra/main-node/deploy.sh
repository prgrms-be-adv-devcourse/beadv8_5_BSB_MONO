#!/usr/bin/env bash
# 메인 EC2의 k3s에 앱을 배포한다. GitHub Actions(k8s-cd.yml)가 SSM으로 이 스크립트를 부른다 (손으로 실행해도 된다)
#   1. 저장소를 배포할 커밋으로 맞춘다 (main 또는 main에 머지된 커밋만)
#   2. Parameter Store(/crewrun/prod/*)에서 비밀값을 읽어 secret.yaml을 만든다 (꼭 채움 키가 비면 멈춘다)
#   3. GHCR 이미지를 받을 로그인 정보(Secret ghcr-pull)와 지표 수집기 비밀번호(Secret monitoring-remote-write)를 만든다
#   4. 앱마다 지금 떠 있는 이미지 태그와 배포할 커밋을 비교해 교체할 앱을 정한다 (CRU-31)
#      소스가 바뀐 앱(changed-apps.sh)은 새 SHA 태그로, 나머지는 지금 태그 그대로 둔다
#      설정(app-config·Secret)이 바뀌었으면 모든 앱이 교체 대상이다 (환경변수는 Pod가 시작할 때만 읽으므로)
#   5. 교체할 앱을 뺀 나머지(설정 객체·nginx 등)를 먼저 적용하고, 교체할 앱은 하나씩 적용해 새 Pod가 뜰 때까지 기다린 뒤 다음 앱으로 간다
#      앱마다 RollingUpdate라 새 Pod가 뜬 뒤 옛 Pod가 내려간다. 한 번에 한 앱만 두 벌이 되어 메모리가 넘치지 않는다
#      (2026-11-17: 13개를 한꺼번에 바꾸다 메인 EC2 메모리가 차서 k3s가 멈췄다)
#   6. 모든 앱이 바뀌면 어떤 컨테이너도 쓰지 않는 이미지를 지운다 (옛 이미지가 쌓여 디스크가 차지 않게)
#   끝나면(실패해도) secret.yaml과 임시 파일을 지운다
#
# 사용 (메인 EC2에서, root)
#   sudo bash /opt/crewrun/infra/main-node/deploy.sh main
#   sudo bash /opt/crewrun/infra/main-node/deploy.sh <커밋 SHA>   # Actions가 쓰는 방식
#   sudo DEPLOY_DRY_RUN=1 bash /opt/crewrun/infra/main-node/deploy.sh <커밋 SHA>
#     교체 계획과 바뀔 내용(kubectl diff)만 찍고 클러스터는 바꾸지 않는다 (Secret도 서버에서 검사만 한다)
#     저장소 checkout은 하므로, 운영 저장소 대신 복사본에서 돌리려면 APP_DIR을 복사본으로 준다
# 이미지는 GitHub Actions가 커밋 SHA 태그로 GHCR에 먼저 올려 둔다. 그 커밋의 이미지가 없으면 ImagePullBackOff가 난다
#
# 이 스크립트는 1단계에서 git checkout으로 자기 파일을 바꾼다. bash는 파일을 읽으면서 실행하므로
#   - 본문을 main() 함수로 감싸 맨 끝에서 부른다 → 파일을 끝까지 읽은 뒤 실행해 도중에 바뀌어도 꼬이지 않는다
#   - checkout 뒤 이 파일이 바뀌었으면 배포할 커밋의 deploy.sh로 한 번 다시 실행한다 (1-1단계)
#   (2026-11-16: 이 처리가 없어 옛 흐름으로 배포되어 수집기 비밀번호 Secret과 새 앱 inquiry-service가 빠졌다)
set -euo pipefail

SELF="$(readlink -f "${BASH_SOURCE[0]}")"
# checkout 전 이 파일의 내용 지문. 1-1단계에서 바뀌었는지 비교한다
SELF_SUM="$(sha256sum "$SELF" | cut -d' ' -f1)"

main() {
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
# 바뀐 앱 판단 규칙. k8s-cd.yml의 plan job도 같은 파일을 쓴다
CHANGED_APPS="$(dirname "$SELF")/changed-apps.sh"
DRY_RUN="${DEPLOY_DRY_RUN:-}"
# 클러스터에 쓰는 명령. 계획만 볼 때는 API 서버에서 검사만 하고 저장하지 않는다
if [ -n "$DRY_RUN" ]; then
	KUBE_APPLY=(kubectl apply --dry-run=server -f -)
else
	KUBE_APPLY=(kubectl apply -f -)
fi
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

# 1-1. 배포할 커밋에서 deploy.sh가 바뀌었으면 그 deploy.sh로 다시 실행한다
#   지금 도는 것은 checkout 전 내용이라, 그대로 가면 새로 더한 단계·앱이 이번 배포에서 빠진다
#   커밋 SHA로 넘겨 같은 커밋을 다시 맞추고, DEPLOY_REEXEC로 한 번만 다시 실행한다 (secret.yaml을 만들기 전이라 지울 것이 없다)
if [ "${DEPLOY_REEXEC:-}" != "$SHA" ] && [ "$(sha256sum "$SELF" | cut -d' ' -f1)" != "$SELF_SUM" ]; then
	log "deploy.sh가 바뀌어 ${SHA:0:8}의 deploy.sh로 다시 실행"
	DEPLOY_REEXEC="$SHA" exec bash "$SELF" "$SHA"
fi

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
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || [ -n "$DRY_RUN" ] || kubectl create namespace "$NAMESPACE"
kubectl -n "$NAMESPACE" create secret generic ghcr-pull --type=kubernetes.io/dockerconfigjson \
	--from-file=.dockerconfigjson="$WORK/dockerconfig.json" --dry-run=client -o yaml | "${KUBE_APPLY[@]}"
rm -f "$WORK/dockerconfig.json"

# 3-1. 지표 수집기(metrics-agent)가 개인 AWS EC2의 Prometheus로 보낼 때 쓰는 비밀번호 (BUK-258)
#   2단계에서 읽어 둔 PARAMS에서 이 키만 골라 따로 Secret을 만든다
#   secret.yaml.example에 키가 없어 app-secret에는 들어가지 않는다 → 앱 12개의 환경 변수로 퍼지지 않는다
#   값이 없으면 경고만 하고 배포는 계속한다. 수집기는 Secret 없이도 뜨고 보내기만 실패한다 (metrics-agent.yaml의 optional)
RW_PASSWORD_PARAM="$PARAM_PATH/MONITORING_RW_PASSWORD"
# -j: 끝에 줄바꿈을 붙이지 않는다. 값은 파일로만 다루고 화면에 찍지 않는다
jq -j --arg name "$RW_PASSWORD_PARAM" '.[] | select(.k == $name) | .v' <<<"$PARAMS" > "$WORK/rw-password"
if [ -s "$WORK/rw-password" ]; then
	kubectl -n "$NAMESPACE" create secret generic monitoring-remote-write \
		--from-file=password="$WORK/rw-password" --dry-run=client -o yaml | "${KUBE_APPLY[@]}"
else
	log "지표 수집기 비밀번호가 없어 지표를 보내지 못합니다 (앱 배포는 계속): $RW_PASSWORD_PARAM"
fi
rm -f "$WORK/rw-password"

# 4. 무엇을 교체할지 정한다 (CRU-31)
#    설정 표시: app-config·Secret 내용의 지문. 바뀌면 Pod 템플릿 주석이 달라져 Pod가 다시 뜬다 (환경변수는 Pod가 시작할 때만 읽으므로)
CONFIG_HASH=$(cat "$SECRET_FILE" k8s/base/app-config.yaml k8s/overlays/aws/app-config.yaml | sha256sum | cut -c1-16)

#    지금 떠 있는 앱마다 이미지 태그와 설정 표시를 읽는다. 한 줄에 "Deployment 이름|이미지|설정 표시"
#    (구분자를 탭으로 하면 빈 칸이 붙어 읽혀 칸이 밀리므로 |를 쓴다)
#    읽기에 실패하면 멈춘다. 그냥 가면 모든 앱이 "처음 배포"로 보여 13개가 한꺼번에 바뀐다
DEPLOY_STATE=$(kubectl -n "$NAMESPACE" get deploy -o json | jq -r '.items[] | [
	.metadata.name,
	((.spec.template.spec.containers[] | select(.name == "app") | .image) // ""),
	(.spec.template.metadata.annotations["crewrun.io/config-hash"] // "")
] | join("|")') || fail "지금 떠 있는 앱 정보를 읽지 못했습니다 (k3s API 응답 확인)"
declare -A CUR_TAG=() CUR_HASH=() TARGET=() REASON=() DIFF_CACHE=()
while IFS='|' read -r name image hash; do
	[ -n "$image" ] || continue
	CUR_TAG[$name]="${image##*:}"
	CUR_HASH[$name]="$hash"
done <<<"$DEPLOY_STATE"

#    앱마다 목표 태그를 정한다
#      처음 뜨는 앱, 태그가 커밋 SHA가 아닌 앱, 그 커밋을 모르는 앱 → 새 SHA (무엇이 바뀌었는지 모르면 새로 띄우는 쪽이 안전하다)
#      지금 태그와 배포할 커밋 사이에 소스가 바뀐 앱 → 새 SHA
#      그 밖 → 지금 태그 그대로 (k8s-cd.yml이 안 바뀐 앱에도 SHA 태그를 붙여 두지만, 태그를 바꾸면 Pod가 다시 뜨므로 바꾸지 않는다)
#    교체 대상(ROLL): 이미 떠 있고, 목표 태그나 설정 표시가 지금과 다른 앱
ROLL=()
for app in "${APPS[@]}"; do
	name="bukang-$app"
	cur="${CUR_TAG[$name]-}"
	if [ -z "$cur" ]; then
		TARGET[$app]="$SHA"; REASON[$app]="처음 배포"
		continue
	fi
	if [ "$cur" = "$SHA" ]; then
		TARGET[$app]="$SHA"; REASON[$app]=""
	elif ! [[ "$cur" =~ ^[0-9a-f]{40}$ ]] || ! git cat-file -e "${cur}^{commit}" 2>/dev/null; then
		TARGET[$app]="$SHA"; REASON[$app]="지금 태그($cur)의 커밋을 모름"
	else
		# 같은 태그를 쓰는 앱이 많으므로 태그마다 한 번만 비교한다
		[ -n "${DIFF_CACHE[$cur]+x}" ] || DIFF_CACHE[$cur]="$(bash "$CHANGED_APPS" "$cur" "$SHA" | tr '\n' ' ')"
		if [[ " ${DIFF_CACHE[$cur]} " == *" $app "* ]]; then
			TARGET[$app]="$SHA"; REASON[$app]="소스 바뀜"
		else
			TARGET[$app]="$cur"; REASON[$app]=""
		fi
	fi
	if [ "${CUR_HASH[$name]-}" != "$CONFIG_HASH" ]; then
		REASON[$app]="${REASON[$app]:+${REASON[$app]}, }설정 바뀜"
	fi
	if [ -n "${REASON[$app]}" ]; then
		ROLL+=("$app")
	fi
done

log "교체 계획 (배포 커밋 ${SHA:0:8}, 설정 표시 $CONFIG_HASH)"
for app in "${APPS[@]}"; do
	cur="${CUR_TAG[bukang-$app]-}"
	printf '[deploy]   %-22s %-8s → %-8s %s\n' "$app" "${cur:0:8}" "${TARGET[$app]:0:8}" "${REASON[$app]:-그대로}"
done
log "하나씩 교체할 앱 ${#ROLL[@]}개: ${ROLL[*]:-없음}"

#    임시 kustomization을 만든다 (저장소 파일은 고치지 않는다). render <n>: 교체 대상 중 앞의 n개까지만 목표 값으로 바꾼다
#    아직 차례가 아닌 앱은 지금 태그·지금 설정 표시를 그대로 넣어, 적용해도 그 앱의 Pod는 바뀌지 않는다
#    patches는 적힌 순서로 덮어쓴다 → 모든 Deployment에 새 설정 표시를 붙인 뒤, 기다리는 앱만 지금 값으로 되돌린다
render() {
	local done_count="$1" i app tag
	local -A waiting=()
	for ((i = done_count; i < ${#ROLL[@]}; i++)); do
		waiting[${ROLL[$i]}]=1
	done
	{
		echo "apiVersion: kustomize.config.k8s.io/v1beta1"
		echo "kind: Kustomization"
		echo "resources:"
		echo "  - ../aws"
		echo "images:"
		for app in "${APPS[@]}"; do
			if [ -n "${waiting[$app]-}" ]; then
				tag="${CUR_TAG[bukang-$app]}"
			else
				tag="${TARGET[$app]}"
			fi
			echo "  - name: $REGISTRY/bukang-$app"
			echo "    newTag: \"$tag\""
		done
		echo "patches:"
		echo "  - target:"
		echo "      kind: Deployment"
		echo "    patch: |-"
		echo "      - op: add"
		echo "        path: /spec/template/metadata/annotations"
		echo "        value:"
		echo "          crewrun.io/config-hash: \"$CONFIG_HASH\""
		for app in "${APPS[@]}"; do
			[ -n "${waiting[$app]-}" ] || continue
			echo "  - target:"
			echo "      kind: Deployment"
			echo "      name: bukang-$app"
			echo "    patch: |-"
			echo "      - op: add"
			echo "        path: /spec/template/metadata/annotations"
			echo "        value:"
			echo "          crewrun.io/config-hash: \"${CUR_HASH[bukang-$app]}\""
		done
	} > "$WORK/kustomization.yaml"
}
apply_rendered() {
	kubectl kustomize --load-restrictor LoadRestrictionsNone "$WORK" | "${KUBE_APPLY[@]}"
}

if [ -n "$DRY_RUN" ]; then
	# 계획만 볼 때: 모두 교체한 뒤의 모습과 지금 클러스터의 차이를 찍는다 (diff는 차이가 있으면 1로 끝나므로 실패로 보지 않는다)
	render "${#ROLL[@]}"
	log "계획만 보기: 모두 적용했을 때 바뀌는 내용"
	kubectl kustomize --load-restrictor LoadRestrictionsNone "$WORK" | kubectl diff -f - || true
	log "계획만 보기 끝 (클러스터는 바꾸지 않았습니다)"
	return 0
fi

# 5. 적용
#    5-1. 교체 대상 앱은 그대로 두고 나머지를 먼저 적용한다 (ConfigMap·Secret·Service·nginx·지표 수집기, 처음 뜨는 앱 등)
log "적용 1단계: 교체할 앱을 뺀 나머지"
render 0
apply_rendered

#    5-2. 교체 대상 앱을 하나씩 바꾸고, 새 Pod가 준비될 때까지 기다린 뒤 다음 앱으로 간다
#         Spring 앱은 CPU 2개 서버에서 하나당 2~3분 걸린다. 실패하면 남은 앱은 바꾸지 않고 멈춘다 (옛 Pod가 계속 서비스한다)
for i in "${!ROLL[@]}"; do
	app="${ROLL[$i]}"
	log "적용 $((i + 2))단계: $app (${REASON[$app]}) $((i + 1))/${#ROLL[@]}"
	render $((i + 1))
	apply_rendered
	if ! kubectl -n "$NAMESPACE" rollout status "deploy/bukang-$app" --timeout=600s; then
		kubectl -n "$NAMESPACE" get pods -o wide >&2
		fail "새 Pod로 바뀌지 못해 멈춥니다: $app (남은 앱: ${ROLL[*]:$((i + 1))}) (kubectl -n $NAMESPACE describe/logs 로 원인 확인)"
	fi
done

#    5-3. 1단계에서 바뀐 것(nginx·처음 뜨는 앱 등)까지 모든 Deployment가 준비됐는지 확인한다
FAILED=()
for deploy in $(kubectl -n "$NAMESPACE" get deploy -o name); do
	kubectl -n "$NAMESPACE" rollout status "$deploy" --timeout=600s || FAILED+=("$deploy")
done
if [ "${#FAILED[@]}" -gt 0 ]; then
	kubectl -n "$NAMESPACE" get pods -o wide >&2
	fail "새 Pod로 바뀌지 못했습니다: ${FAILED[*]} (kubectl -n $NAMESPACE describe/logs 로 원인 확인)"
fi
kubectl -n "$NAMESPACE" get pods

# 6. 어떤 컨테이너도 쓰지 않는 이미지를 지운다
#    배포마다 옛 이미지가 남아 디스크가 찼다 (2026-11-19: 이미지 57개, 디스크 83% → 지운 뒤 21개, 67%)
#    되돌리기는 k8s-cd가 GHCR에서 그 커밋 이미지를 다시 받으므로 서버에 옛 이미지를 둘 필요가 없다
#    아직 내려가는 중인 옛 Pod의 이미지는 쓰이는 중이라 남고, 다음 배포 때 지워진다
#    정리에 실패해도 배포는 끝났으므로 경고만 남긴다
log "쓰지 않는 이미지 지우기"
if PRUNED=$(k3s crictl rmi --prune 2>&1); then
	log "지운 이미지 $(grep -c '^Deleted:' <<<"$PRUNED" || true)개, 디스크 $(df -h / | awk 'NR==2 {print $5}') 사용"
else
	log "이미지를 지우지 못했습니다 (배포는 끝남): $PRUNED"
fi
log "완료: ${SHA:0:8}"
}

# 파일을 끝까지 읽은 뒤 실행한다 (위 설명). exit로 끝내 이 뒤에 무엇이 붙어도 읽지 않는다
main "$@"
exit
