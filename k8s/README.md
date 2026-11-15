# k8s 배포 설정

크루런 앱(gateway, 서비스 10개, web, 입구 nginx)을 k3s에 띄우는 매니페스트입니다. 교안 「멀티모듈 MSA + K8S 배포(로컬)」(slog 14709) 순서를 따릅니다.
인프라(MySQL·Redis·Kafka·Elasticsearch·S3Mock)는 k8s에 넣지 않고 compose로 띄웁니다. 로컬은 내 PC, 운영은 보조 EC2입니다.

## 요청 흐름

```
브라우저 → Traefik(k3s 기본 Ingress, 운영은 HTTPS)
         → bukang-nginx (backend/docker/nginx/routes.conf 그대로: 로그인 요청 제한, /internal 막기, 접속자 IP 전달)
             ├ /api → bukang-gateway (토큰 검사, 서비스별 라우팅) → bukang-<서비스>
             └ 나머지 → bukang-web (Next.js)
앱 → bukang-db / bukang-redis / bukang-kafka / bukang-elasticsearch / bukang-s3mock
     (overlay의 Service가 로컬은 내 PC, 운영은 보조 EC2로 이어 준다)
```

Service 이름은 compose의 컨테이너 이름(`bukang-*`)과 같습니다. 그래서 `application-prod.yaml`, nginx `upstreams.conf`를 고치지 않습니다.

## 폴더

| 경로 | 내용 |
|---|---|
| `base/kustomization.yaml` | 공통 목록. nginx 설정 파일로 ConfigMap `nginx-config`를 만들고 패치를 붙인다 |
| `base/app-config.yaml` | ConfigMap `app-config`: 비밀이 아닌 공통 설정 (인프라 주소, 프로파일, S3) |
| `base/apps/*.yaml` | 앱마다 Deployment + Service. 이름·이미지·포트만 있다 |
| `base/patches/spring-common.yaml` | Spring 앱 11개 공통: 설정 주입, JVM 옵션, 메모리, 상태 확인 |
| `base/patches/spring-memory.yaml` | 메모리를 더 쓰는 서비스(order, product, member, payout)의 상한 |
| `base/edge/` | 입구 nginx(Deployment·Service·k8s용 default.conf)와 Ingress |
| `overlays/local/` | k3d용: 인프라 이름을 내 PC로 잇는 Service, Secret(`secret.yaml`, git 제외), Kafka 포트를 여는 compose 덮어쓰기 |
| `overlays/aws/` | 운영용 (BUK-220에서 만든다) |
| `scripts/local-images.sh` | 이미지를 빌드해 k3d에 직접 넣는다 |
| `scripts/local-apply.sh` | base + overlays/local을 합쳐 k3d에 적용한다 (`--wait`이면 다 뜰 때까지 기다림) |

## 스크립트 (`k8s/scripts`)

로컬 k3d에 띄우는 일은 두 단계다. 이미지를 준비하고(①), 그 이미지로 k8s 객체를 만든다(②).
교안 14709의 16강(`push-images.sh`)이 ①, 18강(`kubectl apply`)이 ②에 해당한다.

```
① local-images.sh : 코드 → docker compose build → 이미지 → k3d image import → 클러스터 안 이미지
② local-apply.sh  : base + overlays/local → kustomize로 합침 → kubectl apply → Deployment 등 → Pod
```

둘 다 저장소 루트(MONO)에서 실행한다. 스크립트 안에서 저장소 루트로 옮겨 가므로 다른 폴더에서 실행해도 된다.

### `local-images.sh`: 이미지를 만들어 k3d에 넣기

```bash
k8s/scripts/local-images.sh                       # 앱 12개 모두 (10분 넘게 걸린다)
k8s/scripts/local-images.sh member-service web    # 이름을 준 것만
```

| 순서 | 하는 일 |
|---|---|
| 1 | 인자가 없으면 앱 12개, 있으면 그 이름들을 대상으로 정한다. 이름은 `backend/compose.yml`의 서비스 이름이다 |
| 2 | `docker compose -f backend/compose.yml --profile app build <대상>`으로 이미지를 만든다. Dockerfile·빌드 인자가 compose와 같다 (web 빌드 인자는 `backend/.env`에서 읽는다) |
| 3 | 만든 이미지 `bukang-<서비스>:latest`를 `k3d image import -c crewrun`으로 클러스터 안에 복사한다 |

- k3d 클러스터는 내 PC Docker의 이미지를 직접 보지 못해서 3번 복사가 필요하다. 교안은 이 자리에서 Docker Hub에 push한다.
- **Pod는 띄우지 않는다.** 이미 떠 있는 Pod를 새 이미지로 바꾸려면 `kubectl rollout restart -n crewrun deploy/bukang-<서비스>`를 한다.
  태그가 `latest`로 같아서 `local-apply.sh`만으로는 바뀌지 않는다.
- 다른 클러스터에 넣으려면 `CLUSTER=<이름> k8s/scripts/local-images.sh`.
- 명령이 하나라도 실패하면 바로 멈춘다(`set -euo pipefail`).

### `local-apply.sh`: k8s 설정을 한 번에 적용하기

```bash
k8s/scripts/local-apply.sh          # 적용만
k8s/scripts/local-apply.sh --wait   # 적용한 뒤 Pod가 모두 뜰 때까지 기다린다 (최대 10분)
```

| 순서 | 하는 일 |
|---|---|
| 1 | `k8s/overlays/local/secret.yaml`이 없으면 안내하고 멈춘다 (비밀값 없이 적용해 앱이 실패하지 않게) |
| 2 | `kubectl kustomize --load-restrictor LoadRestrictionsNone k8s/overlays/local`로 base와 local overlay를 합친다 (객체 36개) |
| 3 | 합친 결과를 파이프로 `kubectl apply -f -`에 넘겨 클러스터에 보낸다. 바뀐 객체만 바뀌고 같은 것은 그대로 둔다 |
| 4 | `--wait`이면 `kubectl rollout status -n crewrun deploy --timeout=600s`로 다 뜰 때까지 기다린다 |

- 몇 번을 실행해도 결과가 같다. 이미 적용된 상태면 `unchanged`가 나온다 (`secret/app-secret`은 매번 `configured`로 나오지만 값은 그대로다).
- `app-config.yaml`·`secret.yaml`을 고친 뒤에는 이 스크립트 다음에 `kubectl rollout restart -n crewrun deploy`를 한다. 환경변수는 Pod가 시작할 때만 읽는다.
- `kubectl apply -f k8s`나 `kubectl apply -k k8s/overlays/local`로 대신할 수 없는 이유는 아래 「로컬에서 띄우기」에 있다.

## 로컬에서 띄우기 (k3d)

준비: Docker Desktop, [k3d](https://k3d.io), kubectl

```bash
# 1. 클러스터 (처음 한 번). 80 포트로 Traefik에 들어온다. 메모리를 아끼려고 metrics-server는 끈다
k3d cluster create crewrun -p "80:80@loadbalancer" --k3s-arg "--disable=metrics-server@server:0"

# 2. 인프라 (compose). 앱 컨테이너는 띄우지 않는다. Kafka internal 리스너(29092)도 내 PC에 연다
#    compose의 nginx(80)는 띄우지 않는다. 80은 k3d가 쓴다
docker compose -f backend/compose.yml -f k8s/overlays/local/compose.k3d.yml up -d

# 3. 비밀값. 복사한 뒤 backend/.env의 같은 키 값을 채운다
#    MYSQL_USER·MYSQL_PASS는 backend/compose.yml의 bukang-db 앱 계정, JWT_SECRET은 openssl rand -base64 32
cp k8s/overlays/local/secret.yaml.example k8s/overlays/local/secret.yaml

# 4. 이미지를 만들어 k3d에 넣기 (앱 12개 모두는 10분 넘게 걸린다. 고친 것만 이름을 주면 그것만 만든다)
k8s/scripts/local-images.sh
k8s/scripts/local-images.sh member-service web

# 5. 적용하고 Pod가 모두 뜰 때까지 기다리기
k8s/scripts/local-apply.sh --wait
```

`local-apply.sh`는 아래 한 줄을 실행한다. `kubectl apply -f k8s`나 `kubectl apply -k`로는 띄울 수 없다.
`-f`는 파일을 합치지 않고 그대로 보내서 패치 조각(`spring-common`)이 Deployment로 만들어지고 namespace도 빠진다.
`-k`는 nginx 설정을 k8s 폴더 밖(`backend/docker/nginx`)에서 읽는 것을 막는데, 이를 푸는 `--load-restrictor`를 붙일 수 없다.

```bash
kubectl kustomize --load-restrictor LoadRestrictionsNone k8s/overlays/local | kubectl apply -f -
```

접속: <http://localhost> (또는 <http://crewrun.127.0.0.1.nip.io>). Pod가 뜨기 전에는 Traefik의 `404 page not found`가 나온다.

이미지를 다시 넣었을 때는 태그가 `latest`로 같아 apply만으로는 바뀌지 않는다. `kubectl rollout restart -n crewrun deploy/<이름>`으로 다시 띄운다.

### 뜨지 않을 때 (2026-11-15에 겪은 것)

| 증상 (`kubectl logs --previous`) | 원인 | 해결 |
|---|---|---|
| `Could not resolve placeholder 'TOSS_SECRET_KEY'` | 앱 설정에 기본값이 없는 키가 Secret에 없음 | `secret.yaml`에 키를 빈 값으로라도 둔다 (example에 있음) |
| `Detected failed migration to version ...` (Flyway) | 로컬 DB에 예전에 실패한 마이그레이션 기록이 남음 | 로컬 DB를 새로 만든다: `docker compose -f backend/compose.yml up -d --force-recreate -V bukang-db` (데이터 삭제) |
| `Schema validation: missing column ...` | Flyway 도입 전에 만든 표라 칸이 없음 | 위와 같음 |

### 확인한 것 (2026-11-15)

http://localhost 로 화면(200), 가입(201) → 로그인(200) → 내 정보(토큰 검사 통과, 200) → 대회 목록(200),
`/internal` 막기(404), 로그인 요청 제한(21번째부터 429와 우리 JSON 응답)을 확인했다.
로컬에는 포트원 키가 없어 가입은 본인인증 결과를 Redis(`identity-verifications:<ID>:verified`)에 직접 넣어 확인했다.

## 메모리 (로컬 k3d, 뜬 직후, 2026-11-15)

프로세스 메모리(cgroup `memory.stat`의 anon)다. 파일 캐시는 빼고, 필요하면 비울 수 있다.
힙은 상한의 60%까지 커질 수 있어서, 뜬 직후 값이 상한의 80%를 넘는 서비스는 상한을 올렸다.

| 앱 | 프로세스 메모리 | 상한 (limits) | 요청 (requests) |
|---|---|---|---|
| gateway | 209Mi | 512Mi | 384Mi |
| member-service | 414Mi | **640Mi** (512에서 올림) | 512Mi |
| cash-service | 377Mi | 512Mi | 384Mi |
| payout-service | 452Mi | **640Mi** (512에서 올림) | 512Mi |
| file-service | 313Mi | 512Mi | 384Mi |
| order-service | 493Mi | 768Mi | 512Mi |
| product-service | 459Mi | 640Mi | 512Mi |
| queue-service | 337Mi | 512Mi | 384Mi |
| notification-service | 280Mi | 512Mi | 384Mi |
| board-service | 366Mi | 512Mi | 384Mi |
| ai-service | 222Mi | 512Mi | 384Mi |
| web | 55Mi | 256Mi | 128Mi |
| nginx | 11Mi | 64Mi | 32Mi |
| **합계** | **약 3.9Gi** | **약 6.4Gi** | **약 4.8Gi** |

- 운영 메인 EC2(7.6GB)에는 여기에 k3s 자체(약 0.5GB)와 figcraft 컨테이너가 더 올라간다. 상한 합계는 모든 앱이 동시에 상한까지 찼을 때 값이다.
- 요청이 적은 상태에서 잰 값이다. 운영으로 옮긴 뒤 `kubectl describe pod`의 재시작 이유(`OOMKilled`)를 보고 다시 맞춘다.
- 재는 명령: `kubectl exec -n crewrun deploy/<이름> -- grep -E "^anon " /sys/fs/cgroup/memory.stat` (metrics-server를 꺼서 `kubectl top`은 쓰지 않는다)

## 로컬 명령 모음 (상황별)

모두 저장소 루트에서 Git Bash로 실행한다.

### 하루 시작·끝

```bash
# 시작: 인프라 → 클러스터 순서. 클러스터를 켜면 떠 있던 Pod가 다시 뜬다 (몇 분)
docker compose -f backend/compose.yml -f k8s/overlays/local/compose.k3d.yml up -d
k3d cluster start crewrun
kubectl rollout status -n crewrun deploy --timeout=600s

# 끝: 클러스터와 인프라를 멈춘다 (지우지 않는다. 데이터와 이미지는 남는다)
k3d cluster stop crewrun
docker compose -f backend/compose.yml stop
```

### 코드를 고쳤을 때 (이미지 다시 만들기)

```bash
# 고친 서비스만 빌드해 k3d에 넣고 다시 띄운다 (이름은 compose 서비스 이름)
k8s/scripts/local-images.sh member-service
kubectl rollout restart -n crewrun deploy/bukang-member-service
kubectl rollout status -n crewrun deploy/bukang-member-service

# 여러 개
k8s/scripts/local-images.sh member-service web
kubectl rollout restart -n crewrun deploy/bukang-member-service deploy/bukang-web
```

### k8s 파일을 고쳤을 때

```bash
# Deployment·Service·패치·nginx 설정을 고쳤으면 적용만 하면 된다 (바뀐 Pod만 다시 뜬다)
k8s/scripts/local-apply.sh

# app-config.yaml·secret.yaml을 고쳤으면 적용한 뒤 앱을 모두 다시 띄운다 (환경변수는 시작할 때만 읽는다)
k8s/scripts/local-apply.sh
kubectl rollout restart -n crewrun deploy

# 적용하기 전에 합쳐진 결과만 보기 (Secret 값이 base64로 찍히니 공유하지 않는다)
kubectl kustomize --load-restrictor LoadRestrictionsNone k8s/overlays/local
```

### 상태·로그 보기

```bash
kubectl get pods -n crewrun                                     # Pod 상태 (READY 1/1, STATUS Running이면 정상)
kubectl get pods -n crewrun -w                                  # 바뀔 때마다 계속 보기 (Ctrl+C로 끝)
kubectl get all,configmap,secret,ingress -n crewrun             # 만든 객체 전부
kubectl logs -n crewrun deploy/bukang-member-service -f         # 로그 계속 보기
kubectl logs -n crewrun deploy/bukang-member-service --previous # 죽기 전 로그 (CrashLoopBackOff일 때)
kubectl describe pod -n crewrun <Pod 이름>                       # 뜨지 않는 이유 (맨 아래 Events, OOMKilled 등)
kubectl get events -n crewrun --sort-by=.lastTimestamp          # 최근 일어난 일
kubectl exec -it -n crewrun deploy/bukang-member-service -- sh  # Pod 안으로 들어가기
```

### 접속

| 주소 | 내용 |
|---|---|
| <http://localhost>, <http://crewrun.127.0.0.1.nip.io> | 크루런 화면·API (Traefik → nginx) |
| <http://localhost:8091> | Kafka Console (compose) |

### 지우기·초기화

```bash
# 앱만 모두 지우기 (클러스터와 인프라는 남는다). 다시 띄우려면 k8s/scripts/local-apply.sh --wait
kubectl kustomize --load-restrictor LoadRestrictionsNone k8s/overlays/local | kubectl delete -f -

# 로컬 DB를 빈 상태로 새로 만들기 (데이터 삭제). 그 뒤 앱을 다시 띄워 Flyway가 표를 만들게 한다
docker compose -f backend/compose.yml -f k8s/overlays/local/compose.k3d.yml up -d --force-recreate -V bukang-db
kubectl rollout restart -n crewrun deploy

# 클러스터 통째로 지우기 (넣어 둔 이미지도 사라진다. 다시 만들 때는 위 「로컬에서 띄우기」 1번부터)
k3d cluster delete crewrun
```

## 정해 둔 것

- **상태 확인은 포트 연결만 봅니다.** actuator가 들어오면 `patches/spring-common.yaml`의 프로브를 `httpGet /actuator/health`로 바꿉니다.
- **Pod는 서비스마다 1개입니다.** 스케줄 작업 분산 락(BUK-206)이 머지되기 전에 늘리면 스케줄 작업이 겹쳐 돕니다.
- **메모리:** 힙은 상한의 60%(`MaxRAMPercentage=60`), 공통 상한 512Mi, order 768Mi, product·member·payout 640Mi (위 메모리 표).
- **inquiry-service는 띄우지 않습니다.** compose 운영에서도 띄우지 않고 있습니다(게이트웨이 라우팅만 있음).
- **설정은 두 곳으로 나눕니다.** 비밀이 아닌 값(인프라 주소·프로파일)은 ConfigMap `app-config`(`base/app-config.yaml`, 커밋), 비밀값은 Secret `app-secret`(`overlays/<환경>/secret.yaml`, git 제외)에 둡니다. 같은 키를 두 곳에 두지 않습니다.
- **`app-config.yaml`·`secret.yaml`을 고친 뒤에는 Pod를 다시 띄웁니다** (`kubectl rollout restart -n crewrun deploy`). apply만 하면 떠 있는 Pod는 옛 값을 씁니다.
- **nginx 설정(`nginx-config`)만 kustomize configMapGenerator로 만듭니다.** `routes.conf`·`upstreams.conf`를 복사하지 않고 compose와 같은 파일을 읽으려는 것입니다. 내용이 바뀌면 이름 뒤 해시가 바뀌어 nginx Pod가 저절로 새로 뜹니다.
- **로컬 `secret.yaml`에는 로컬에서 쓰는 값만 넣습니다.** 실제 AWS 키·운영 Slack 주소는 넣지 않습니다(S3는 S3Mock용 가짜 값). 운영 비밀값은 SSM Parameter Store에 둡니다.
