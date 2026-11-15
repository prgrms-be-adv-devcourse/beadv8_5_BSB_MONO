# 운영 서버 (AWS)

운영을 docker compose(메인 EC2 한 대)에서 **메인 EC2 k3s(앱) + 보조 EC2 compose(데이터)**로 옮깁니다(BUK-220).
이 문서는 전체 구성, 필요한 AWS 자원, 한 번에 옮기는 전환 절차서입니다. 서버별 자세한 내용은 각 폴더의 README에 있습니다.

```
브라우저 ─ https://crewrun.duckdns.org
   │
메인 EC2 (퍼블릭, 15.164.150.76 / 10.0.1.145) ── k3s
   Traefik(443, 인증서) → nginx → gateway·서비스 10개·web          ← infra/main-node, k8s/
   │  사설망 (3306·6379·29092·9200)
보조 EC2 (프라이빗, 10.0.2.222) ── docker compose
   MySQL · Redis · Redpanda · Elasticsearch                         ← infra/data-node
   (인터넷은 메인 EC2의 NAT로 나감)

GitHub Actions ─(OIDC, 키 없음)→ AWS 역할 ─(SSM Run Command)→ 각 서버의 deploy.sh
메인 EC2 deploy.sh ─(EC2 역할)→ Parameter Store /crewrun/prod/* (비밀값)
```

## 폴더와 파일

| 위치 | 하는 일 |
|---|---|
| `infra/main-node/` | k3s 설치, Traefik HTTPS, 비밀값 옮기기, 앱 배포 스크립트 |
| `infra/data-node/` | 데이터 서버 준비(setup.sh), 인프라 배포 스크립트 |
| `infra/iam/` | AWS 역할에 붙일 정책 JSON 3개 (아래 「AWS 자원」) |
| `k8s/overlays/aws/` | 운영용 k8s 설정 (GHCR 이미지, 보조 EC2 주소, 운영 공개 설정, 도메인·HTTPS) |
| `.github/workflows/k8s-cd.yml` | 이미지 빌드 → 메인 EC2 `deploy.sh <SHA>` (지금은 손으로 실행만) |
| `.github/workflows/data-node-cd.yml` | 보조 EC2 `deploy.sh <main\|SHA>` (손으로 실행만) |
| `.github/scripts/ssm-run.sh` | 두 워크플로가 SSM으로 명령을 보내고 결과를 기다리는 스크립트 |

## AWS 자원 (BUK-220 D6)

계정 `823315816237`, 리전 `ap-northeast-2`(서울). 계정 번호는 S3 버킷 이름에서 읽은 값이라 콘솔 오른쪽 위에서 한 번 확인합니다.

### 역할 3개

| 역할 (이름 예) | 누가 쓰나 | 붙일 권한 | 신뢰 |
|---|---|---|---|
| `crewrun-main-ec2` | 메인 EC2 | AWS 관리형 `AmazonSSMManagedInstanceCore` + `infra/iam/main-ec2-policy.json` | EC2 |
| `crewrun-data-ec2` | 보조 EC2 | `AmazonSSMManagedInstanceCore`만 | EC2 |
| `crewrun-github-deploy` | GitHub Actions | `infra/iam/github-deploy-policy.json` | `infra/iam/github-deploy-trust.json` |

- **`AmazonSSMManagedInstanceCore`:** 서버가 SSM에 등록되어 Run Command를 받게 합니다. 지금 두 서버에는 역할이 없어 SSM Agent가 돌아도 등록되지 않습니다(2026-11-14 확인).
- **`main-ec2-policy.json`:** `/crewrun/prod/*`·`/crewrun/registry/*` **읽기**와, Parameter Store를 거칠 때만 쓰는 `kms:Decrypt`. 쓰기 권한은 없습니다. 보조 EC2는 비밀값을 읽지 않으므로(`.env`를 서버에 둠) 이 정책을 붙이지 않습니다.
- **`github-deploy-trust.json`:** 이 저장소의 **main 브랜치에서 돈 워크플로만** 역할을 받을 수 있습니다. 다른 브랜치·PR·포크에서는 거부됩니다.
- **`github-deploy-policy.json`:** 두 서버에 `AWS-RunShellScript`를 보내고 결과를 읽는 것만 됩니다. `<MAIN_INSTANCE_ID>`·`<DATA_INSTANCE_ID>`를 실제 인스턴스 ID(`i-...`)로 바꿔 붙입니다.
  - 이 역할은 서버에서 root로 아무 명령이나 실행할 수 있는 것과 같습니다. main 브랜치 보호(리뷰 필수)가 곧 이 권한의 보호막입니다.

### 그 밖의 자원

| 자원 | 내용 |
|---|---|
| GitHub OIDC 공급자 | IAM → 자격 증명 공급자 → OpenID Connect, URL `https://token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com`. 계정에 이미 있으면 그대로 씁니다 |
| 보조 EC2 보안 그룹 | 인바운드 3306·6379·29092·9200을 **메인 EC2의 보안 그룹에서만** 허용. 아웃바운드는 기본(전체) 유지(SSM·Docker 이미지에 필요) |
| Parameter Store | `/crewrun/prod/<키>` 22개, `/crewrun/registry/GHCR_AUTH`. `infra/main-node/env-to-parameter-store.sh`로 넣음 |
| 저장소 Variables | `AWS_DEPLOY_ROLE_ARN`(배포 역할 ARN), `MAIN_INSTANCE_ID`, `DATA_INSTANCE_ID`. 비밀값이 아니라 Secrets가 아닌 Variables에 둡니다 (저장소 관리자 권한 필요) |

### D6 순서

서버는 그대로 운영하면서 AWS 쪽만 준비합니다. 사용자 서비스에는 영향이 없습니다.

1. 이 PR이 main에 머지되어 있어야 합니다(서버가 main에서 파일을 받음).
2. OIDC 공급자 확인·생성 → 역할 3개 생성 → 두 EC2에 역할 연결(EC2 → 작업 → 보안 → IAM 역할 수정, 재시작 필요 없음).
3. 두 서버에서 SSM Agent를 다시 시작하고 등록을 확인합니다.
   ```bash
   sudo snap restart amazon-ssm-agent      # 각 서버에서
   ```
   ```bash
   # CloudShell에서. 두 서버가 Online이면 됨
   aws ssm describe-instance-information --query 'InstanceInformationList[].[InstanceId,PingStatus,IPAddress]' --output table
   ```
4. 보조 EC2 보안 그룹 인바운드 추가.
5. 메인 EC2에 저장소를 받고 비밀값을 옮깁니다(값은 서버 안에서만 움직임).
   ```bash
   sudo git clone https://github.com/prgrms-be-adv-devcourse/beadv8_5_BSB_MONO.git /opt/crewrun
   sudo snap install aws-cli --classic; sudo apt-get install -y jq
   cd /opt/crewrun
   # 쓰기 권한이 있는 사용자 키를 이 셸에서만 씀 (끝나면 exit)
   export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_DEFAULT_REGION=ap-northeast-2
   bash infra/main-node/env-to-parameter-store.sh           # 미리보기: 키 이름과 길이만
   bash infra/main-node/env-to-parameter-store.sh --apply
   exit
   ```
6. GitHub 저장소 Variables 3개 등록.

## 전환 절차서 (BUK-220 D7)

운영을 모두 내리고 한 번에 옮깁니다. 공지는 미리 합니다. 예상 중단 시간은 1~2시간입니다.
명령 블록마다 **어느 서버에서** 실행하는지 적었습니다. 메인 EC2의 옛 저장소는 `~/workspace/beadv8_1_BSB_MONO`(compose 실행 위치는 그 아래 `backend/`)입니다.

### 0. 전날 확인 (읽기만, 서비스 영향 없음)

```bash
# 메인 EC2
cd ~/workspace/beadv8_1_BSB_MONO && git remote -v && git log --oneline -1
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}'
docker exec bukang-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES"'   # 앱 DB가 bukang-db 하나인지
docker exec bukang-elasticsearch curl -s localhost:9200 | grep '"number"'                  # 새 서버는 9.4.5. 이보다 높으면 멈추고 상의
jq -r '.auths["ghcr.io"].auth' ~/.docker/config.json | base64 -d | cut -d: -f1            # GHCR 토큰 주인 (토큰은 출력하지 않음)
sudo iptables -t nat -S POSTROUTING | grep MASQUERADE                                      # NAT 규칙
```

- GHCR 토큰 주인에게 만료일을 묻습니다. 곧 만료되면 새 토큰(`read:packages`)으로 `/crewrun/registry/GHCR_AUTH`를 바꿔 둡니다.
- D6가 끝나 두 서버가 SSM에 Online이고, Parameter Store·Variables가 들어가 있어야 합니다.
- `k8s-cd.yml`을 한 번 돌려 **이미지 빌드만** 성공하는지 봅니다(아직 k3s가 없어 deploy 잡은 실패해도 됨). 전환 당일 빌드 시간을 줄입니다.

### 1. 옛 배포 막기

GitHub → Actions → **app-cd** → `...` → **Disable workflow**.
dev→main 자동 머지(매일 05:00) 때 app-cd가 compose를 다시 띄워 80·443을 빼앗는 것을 막습니다.

### 2. 요청 끊기 → 메시지 비우기 → 앱 멈추기

```bash
# 메인 EC2
cd ~/workspace/beadv8_1_BSB_MONO/backend
docker stop bukang-nginx                                 # 새 요청 차단 (사이트 접속 불가 시작)
sleep 60
docker exec bukang-kafka rpk group list
docker exec bukang-kafka rpk group describe <그룹 이름>   # 그룹마다 TOTAL-LAG 0 확인. 0이 아니면 잠시 기다림
# 앱만 멈춤 (인프라는 3단계 백업에 필요해 그대로. 컨테이너는 지우지 않고 되돌릴 때 씀)
docker compose stop web gateway member-service cash-service payout-service file-service \
	order-service product-service queue-service notification-service board-service ai-service
```

Redpanda 메시지는 옮기지 않습니다. 남은 메시지가 없는 것을 확인하고 멈추면 잃는 것이 없습니다.

### 3. 데이터 백업 (메인 EC2)

```bash
mkdir -p ~/migration && cd ~/migration
# MySQL: 덤프 (볼륨을 통째로 옮기지 않고 SQL로 옮김. Flyway 기록 포함)
docker exec bukang-db sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --set-gtid-purged=OFF --databases bukang-db' | gzip > db.sql.gz
# Redis: 지금 상태를 파일로 저장한 뒤 복사 (로그인·대기열 상태 유지)
docker exec bukang-redis redis-cli SAVE
docker cp bukang-redis:/data/dump.rdb redis-dump.rdb
# Elasticsearch: 멈춘 뒤 데이터 폴더를 통째로 복사 (색인을 처음부터 다시 만드는 기능이 앱에 없음)
docker stop bukang-elasticsearch
docker cp bukang-elasticsearch:/usr/share/elasticsearch/data - | gzip > es-data.tar.gz
ls -lh                                                    # 세 파일 크기가 0이 아닌지
zcat db.sql.gz | tail -1                                  # "-- Dump completed" 이면 덤프가 끝까지 됨
```

### 4. compose 전체 멈추기 (메인 EC2)

```bash
cd ~/workspace/beadv8_1_BSB_MONO/backend
docker compose --profile app stop                         # 인프라까지 모두 멈춤 (figcraft는 다른 프로젝트라 그대로)
# 서버·Docker가 다시 시작해도 옛 컨테이너가 살아나지 않게 함 (되돌릴 때 원래대로 바꿈)
docker inspect --format '{{.Name}} {{.HostConfig.RestartPolicy.Name}}' $(docker ps -aq --filter name=bukang-) > ~/migration/restart-policy.txt
docker update --restart=no $(docker ps -aq --filter name=bukang-)
sudo ss -ltnp '( sport = :80 or sport = :443 )'           # 아무것도 없어야 함
```

### 5. 보조 EC2 준비와 데이터 복원

```bash
# 메인 EC2 → 보조 EC2로 백업 파일 보내기
ssh -i ~/team05-bukang-EC2-private-key-pair.pem ubuntu@10.0.2.222 mkdir -p migration
scp -i ~/team05-bukang-EC2-private-key-pair.pem ~/migration/{db.sql.gz,redis-dump.rdb,es-data.tar.gz} ubuntu@10.0.2.222:migration/
ssh -i ~/team05-bukang-EC2-private-key-pair.pem ubuntu@10.0.2.222
```

```bash
# 보조 EC2
curl -fsSLO https://raw.githubusercontent.com/prgrms-be-adv-devcourse/beadv8_5_BSB_MONO/main/infra/data-node/setup.sh
sudo bash setup.sh
sudo vi /opt/crewrun/infra/data-node/.env
#   MYSQL_USER·MYSQL_PASSWORD = 운영 .env의 MYSQL_USER·MYSQL_PASS와 같은 값 (메인 EC2에서 grep으로 보고 옮겨 적음)
#   MYSQL_ROOT_PASSWORD = 새 값 (openssl rand -base64 24)

# Redis·ES 데이터는 서비스를 처음 띄우기 전에 볼륨에 넣는다
# ("volume already exists but was not created by Docker Compose" 경고는 무시해도 됨)
sudo docker volume create bukang-redis-data
sudo docker volume create bukang-es-data
sudo docker run --rm -v bukang-redis-data:/data -v ~/migration:/m:ro alpine sh -c 'cp /m/redis-dump.rdb /data/dump.rdb && chown 999:999 /data/dump.rdb'
gunzip -c ~/migration/es-data.tar.gz | sudo docker run --rm -i -v bukang-es-data:/target alpine sh -c 'tar -x -C /target --strip-components=1 && chown -R 1000:0 /target'

sudo bash /opt/crewrun/infra/data-node/deploy.sh main     # 4개가 healthy가 될 때까지 기다림

# MySQL 복원
gunzip -c ~/migration/db.sql.gz | sudo docker exec -i bukang-db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
sudo docker exec bukang-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM \`bukang-db\`.flyway_schema_history"'
sudo docker exec bukang-elasticsearch curl -s 'localhost:9200/_cat/indices?v'          # 옛 서버와 같은 인덱스·문서 수
sudo docker exec bukang-redis redis-cli DBSIZE
exit
```

### 6. k3s 설치 (메인 EC2)

```bash
cd /opt/crewrun && sudo git fetch && sudo git checkout --detach origin/main
sudo ACME_EMAIL="$(grep '^CERTBOT_EMAIL=' ~/workspace/.env | cut -d= -f2-)" bash infra/main-node/install-k3s.sh
sudo iptables -t nat -S POSTROUTING | grep MASQUERADE                                     # 0단계와 같은지
ssh -i ~/team05-bukang-EC2-private-key-pair.pem ubuntu@10.0.2.222 curl -sI https://github.com | head -1   # 보조 EC2 인터넷
```

- 인증서는 처음부터 실제 서버로 받습니다. 7단계에서 인증서가 거듭 실패할 때만 `ACME_STAGING=1`로 원인을 찾습니다(실제 서버는 같은 도메인 주 5회 제한).
- 시험 인증서에서 실제로 바꿀 때는 받아 둔 시험 인증서를 지워야 합니다.
  ```bash
  sudo ACME_EMAIL=... bash infra/main-node/install-k3s.sh           # ACME_STAGING 없이 다시 실행 (설정만 바뀜)
  sudo k3s kubectl -n kube-system exec deploy/traefik -- rm -f /data/acme.json
  sudo k3s kubectl -n kube-system rollout restart deploy/traefik
  ```

### 7. 첫 배포

GitHub → Actions → **k8s-cd** → Run workflow (main). 빌드 → 메인 EC2 `deploy.sh <SHA>`까지 15~30분 걸립니다.
Actions가 안 되면 서버에서 직접 실행해도 같습니다(그 커밋의 이미지가 GHCR에 있어야 함).

```bash
# 메인 EC2
sudo bash /opt/crewrun/infra/main-node/deploy.sh <0단계에서 빌드한 main 커밋 SHA>
```

### 8. 확인

```bash
# 메인 EC2
sudo k3s kubectl -n crewrun get pods                      # 13개 Running, RESTARTS가 늘지 않음
sudo k3s kubectl -n kube-system logs deploy/traefik | grep -i acme | tail   # 인증서 오류 없음
curl -sI http://crewrun.duckdns.org | head -3             # 301/308 → https
curl -sI https://crewrun.duckdns.org | head -1            # 200
free -h                                                   # 메모리 여유 (swap을 많이 쓰지 않는지)
```

브라우저에서: 로그인(카카오·구글) → 대회 검색(ES) → 상품 상세(이미지, S3) → 예치금 충전 화면 → 주문 하나(Kafka 메시지가 오가는 흐름). 판매자 화면에서 이미지 올리기(S3).

### 9. 되돌리기 (8단계가 실패하고 바로 고칠 수 없을 때)

```bash
# 메인 EC2: k3s를 멈춰 80·443을 비움 (설치는 남겨 둠)
sudo systemctl disable --now k3s
sudo /usr/local/bin/k3s-killall.sh
sudo iptables -t nat -S POSTROUTING | grep -q -- '-s 10.0.0.0/16.*MASQUERADE' \
	|| sudo iptables -t nat -A POSTROUTING -s 10.0.0.0/16 -j MASQUERADE
# 옛 compose 되살리기
cd ~/workspace/beadv8_1_BSB_MONO/backend
while read -r name policy; do docker update --restart="$policy" "${name#/}"; done < ~/migration/restart-policy.txt
docker compose --profile app start
```

- 그 뒤 GitHub에서 **app-cd**를 다시 켭니다(Enable workflow).
- 옛 DB는 2단계 시점 그대로입니다. k3s로 운영한 사이에 생긴 주문·가입은 옛 DB에 없습니다. 오래 운영한 뒤라면 되돌리기 전에 보조 EC2에서 덤프해 옛 DB로 거꾸로 옮길지 정합니다.
- 보조 EC2의 데이터는 지우지 않고 둡니다. 원인을 고친 뒤 3단계부터 다시 합니다(보조 EC2 볼륨은 `docker compose ... down -v`가 아니라 볼륨을 직접 지우고 다시 복원).

## 전환 뒤 (BUK-220 D8)

- `k8s-cd.yml`에 main push 트리거를 켭니다. `app-cd.yml`(compose 배포)과 셀프호스티드 러너를 정리합니다.
- 메인 EC2 보안 그룹의 22번 등 더는 쓰지 않는 규칙을 정리할지 정합니다(SSM Session Manager로 접속 가능).
- 옛 compose 컨테이너·볼륨과 `~/migration` 백업은 며칠 운영해 본 뒤 지울지 정합니다.
