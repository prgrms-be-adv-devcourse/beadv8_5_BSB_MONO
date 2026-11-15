#!/usr/bin/env bash
# 지금 운영 compose의 .env에서 비밀값을 SSM Parameter Store로 옮긴다. 전환 때 한 번, 메인 EC2에서 실행한다 (BUK-220 D6)
#   값이 서버 밖(PC, 채팅)으로 나가지 않게 서버 안에서 바로 옮긴다
#   옮기는 키는 k8s/overlays/aws/secret.yaml.example의 목록이다 (공개 설정은 app-config.yaml에 이미 있다)
#   → /crewrun/prod/<키> (SecureString, KMS 기본 키로 암호화)
#   GHCR 로그인 정보도 옮긴다: 서버 docker 로그인(~/.docker/config.json)의 ghcr.io → /crewrun/registry/GHCR_AUTH
#
# 기본은 미리보기다. 키 이름과 값 길이만 찍고 아무것도 쓰지 않는다. --apply를 붙여야 실제로 쓴다
#   Parameter Store에 쓰는 권한이 필요하다. 메인 EC2 역할은 읽기만 되므로, 이 셸에서만 쓸 사용자 자격 증명으로 실행한다
#   (aws configure 대신 환경변수 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY로 넘기고, 끝나면 셸을 닫는다)
#
# 사용 (메인 EC2, 저장소 루트 /opt/crewrun)
#   bash infra/main-node/env-to-parameter-store.sh            # 미리보기
#   bash infra/main-node/env-to-parameter-store.sh --apply    # 쓰기 (이미 있는 값은 덮어쓴다)
set -euo pipefail

ENV_FILE="${ENV_FILE:-/home/ubuntu/workspace/.env}"
DOCKER_CONFIG_FILE="${DOCKER_CONFIG_FILE:-/home/ubuntu/.docker/config.json}"
PARAM_PATH=/crewrun/prod
REGISTRY_PARAM=/crewrun/registry/GHCR_AUTH
APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

cd "$(dirname "$0")/../.."
[ -f "$ENV_FILE" ] || { echo "[env→ssm] .env가 없습니다: $ENV_FILE" >&2; exit 1; }
for tool in jq aws; do
	command -v "$tool" >/dev/null 2>&1 \
		|| { echo "[env→ssm] $tool 이 없습니다. 설치: sudo apt-get install -y jq / sudo snap install aws-cli --classic" >&2; exit 1; }
done

log() { echo "[env→ssm] $*"; }

# .env에서 키 하나의 값을 읽는다. 같은 키가 여러 번이면 마지막 줄(compose와 같음). 값 앞뒤의 따옴표 한 쌍은 뗀다
env_value() {
	local line value
	line=$(grep -E "^$1=" "$ENV_FILE" | tail -1) || return 1
	value="${line#*=}"
	value="${value%$'\r'}"
	if [[ "$value" =~ ^\"(.*)\"$ ]] || [[ "$value" =~ ^\'(.*)\'$ ]]; then
		value="${BASH_REMATCH[1]}"
	fi
	printf '%s' "$value"
}

# 값 하나를 쓴다. 값은 명령 기록에 남지 않게 파일(file://)로 넘긴다. 4KB를 넘으면 Advanced 등급이 필요하다
put_secure() {
	local name="$1" value="$2" tier=Standard tmp
	[ "${#value}" -gt 4096 ] && tier=Advanced
	if [ "$APPLY" = "1" ]; then
		tmp=$(mktemp)
		printf '%s' "$value" > "$tmp"
		aws ssm put-parameter --name "$name" --type SecureString --tier "$tier" --overwrite \
			--value "file://$tmp" >/dev/null
		rm -f "$tmp"
		log "썼음  $name (${#value}자, $tier)"
	else
		log "미리보기 $name (${#value}자, $tier)"
	fi
}

mapfile -t KEYS < <(grep -oE '^  [A-Z0-9_]+:' k8s/overlays/aws/secret.yaml.example | tr -d ' :')
log "대상 키 ${#KEYS[@]}개, 원본 $ENV_FILE"
SKIPPED=()
for key in "${KEYS[@]}"; do
	value=$(env_value "$key") || value=""
	# Parameter Store는 빈 값을 저장할 수 없다. 빈 키는 deploy.sh가 빈 값으로 넣는다
	if [ -z "$value" ]; then
		SKIPPED+=("$key")
		continue
	fi
	put_secure "$PARAM_PATH/$key" "$value"
done
[ "${#SKIPPED[@]}" -gt 0 ] && log "비어 있거나 없어서 건너뜀: ${SKIPPED[*]}"

# GHCR 로그인 정보
if [ -f "$DOCKER_CONFIG_FILE" ] && GHCR_AUTH=$(jq -er '.auths["ghcr.io"].auth' "$DOCKER_CONFIG_FILE" 2>/dev/null); then
	put_secure "$REGISTRY_PARAM" "$GHCR_AUTH"
else
	log "주의: $DOCKER_CONFIG_FILE 에 ghcr.io 로그인이 없습니다. read:packages 권한 토큰으로 직접 넣으세요:"
	log "  printf '%s' '<GitHub 아이디>:<토큰>' | base64 -w0  → $REGISTRY_PARAM (SecureString)"
fi

[ "$APPLY" = "1" ] || log "미리보기였습니다. 쓰려면 --apply 를 붙여 다시 실행하세요"
