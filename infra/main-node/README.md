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
| `deploy.sh <main\|SHA>` | 커밋 맞추기 → Parameter Store에서 Secret 만들기 → GHCR 로그인 Secret → 이미지 태그를 SHA로 바꿔 적용 → 새 Pod 확인 | 배포마다 (GitHub Actions가 SSM으로 부름) |

## 배포 한 번의 흐름 (`deploy.sh`)

```
① git fetch → main 또는 main에 머지된 커밋인지 확인 → 그 커밋으로 맞춤
② Parameter Store /crewrun/prod/* 읽기 → k8s/overlays/aws/secret.yaml 만들기 (키 목록은 secret.yaml.example)
     꼭 채움 키(MYSQL_USER·MYSQL_PASS·JWT_SECRET·CRYPTO_* 3개)가 비면 멈춤
③ /crewrun/registry/GHCR_AUTH → Secret ghcr-pull (GHCR 이미지가 비공개라 필요)
④ 임시 kustomization: overlays/aws + 이미지 태그 = 커밋 SHA + 설정 표시(config-hash)
     → kubectl kustomize | kubectl apply
⑤ Deployment마다 rollout status (최대 10분). 실패하면 Pod 상태를 찍고 실패로 끝남
⑥ secret.yaml·임시 폴더 지우기 (실패해도)
```

- **설정 표시(config-hash):** `app-config.yaml`·Secret 값이 바뀌면 Pod 템플릿의 표시가 바뀌어 Pod가 다시 뜹니다. 같은 커밋을 다시 배포해도 비밀값만 바뀌었으면 반영됩니다.
- **이미지:** GitHub Actions가 커밋 SHA 태그로 GHCR에 먼저 올립니다(`app-cd.yml`의 build 잡과 같음). 그 커밋의 이미지가 없으면 ImagePullBackOff가 납니다.

## 비밀값 (SSM Parameter Store)

| 경로 | 내용 | 넣는 방법 |
|---|---|---|
| `/crewrun/prod/<키>` | 앱 비밀값 22개 (`k8s/overlays/aws/secret.yaml.example`의 목록) | `env-to-parameter-store.sh --apply` |
| `/crewrun/registry/GHCR_AUTH` | GHCR 로그인 (`아이디:토큰`을 base64로 바꾼 값) | 같은 스크립트가 서버 docker 로그인에서 옮김 |

- 모두 SecureString(KMS로 암호화)입니다. 값은 서버 안에서 옮기고 PC·채팅으로 가져오지 않습니다.
- Parameter Store는 빈 값을 저장할 수 없습니다. 운영 `.env`에서 빈 키(예: `ELASTICSEARCH_USERNAME`)는 건너뛰고, deploy.sh가 빈 값으로 넣습니다.
- 메인 EC2 역할에는 **읽기만** 줍니다(BUK-220 D5 `infra/iam`). 옮기는 스크립트는 그 셸에서만 쓰는 사용자 자격 증명으로 한 번 실행합니다.
- 공개해도 되는 운영 값(버킷 이름, 사이트 주소, 소셜 로그인 앱 ID, 포트원 상점 ID)은 `k8s/overlays/aws/app-config.yaml`에 커밋되어 있습니다.

## HTTPS

- Traefik이 443을 받고 Let's Encrypt 인증서를 받아 갱신합니다. certbot과 nginx의 HTTPS 템플릿은 쓰지 않습니다.
- 인증서는 Traefik 볼륨(`/data/acme.json`)에 남아, Traefik이 다시 떠도 새로 받지 않습니다.
- 처음 설치할 때는 `ACME_STAGING=1`로 시험 인증서를 받아 흐름을 확인한 뒤, 빼고 다시 설치합니다. Let's Encrypt는 같은 도메인 발급을 주 5회로 제한합니다.
- Traefik이 HTTPS를 풀고 nginx에는 HTTP로 넘깁니다. 앱은 `X-Forwarded-Proto`를 읽지 않아 nginx 설정은 그대로 둡니다(로그인 쿠키는 항상 Secure, 소셜 로그인 콜백 주소는 `OAUTH2_REDIRECT_BASE_URL`).

## 주의

- **80·443:** install-k3s.sh는 compose의 nginx가 떠 있으면 멈춥니다. 전환 절차서 순서대로 compose를 먼저 내립니다.
- **NAT:** 이 서버는 보조 EC2가 인터넷에 나가는 NAT입니다. k3s가 방화벽 규칙을 더해도 `10.0.0.0/16 MASQUERADE`가 남는지 설치 전후로 확인합니다.
- **GHCR 토큰:** 지금 서버의 docker 로그인 토큰이 누구 것인지, 언제 만료되는지 전환 전에 확인합니다. 만료되면 이미지를 받지 못해 배포가 멈춥니다.
- **스크립트는 서버에 아직 적용하지 않았습니다.** 전환(BUK-220 D7)은 `infra/README.md`의 절차서를 따릅니다(D5에서 작성).
