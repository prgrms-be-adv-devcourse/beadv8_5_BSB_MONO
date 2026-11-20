# 메인 EC2 (k3s)

앱 12개(gateway, 서비스 10개, web)와 입구 nginx를 k3s로 띄우는 서버입니다. 인프라(MySQL·Redis·Redpanda·ES)는 보조 EC2(`infra/data-node`)에 있습니다.

| 항목 | 값 |
|---|---|
| 주소 | 공인 15.164.150.76 / 사설 10.0.1.145 (퍼블릭 서브넷, 보조 EC2의 NAT 겸용) |
| 사양 | t3.large, 2 vCPU, 7.6GB |
| 도메인 | crewrun.duckdns.org (HTTPS는 Traefik + Let's Encrypt) |
| 저장소 위치 | `/opt/crewrun` |

## 파일

| 파일 | 하는 일 | 언제 |
|---|---|---|
| `install-k3s.sh` | 80·443 비었는지 확인 → NAT 규칙 기록 → jq·aws CLI 설치 → Traefik 설정 두기 → k3s 설치(metrics-server 끔, Secret 암호화) → NAT 규칙 다시 확인 | 전환 때 한 번 |
| `traefik-config.yaml` | Traefik 설정: 접속자 IP 유지(`externalTrafficPolicy: Local`), HTTP→HTTPS, Let's Encrypt 인증서와 보관 | install-k3s.sh가 이메일을 채워 k3s 폴더에 둠 |
| `env-to-parameter-store.sh` | 지금 운영 `.env`의 비밀값을 Parameter Store로 옮김 (기본은 미리보기, `--apply`로 쓰기) | 전환 전 한 번 |
| `deploy.sh <main\|SHA>` | 커밋 맞추기 → Parameter Store에서 Secret 만들기 → GHCR 로그인 Secret → 바뀐 앱만 골라 하나씩 교체 → 새 Pod 확인. `DEPLOY_DRY_RUN=1`이면 계획과 바뀔 내용만 찍음 | 배포마다 (GitHub Actions가 SSM으로 부름) |
| `changed-apps.sh <이전> <새>` | 두 커밋 사이에 이미지를 다시 만들어야 하는 앱 목록을 찍음 (CRU-31). `k8s-cd.yml`(무엇을 빌드할지)과 `deploy.sh`(무엇을 교체할지)가 같은 규칙을 쓰게 한 곳에 둠 | deploy.sh·k8s-cd가 부름 |

## 배포 한 번의 흐름 (`deploy.sh`)

```
① git fetch → main 또는 main에 머지된 커밋인지 확인 → 그 커밋으로 맞춤
② Parameter Store /crewrun/prod/* 읽기 → k8s/overlays/aws/secret.yaml 만들기 (키 목록은 secret.yaml.example)
     꼭 채움 키(MYSQL_USER·MYSQL_PASS·JWT_SECRET·CRYPTO_* 3개)가 비면 멈춤
③ /crewrun/registry/GHCR_AUTH → Secret ghcr-pull (GHCR 이미지가 비공개라 필요)
   /crewrun/prod/MONITORING_RW_PASSWORD → Secret monitoring-remote-write (지표 수집기용, 없으면 경고만)
④ 교체 계획: 앱마다 지금 떠 있는 이미지 태그·설정 표시(config-hash)를 읽음 (읽지 못하면 멈춤)
     지금 태그 ~ 배포 커밋 사이에 소스가 바뀐 앱(changed-apps.sh) → 새 SHA 태그, 나머지 → 지금 태그 그대로
     설정 표시가 달라진 앱(app-config·Secret이 바뀜) → 태그는 위대로, 교체 대상
⑤ 적용 (임시 kustomization → kubectl kustomize | kubectl apply)
     1단계: 교체 대상 앱은 지금 값 그대로 두고 나머지 적용 (ConfigMap·Secret·nginx·지표 수집기·처음 뜨는 앱)
     2단계부터: 교체 대상을 하나씩 적용 → rollout status(최대 10분) → 다음 앱. 실패하면 남은 앱은 그대로 두고 멈춤
     끝: 모든 Deployment rollout status
⑥ 쓰지 않는 이미지 지우기 (k3s crictl rmi --prune, 실패해도 경고만)
⑦ secret.yaml·임시 폴더 지우기 (실패해도)
```

- **하나씩 교체 (CRU-31):** 앱은 RollingUpdate라 새 Pod가 준비된 뒤 옛 Pod가 내려갑니다. 13개를 한꺼번에 바꾸면 13개가 동시에 두 벌이 되어 메모리(7.6GB)가 차고 k3s가 멈췄습니다(2026-11-17). 지금은 한 번에 한 앱만 두 벌이 됩니다.
  - 공통 모듈(`backend/common` 등)이나 설정이 바뀌면 13개를 하나씩 바꾸므로 30~40분 걸립니다. k8s-cd의 서버 명령 제한은 50분입니다.
  - `k8s/base/apps/<앱>.yaml` 자체가 바뀐 앱은 1단계에서 바로 다시 뜹니다. 여러 앱의 이 파일을 한 번에 바꾸면 그 앱들은 동시에 바뀝니다.
- **설정 표시(config-hash):** `app-config.yaml`·Secret 값이 바뀌면 Pod 템플릿의 표시가 바뀌어 Pod가 다시 뜹니다. 같은 커밋을 다시 배포해도 비밀값만 바뀌었으면 반영됩니다.
- **이미지:** GitHub Actions(`k8s-cd.yml`)가 바뀐 앱만 빌드하고, 안 바뀐 앱은 직전 커밋 이미지에 이번 커밋 SHA 태그만 붙입니다. 그래서 배포한 커밋마다 13개 이미지가 다 있고, 예전 커밋으로 되돌리기도 됩니다. 그 커밋의 이미지가 없으면 ImagePullBackOff가 나고, 그 앱은 옛 Pod가 계속 서비스합니다.
  - 서버에는 지금 쓰는 이미지만 둡니다. 배포가 끝나면 어떤 컨테이너도 쓰지 않는 이미지를 지웁니다(⑥). 되돌릴 때는 GHCR에서 다시 받습니다.
    이 정리가 없을 때는 배포마다 옛 이미지가 쌓여 이미지 57개, 디스크 83%까지 찼습니다(2026-11-19, 지운 뒤 21개·67%).
- **계획만 보기:** `sudo DEPLOY_DRY_RUN=1 bash deploy.sh <SHA>` → 교체 계획과 `kubectl diff`만 찍고 클러스터는 바꾸지 않습니다. 저장소 checkout은 하므로, 운영 저장소를 건드리지 않으려면 복사본을 만들어 `APP_DIR=<복사본>`으로 줍니다.

## 비밀값 (SSM Parameter Store)

| 경로 | 내용 | 넣는 방법 |
|---|---|---|
| `/crewrun/prod/<키>` | 앱 비밀값 26개 (`k8s/overlays/aws/secret.yaml.example`의 목록. 11/18 메일·수신 거부 3개 추가) | `env-to-parameter-store.sh --apply` |
| `/crewrun/registry/GHCR_AUTH` | GHCR 로그인 (`아이디:토큰`을 base64로 바꾼 값) | 같은 스크립트가 서버 docker 로그인에서 옮김 |
| `/crewrun/prod/MONITORING_RW_PASSWORD` | 지표 수집기가 개인 AWS EC2 Prometheus로 보낼 때 쓰는 비밀번호 (BUK-258). 앱 Secret에는 넣지 않고 Secret `monitoring-remote-write`로만 만듦 | 아래 「지표 수집기」 |

- 모두 SecureString(KMS로 암호화)입니다. 값은 서버 안에서 옮기고 PC·채팅으로 가져오지 않습니다.
- Parameter Store는 빈 값을 저장할 수 없습니다. 운영 `.env`에서 빈 키(예: `ELASTICSEARCH_USERNAME`)는 건너뛰고, deploy.sh가 빈 값으로 넣습니다.
- 메인 EC2 역할(`team05-bukang-main-ec2-role`)에는 **읽기만** 줍니다(`infra/iam/main-ec2-policy.json`). 옮길 때만 임시 쓰기 정책을 붙였다가 지웁니다(`infra/README.md` D6 순서 5).
- 공개해도 되는 운영 값(버킷 이름, 사이트 주소, 소셜 로그인 앱 ID, 포트원 상점 ID)은 `k8s/overlays/aws/app-config.yaml`에 커밋되어 있습니다.

## 지표 수집기 (BUK-258)

`k8s/overlays/aws/monitoring/`의 수집기(`metrics-agent`, Prometheus 에이전트 모드)가 15초마다 Spring 앱·보조 EC2 exporter·서버·Pod 지표를 모아
개인 AWS EC2의 Prometheus(`https://prom.bukang-devops.duckdns.org`, `infra/monitoring-node`)로 보냅니다. 이 서버에는 저장하지 않습니다.

비밀번호 넣기 (처음 한 번). 값은 개인 EC2의 `/opt/monitoring-node/secrets/prom-password`입니다.
쓰기는 앞의 비밀값 옮기기처럼 메인 EC2 역할에 임시 쓰기 정책을 붙이고 메인 EC2에서 넣은 뒤 정책을 지웁니다.

```bash
# 메인 EC2 (임시 쓰기 정책을 붙인 동안). read -s 라 입력한 값이 화면·기록에 남지 않음
read -rs -p "개인 EC2 prom-password 값: " PW; echo
aws ssm put-parameter --name /crewrun/prod/MONITORING_RW_PASSWORD --type SecureString --value "$PW" --overwrite >/dev/null
unset PW
```

다음 배포 때 deploy.sh가 Secret을 만듭니다. 이미 떠 있는 수집기는 보낼 때마다 비밀번호 파일을 다시 읽어서, 보통 1~2분 안에 새 값이 반영됩니다.
반영되지 않으면 `sudo k3s kubectl -n crewrun rollout restart deploy/metrics-agent`로 다시 띄웁니다.

```bash
sudo k3s kubectl -n crewrun get pods -l app.kubernetes.io/name=metrics-agent        # Running, READY 1/1
sudo k3s kubectl -n crewrun logs deploy/metrics-agent | grep -iE "error|401" | tail  # 비어 있으면 정상
sudo k3s kubectl -n crewrun scale deploy/metrics-agent --replicas=0                  # 문제가 생기면 수집기만 끔 (앱 영향 없음)
```

## HTTPS

- Traefik이 443을 받고 Let's Encrypt 인증서를 받아 갱신합니다. certbot과 nginx의 HTTPS 템플릿은 쓰지 않습니다.
- 인증서는 Traefik 볼륨(`/data/acme.json`)에 남아, Traefik이 다시 떠도 새로 받지 않습니다.
- Let's Encrypt는 같은 도메인 발급을 주 5회로 제한합니다. 인증서가 거듭 실패할 때는 `ACME_STAGING=1`(시험 서버)로 원인을 찾습니다.
  시험 인증서에서 실제로 바꿀 때는 `ACME_STAGING` 없이 install-k3s.sh를 다시 실행하고, `/data/acme.json`을 지운 뒤 Traefik을 다시 띄웁니다(명령은 `infra/README.md` 6단계).
- Traefik이 HTTPS를 풀고 nginx에는 HTTP로 넘깁니다. 앱은 `X-Forwarded-Proto`를 읽지 않아 nginx 설정은 그대로 둡니다(로그인 쿠키는 항상 Secure, 소셜 로그인 콜백 주소는 `OAUTH2_REDIRECT_BASE_URL`).

## 주의

- **80·443:** install-k3s.sh는 compose의 nginx가 떠 있으면 멈춥니다. 전환 절차서 순서대로 compose를 먼저 내립니다.
- **NAT:** 이 서버는 보조 EC2가 인터넷에 나가는 NAT입니다. k3s가 방화벽 규칙을 더해도 `10.0.0.0/16 MASQUERADE`가 남는지 설치 전후로 확인합니다.
- **GHCR 토큰:** 지금 서버의 docker 로그인 토큰이 누구 것인지, 언제 만료되는지 전환 전에 확인합니다. 만료되면 이미지를 받지 못해 배포가 멈춥니다.
- **2026-11-15 전환을 마쳤습니다**(BUK-220 D7). 다시 설치하거나 되돌릴 때는 `infra/README.md`의 절차서를 따릅니다.
- `install-k3s.sh`는 다시 실행해도 됩니다. k3s가 있으면 설치를 건너뛰고 Traefik 설정만 다시 둡니다(설정을 바꿨을 때 이렇게 반영).
