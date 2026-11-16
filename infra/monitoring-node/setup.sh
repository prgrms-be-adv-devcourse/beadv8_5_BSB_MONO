#!/usr/bin/env bash
# 모니터링 서버(개인 AWS EC2, Ubuntu)를 처음 한 번 준비한다 (BUK-259). 이 폴더에서 root로 실행한다
#   1. swap 2GB를 만든다 (t3.small 메모리 2GB에 Prometheus·Grafana·Caddy를 함께 띄우므로)
#   2. Docker Engine과 compose 플러그인을 Docker 공식 apt 저장소에서 설치한다
#   3. .env를 만들고 Grafana 관리자 비밀번호를 정한다
#   4. Prometheus 비밀번호를 정하고, Caddy가 읽을 해시 파일을 만든다
#   5. 대시보드 JSON을 받는다 (fetch-dashboards.sh)
# 이미 된 단계는 건너뛰므로 여러 번 실행해도 된다. 서비스를 띄우는 일은 docker compose up -d로 한다
#
# 사용 (모니터링 서버에서)
#   sudo cp -r /opt/crewrun/infra/monitoring-node /opt/monitoring-node   # 비밀값이 저장소 폴더에 섞이지 않게 복사해서 쓴다
#   cd /opt/monitoring-node && sudo bash setup.sh
set -euo pipefail

cd "$(dirname "$0")"
SWAP_FILE=/swapfile
SWAP_SIZE=2G
DOCKER_USER="${DOCKER_USER:-ubuntu}"
# 수집기·k6가 Prometheus에 지표를 보낼 때 쓰는 계정 이름
PROM_USER="${PROM_USER:-crewrun}"

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
	grep -q "^$SWAP_FILE " /etc/fstab || echo "$SWAP_FILE none swap sw 0 0" >>/etc/fstab
	# 메모리가 남아 있을 때는 swap을 덜 쓰게 한다 (기본 60)
	sysctl -w vm.swappiness=10
	echo "vm.swappiness=10" >/etc/sysctl.d/99-monitoring-swap.conf
fi

# 2. Docker (https://docs.docker.com/engine/install/ubuntu/ 의 apt 저장소 방식)
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
	log "Docker 이미 있음 ($(docker --version))"
else
	log "Docker 설치"
	apt-get update
	apt-get install -y ca-certificates curl
	install -m 0755 -d /etc/apt/keyrings
	curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
	chmod a+r /etc/apt/keyrings/docker.asc
	# shellcheck disable=SC1091
	. /etc/os-release
	echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
		>/etc/apt/sources.list.d/docker.list
	apt-get update
	apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
	# 컨테이너 로그가 끝없이 쌓이지 않게 컨테이너마다 10MB × 3개까지만 남긴다 (디스크 20GB)
	cat >/etc/docker/daemon.json <<'EOF'
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
EOF
	systemctl enable --now docker
	systemctl restart docker
fi
if id "$DOCKER_USER" >/dev/null 2>&1; then
	usermod -aG docker "$DOCKER_USER"
fi

# 3. .env (도메인, Grafana 관리자 비밀번호)
if [ -f .env ]; then
	log ".env 이미 있음"
else
	cp .env.example .env
	# 영문·숫자만 쓰는 값으로 만든다 (compose가 $ 같은 기호를 변수로 읽지 않게)
	sed -i "s/^GRAFANA_ADMIN_PASSWORD=.*/GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 16)/" .env
	chmod 600 .env
	log ".env를 만들었습니다. DOMAIN이 맞는지 확인하세요 (Grafana admin 비밀번호도 이 파일에 있음)"
fi

# 4. Prometheus 비밀번호
# 평문은 secrets/prom-password에 두고(수집기 설정·k6에 옮겨 적을 때 읽음), Caddy에는 해시만 넘긴다
mkdir -p secrets
chmod 700 secrets
if [ -f secrets/prom-auth.caddy ]; then
	log "Prometheus 비밀번호 이미 있음 (secrets/)"
else
	PROM_PASSWORD="$(openssl rand -hex 24)"
	CADDY_IMAGE="$(grep -m1 -oE 'caddy:[0-9][^[:space:]]*' compose.yml)"
	# 명령 인자에 평문이 보이지 않게 표준 입력으로 넘긴다
	HASH="$(printf '%s\n' "$PROM_PASSWORD" | docker run --rm -i "$CADDY_IMAGE" caddy hash-password)"
	printf '%s\n' "$PROM_PASSWORD" >secrets/prom-password
	cat >secrets/prom-auth.caddy <<EOF
basic_auth {
	${PROM_USER} ${HASH}
}
EOF
	chmod 600 secrets/prom-password
	# Caddy 컨테이너가 읽을 수 있어야 한다 (해시라 평문이 아님)
	chmod 644 secrets/prom-auth.caddy
	log "Prometheus 계정 ${PROM_USER}, 비밀번호는 secrets/prom-password"
fi

# 5. 대시보드
bash fetch-dashboards.sh

log "완료. 다음: docker compose up -d"
