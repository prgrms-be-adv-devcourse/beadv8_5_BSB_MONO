#!/usr/bin/env bash
# 메인 EC2에 k3s를 설치한다. 전환 때 한 번, root로 실행한다 (BUK-220 D7)
#   1. 80·443이 비어 있는지 확인한다 (compose의 nginx를 먼저 내려야 한다)
#   2. 보조 EC2가 인터넷에 나가는 NAT 규칙을 기록해 둔다 (k3s가 방화벽 규칙을 더하면서 지워지지 않았는지 뒤에서 확인)
#   3. 배포에 필요한 도구를 설치한다 (aws CLI, jq)
#   4. Traefik 설정(HTTPS·접속자 IP)을 k3s가 읽는 폴더에 둔다
#   5. k3s를 설치한다: metrics-server 끄기(메모리 절약), Secret 암호화 켜기, 로컬 k3d와 같은 버전
#   6. 노드·Traefik이 뜰 때까지 기다리고 NAT 규칙을 다시 확인한다
#
# 사용 (메인 EC2에서, 저장소 루트 /opt/crewrun)
#   sudo ACME_EMAIL=<인증서 알림 이메일> bash infra/main-node/install-k3s.sh
#   처음에는 Let's Encrypt 시험 서버로 확인하려면 ACME_STAGING=1 을 붙인다 (발급 횟수 제한을 피한다)
set -euo pipefail

K3S_VERSION="${K3S_VERSION:-v1.35.5+k3s1}"
ACME_EMAIL="${ACME_EMAIL:?ACME_EMAIL=<이메일>을 붙여 실행하세요 (Let\'s Encrypt 계정)}"
if [ "${ACME_STAGING:-0}" = "1" ]; then
	ACME_CA_SERVER=https://acme-staging-v02.api.letsencrypt.org/directory
else
	ACME_CA_SERVER=https://acme-v02.api.letsencrypt.org/directory
fi
# 보조 EC2가 있는 VPC 대역. 메인 EC2가 NAT 역할을 해서 이 대역의 나가는 요청을 자기 IP로 바꿔 내보낸다
VPC_CIDR="${VPC_CIDR:-10.0.0.0/16}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MANIFEST_DIR=/var/lib/rancher/k3s/server/manifests

log() { echo "[install-k3s] $*"; }

if [ "$(id -u)" -ne 0 ]; then
	echo "[install-k3s] root로 실행하세요" >&2
	exit 1
fi

# 1. 80·443 확인. Traefik이 이 포트를 받아야 한다
if ss -ltnH '( sport = :80 or sport = :443 )' | grep -q .; then
	echo "[install-k3s] 80 또는 443을 쓰는 프로그램이 있습니다. compose의 nginx를 먼저 내리세요:" >&2
	ss -ltnp '( sport = :80 or sport = :443 )' >&2
	exit 1
fi

# 2. NAT 규칙 기록
nat_rule_present() { iptables -t nat -S POSTROUTING | grep -q -- "-s ${VPC_CIDR}.*MASQUERADE"; }
if nat_rule_present; then
	NAT_BEFORE=1
	log "NAT 규칙 있음 (${VPC_CIDR} MASQUERADE)"
else
	NAT_BEFORE=0
	log "주의: 설치 전에도 ${VPC_CIDR} NAT 규칙이 보이지 않습니다. 보조 EC2의 인터넷 연결을 따로 확인하세요"
fi

# 3. 도구
command -v jq >/dev/null 2>&1 && command -v envsubst >/dev/null 2>&1 \
	|| { log "jq·envsubst 설치"; apt-get update -q; apt-get install -y -q jq gettext-base; }
command -v aws >/dev/null 2>&1 || { log "aws CLI 설치"; snap install aws-cli --classic; }

# 4. Traefik 설정. 이메일·인증 서버만 채우고 나머지 $ 표시는 그대로 둔다
mkdir -p "$MANIFEST_DIR"
ACME_EMAIL="$ACME_EMAIL" ACME_CA_SERVER="$ACME_CA_SERVER" \
	envsubst '${ACME_EMAIL} ${ACME_CA_SERVER}' < "$SCRIPT_DIR/traefik-config.yaml" > "$MANIFEST_DIR/traefik-config.yaml"
chmod 600 "$MANIFEST_DIR/traefik-config.yaml"
log "Traefik 설정 둠 (인증 서버: $ACME_CA_SERVER)"

# 5. k3s 설치 (https://docs.k3s.io/quick-start 의 설치 스크립트, 버전 고정)
if command -v k3s >/dev/null 2>&1; then
	log "k3s 이미 있음 ($(k3s --version | head -1)). 설치는 건너뛴다"
else
	log "k3s $K3S_VERSION 설치"
	curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="$K3S_VERSION" sh -s - server \
		--disable metrics-server \
		--secrets-encryption \
		--write-kubeconfig-mode 600
fi

# 6. 확인
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
log "노드 준비 기다리기"
kubectl wait --for=condition=Ready node --all --timeout=300s
log "Traefik 기다리기"
for _ in $(seq 1 60); do
	kubectl -n kube-system get deploy traefik >/dev/null 2>&1 && break
	sleep 5
done
kubectl -n kube-system rollout status deploy/traefik --timeout=300s
k3s secrets-encrypt status | head -3

if [ "$NAT_BEFORE" = "1" ] && ! nat_rule_present; then
	log "NAT 규칙이 사라져 다시 넣는다 (${VPC_CIDR} MASQUERADE)"
	iptables -t nat -A POSTROUTING -s "$VPC_CIDR" -j MASQUERADE
	log "주의: 재부팅 뒤에도 남는지 확인하세요 (기존 NAT 설정 방식으로 저장)"
fi
log "완료. 다음: sudo bash infra/main-node/deploy.sh main"
