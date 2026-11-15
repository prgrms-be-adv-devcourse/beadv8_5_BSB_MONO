#!/usr/bin/env bash
# 보조 EC2(데이터 서버)를 처음 한 번 준비한다. 서버에서 root로 실행한다 (sudo bash setup.sh)
#   1. swap 2GB를 만든다 (메모리 3.7GB에 ES·MySQL·Redpanda를 함께 띄우므로 갑자기 메모리가 모자랄 때 버티게)
#   2. Docker Engine과 compose 플러그인을 Docker 공식 apt 저장소에서 설치한다
#   3. 저장소를 /opt/crewrun에 받는다 (공개 저장소라 인증이 필요 없다)
#   4. infra/data-node/.env를 예시에서 만든다 (값은 사람이 채운다)
# 이미 된 단계는 건너뛰므로 여러 번 실행해도 된다. 서비스를 띄우는 일은 deploy.sh가 한다
#
# 사용 (보조 EC2에서)
#   curl -fsSLO https://raw.githubusercontent.com/prgrms-be-adv-devcourse/beadv8_5_BSB_MONO/main/infra/data-node/setup.sh
#   sudo bash setup.sh
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/prgrms-be-adv-devcourse/beadv8_5_BSB_MONO.git}"
APP_DIR="${APP_DIR:-/opt/crewrun}"
SWAP_FILE=/swapfile
SWAP_SIZE=2G
# Docker를 sudo 없이 쓸 계정 (SSM으로 명령을 보내면 root로 돌지만, 사람이 접속할 때 편하게)
DOCKER_USER="${DOCKER_USER:-ubuntu}"

log() { echo "[setup] $*"; }

if [ "$(id -u)" -ne 0 ]; then
	echo "[setup] root로 실행하세요: sudo bash $0" >&2
	exit 1
fi

# 1. swap
if swapon --show=NAME --noheadings | grep -qx "$SWAP_FILE"; then
	log "swap 이미 있음 ($SWAP_FILE)"
else
	log "swap $SWAP_SIZE 만들기"
	fallocate -l "$SWAP_SIZE" "$SWAP_FILE"
	chmod 600 "$SWAP_FILE"
	mkswap "$SWAP_FILE"
	swapon "$SWAP_FILE"
	grep -q "^$SWAP_FILE " /etc/fstab || echo "$SWAP_FILE none swap sw 0 0" >> /etc/fstab
	# 메모리가 남아 있을 때는 swap을 덜 쓰게 한다 (기본 60)
	sysctl -w vm.swappiness=10
	echo "vm.swappiness=10" > /etc/sysctl.d/99-crewrun-swap.conf
fi

# Elasticsearch가 요구하는 커널 설정 (없으면 ES가 시작하다 멈출 수 있다)
sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" > /etc/sysctl.d/99-crewrun-es.conf

# 2. Docker (https://docs.docker.com/engine/install/ubuntu/ 의 apt 저장소 방식)
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
	log "Docker 이미 있음 ($(docker --version))"
else
	log "Docker 설치"
	apt-get update
	apt-get install -y ca-certificates curl git
	install -m 0755 -d /etc/apt/keyrings
	curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
	chmod a+r /etc/apt/keyrings/docker.asc
	# shellcheck disable=SC1091
	. /etc/os-release
	echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
		> /etc/apt/sources.list.d/docker.list
	apt-get update
	apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
	systemctl enable --now docker
fi
if id "$DOCKER_USER" >/dev/null 2>&1; then
	usermod -aG docker "$DOCKER_USER"
fi

# 3. 저장소
if [ -d "$APP_DIR/.git" ]; then
	log "저장소 이미 있음 ($APP_DIR)"
else
	log "저장소 받기 → $APP_DIR"
	command -v git >/dev/null 2>&1 || apt-get install -y git
	git clone "$REPO_URL" "$APP_DIR"
fi

# 4. .env
ENV_FILE="$APP_DIR/infra/data-node/.env"
if [ -f "$ENV_FILE" ]; then
	log ".env 이미 있음"
else
	cp "$APP_DIR/infra/data-node/.env.example" "$ENV_FILE"
	chmod 600 "$ENV_FILE"
	log ".env를 만들었습니다. 값을 채운 뒤 deploy.sh를 실행하세요: $ENV_FILE"
fi

log "완료. 다음: sudo bash $APP_DIR/infra/data-node/deploy.sh <커밋 SHA 또는 브랜치>"
