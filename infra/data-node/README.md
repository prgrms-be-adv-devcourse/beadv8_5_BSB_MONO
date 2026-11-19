# 보조 EC2 (데이터 서버)

MySQL·Redis·Redpanda(Kafka)·Elasticsearch를 docker compose로 띄우는 서버입니다. 파일 저장소는 실제 AWS S3를 써서 S3Mock은 띄우지 않습니다. 앱은 메인 EC2의 k3s에서 돌고, 이 서버의 사설 IP로 접속합니다.
k3s agent로 묶지 않은 이유: 데이터는 자주 바뀌지 않고, 앱을 배포할 때 DB가 다시 시작될 위험을 없애려고 따로 둡니다.

| 항목 | 값 |
|---|---|
| 주소 | 사설 10.0.2.222 (프라이빗 서브넷, 외부에서 직접 접속 불가. 인터넷은 메인 EC2 NAT로 나감) |
| 공인 IP | 13.125.183.172가 붙어 있지만 쓰이지 않습니다. 서브넷이 메인 EC2 NAT로만 나가서 인터넷에서 이 IP로 닿지 않습니다(2026-11-16 확인: 22번 연결 안 됨, 밖으로 나갈 때 보이는 IP는 15.164.150.76). 붙어 있는 동안 공인 IPv4 요금(시간당 $0.005)은 나가므로 떼어도 됩니다 |
| 사양 | t3.medium, 2 vCPU, 3.7GB (+ swap 2GB, setup.sh가 만듦) |
| 저장소 위치 | `/opt/crewrun` |

## 파일

| 파일 | 하는 일 |
|---|---|
| `compose.data.yml` | `backend/compose.yml`의 인프라 정의 위에 덮어쓰기: 재시작, 이름 있는 볼륨, 사설 IP에만 포트 열기, MySQL 계정을 운영 값으로 |
| `.env.example` | 사설 IP와 MySQL 계정, exporter 비밀번호. 서버에서 `.env`로 복사해 채운다 (git 제외) |
| `exporter-user.sql` | mysqld-exporter 전용 MySQL 계정. 볼륨이 이미 있어 사람이 한 번 실행한다 (아래 「지표 exporter」) |
| `setup.sh` | 처음 한 번: swap 2GB, ES 커널 설정, Docker 설치, 저장소 받기, `.env` 만들기 |
| `deploy.sh <main\|커밋 SHA>` | 저장소를 그 커밋으로 맞추고 인프라를 띄운다. 바뀐 것만 다시 만들고, healthy가 될 때까지 기다린다. **main에 들어간 커밋만** 받는다(다른 브랜치·머지 전 커밋은 거부) |

인프라 정의(이미지 버전, Redpanda 명령, 상태 확인)는 `backend/compose.yml` 하나가 원본입니다. 로컬과 서버가 같은 정의를 씁니다.

## 여는 포트

모두 사설 IP(`DATA_NODE_BIND_IP`)에만 엽니다. 보안 그룹은 **메인 EC2에서 오는 접속만** 허용합니다(BUK-220 D6).

| 서비스 | 포트 | 앱이 부르는 이름 (메인 k3s의 Service) |
|---|---|---|
| MySQL | 3306 | `bukang-db` |
| Redis | 6379 | `bukang-redis` |
| Redpanda | 29092 (internal 리스너) | `bukang-kafka` |
| Elasticsearch | 9200 | `bukang-elasticsearch` |
| Redpanda 관리·지표 | 9644 | 수집기가 `/public_metrics`를 가져감 |
| redis-exporter | 9121 | 수집기 (job `redis`) |
| mysqld-exporter | 9104 | 수집기 (job `mysql`) |
| elasticsearch-exporter | 9114 | 수집기 (job `elasticsearch`) |
| node-exporter | 9100 | 수집기 (job `node`) |
| 게이트웨이 2 | 8000 | 메인 nginx가 게이트웨이 1과 번갈아 보냄, 수집기 (job `gateway`) |

- Redpanda는 접속한 앱에 `bukang-kafka:29092`로 다시 접속하라고 알려 줍니다. 메인 k3s의 Service `bukang-kafka`가 이 서버의 29092로 이어 주므로 이 포트를 엽니다. external 리스너(9092)는 열지 않습니다.
- k3s Pod가 이 서버로 나올 때 출발지는 메인 EC2 사설 IP(10.0.1.145)로 바뀝니다. 그래서 보안 그룹에는 메인 EC2(또는 그 보안 그룹)만 허용하면 됩니다.
- Redis·Elasticsearch에는 비밀번호가 없습니다(앱 설정이 비밀번호를 쓰지 않음). 보안 그룹이 유일한 보호막이므로 다른 출발지를 열지 않습니다.
- 관리 화면(Kafka Console 8091, Elasticvue 8090)은 띄우지 않습니다.

## 게이트웨이 2 (CRU-75)

부하 테스트(11/18·11/19)에서 메인 EC2 2코어를 가장 많이 쓴 곳이 게이트웨이였습니다(330 RPS에서 0.57코어). 같은 때 이 서버는 CPU 23%, 메모리 여유 최저 1.16GB였습니다. 그래서 게이트웨이를 이 서버에 하나 더 두고 메인 nginx가 요청을 번갈아 보냅니다.

```
인터넷 → Traefik → nginx ─┬─ 게이트웨이 1 (메인 k3s) ──────────────┬─ 서비스 (메인 k3s)
                          └─ 게이트웨이 2 (이 서버 :8000) ─ NodePort ─┘
```

- 이미지·커밋은 게이트웨이 1과 같습니다. `k8s-cd`가 메인 배포 뒤 `gateway2` job에서 `deploy.sh <SHA> gateway`로 이것만 바꿉니다. 인프라 배포(`deploy.sh <SHA>`)는 게이트웨이 2를 건드리지 않습니다.
- 서비스는 k3s 안에만 있어 게이트웨이 2는 메인 EC2의 NodePort(30080~30092, `k8s/overlays/aws/gateway2-nodeports.yaml`)로 보냅니다.
- 게이트웨이 2가 멈추면 nginx가 연결 실패(2초)를 보고 게이트웨이 1로 다시 보내고, 10초 동안 게이트웨이 2를 빼 둡니다(`k8s/overlays/aws/nginx-upstreams.conf`).
- 메모리는 운영에서 257~323MB를 썼고(요청이 늘어도 거의 그대로), 한도는 512MB입니다.

### 보안 그룹 (콘솔에서 한 번)

| 보안 그룹 | 들어오는 규칙 | 출발지 |
|---|---|---|
| 보조 EC2 | TCP 8000 | 메인 EC2 보안 그룹 |
| 메인 EC2 | TCP 30080~30092 | 보조 EC2 보안 그룹 |

**메인 EC2의 30080~30092는 절대 인터넷(0.0.0.0/0)에 열지 않습니다.** 서비스는 `X-Member-Id` 헤더를 로그인한 회원으로 그대로 믿고(JWT는 게이트웨이만 검사), 이 포트는 게이트웨이를 거치지 않고 서비스로 바로 갑니다.

### 처음 켤 때 (한 번)

1. 위 보안 그룹 두 규칙을 더합니다.
2. 이 서버의 `.env`에 `JWT_SECRET`·`GHCR_USER`·`GHCR_TOKEN`을 채웁니다(`.env.example` 참고). 값은 채팅·문서에 붙이지 않습니다.
   ```bash
   sudo -e /opt/crewrun/infra/data-node/.env
   ```
3. PR이 main에 머지되면 `k8s-cd`가 돌며 NodePort·nginx를 바꾸고 `gateway2` job이 게이트웨이 2를 띄웁니다.
   - 서버의 `deploy.sh`가 아직 옛 파일이라 첫 `gateway2` job은 인프라 배포로 끝나고 게이트웨이 2를 띄우지 못할 수 있습니다(옛 파일은 두 번째 값을 모름). 그때는 이 서버에서 한 번 더 실행합니다.
   ```bash
   sudo bash /opt/crewrun/infra/data-node/deploy.sh main gateway
   ```
4. 확인: 이 서버에서 `curl -s -o /dev/null -w '%{http_code}\n' http://10.0.2.222:8000/actuator/health` → 200. Grafana 「크루런 · JVM」에서 gateway의 지표가 두 줄(pod `bukang-gateway…`, `bukang-gateway2`)로 보입니다.

### 비밀값을 바꿨을 때

`.env`의 세 값은 Parameter Store 값을 옮겨 둔 사본입니다(이 서버에는 AWS CLI가 없어 직접 읽지 않음). 원본을 바꾸면 이 서버 `.env`도 다시 맞춥니다.

| `.env` | 원본 | 안 맞추면 |
|---|---|---|
| `JWT_SECRET` | `/crewrun/prod/JWT_SECRET` | 게이트웨이 2로 간 요청만 토큰 오류(401) |
| `GHCR_USER`·`GHCR_TOKEN` | `/crewrun/registry/GHCR_AUTH` ("아이디:토큰"의 base64) | 게이트웨이 2 배포가 이미지 받기에서 실패 |

메인 EC2에서 실행하면 화면에 값을 띄우지 않고 옮깁니다(같은 이름의 줄은 지우고 새로 씀).

```bash
J=$(aws ssm get-parameter --region ap-northeast-2 --name /crewrun/prod/JWT_SECRET --with-decryption --query Parameter.Value --output text)
A=$(aws ssm get-parameter --region ap-northeast-2 --name /crewrun/registry/GHCR_AUTH --with-decryption --query Parameter.Value --output text | base64 -d)
printf 'JWT_SECRET=%s\nGHCR_USER=%s\nGHCR_TOKEN=%s\n' "$J" "${A%%:*}" "${A#*:}" \
  | ssh -i ~/team05-bukang-EC2-private-key-pair.pem ubuntu@10.0.2.222 \
    'sudo bash -c "f=/opt/crewrun/infra/data-node/.env; sed -i -E \"/^(JWT_SECRET|GHCR_USER|GHCR_TOKEN)=/d\" \$f; cat >> \$f"'
unset J A
```

`JWT_SECRET`을 바꿨다면 옮긴 뒤 `sudo bash /opt/crewrun/infra/data-node/deploy.sh main gateway`로 게이트웨이 2를 다시 띄웁니다.

### 끄려면

메인 nginx에서 먼저 빼야 요청이 끊기지 않습니다. `k8s/overlays/aws/nginx-upstreams.conf`에서 `10.0.2.222:8000` 줄을 지우고 배포한 뒤 `docker rm -f bukang-gateway2`.

## 지표 exporter (BUK-257)

메인 k3s의 수집기(에이전트 모드)가 15초마다 사설 IP로 가져가 개인 AWS의 Prometheus로 보냅니다(`infra/monitoring-node`).
이 서버에는 k3s를 깔지 않고 exporter만 compose로 더합니다. 하나에 20~30MB 정도 씁니다.

| exporter | 보는 것 | 비고 |
|---|---|---|
| redis-exporter | Redis 메모리, 키 수, 명령 수 | 정의는 `backend/compose.yml`(로컬과 같음), 여기서는 포트만 엶 |
| mysqld-exporter | 접속 수, 쿼리 수, 느린 쿼리, 표별 읽기·쓰기 | 전용 계정 `exporter`(앱 데이터는 못 읽음). DB를 서비스별로 나눠도 하나면 됨 |
| elasticsearch-exporter | ES 힙·GC, 검색 지연, 거부된 요청, 인덱스별 문서 수 | ES에 비밀번호가 없어 계정 준비 없음 |
| node-exporter | 서버 CPU·메모리·디스크·네트워크 | 서버 네트워크를 그대로 쓰고(host) 사설 IP에서만 들음 |
| (Redpanda) | 처리량, 지연, 파티션 | exporter 없이 관리 포트 9644의 `/public_metrics` |

Redpanda 관리 포트 9644는 설정을 바꾸는 관리 API도 함께 엽니다. 보안 그룹에서 메인 EC2만 허용하는 것이 보호막입니다.

처음 한 번 (main에 머지된 뒤):

1. 보안 그룹 `sg-0f9a0ff371a92e8eb` 인바운드에 9644·9121·9104·9114·9100을 **메인 EC2 보안 그룹(`sg-09d6700bbf0f7348f`)에서만** 허용합니다.
2. 보조 EC2에서 `.env`에 비밀번호를 넣고 MySQL 계정을 만듭니다.
   ```bash
   cd /opt/crewrun
   echo "MYSQL_EXPORTER_PASSWORD=$(openssl rand -hex 24)" | sudo tee -a infra/data-node/.env >/dev/null
   PW=$(sudo grep '^MYSQL_EXPORTER_PASSWORD=' infra/data-node/.env | cut -d= -f2-)
   { echo "SET @pw = '$PW';"; cat infra/data-node/exporter-user.sql; } \
     | sudo docker exec -i bukang-db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
   unset PW
   ```
3. GitHub Actions → **data-node-cd** 실행(main). exporter 4개가 새로 뜨고, Redpanda는 포트가 늘어 다시 만들어집니다(몇 초 끊김, 데이터는 볼륨에 남음).
4. 메인 EC2에서 응답을 확인합니다.
   ```bash
   for p in 9644/public_metrics 9121/metrics 9104/metrics 9114/metrics 9100/metrics; do
     echo "$p $(curl -s -o /dev/null -w '%{http_code}' http://10.0.2.222:$p)"
   done
   curl -s http://10.0.2.222:9104/metrics | grep '^mysql_up'      # 1이면 MySQL 계정 정상
   ```

## 처음 준비 (전환 때 한 번, BUK-220 D7)

```bash
# 보조 EC2에서
curl -fsSLO https://raw.githubusercontent.com/prgrms-be-adv-devcourse/beadv8_5_BSB_MONO/main/infra/data-node/setup.sh
sudo bash setup.sh
sudo vi /opt/crewrun/infra/data-node/.env       # MySQL 계정 채우기 (앱 Secret과 같은 값)
sudo bash /opt/crewrun/infra/data-node/deploy.sh main
```

- `setup.sh`는 main 브랜치의 파일을 받습니다. 이 폴더가 main에 머지된 뒤에 씁니다.
- 지금 운영(메인 EC2 compose)의 데이터를 옮기는 순서(덤프 → 복원)는 `infra/README.md` 전환 절차서에 둡니다(BUK-220 D5).

## 다시 배포할 때

인프라 설정(`backend/compose.yml`의 인프라 부분, `compose.data.yml`)을 바꿨을 때만 합니다. 앱 배포와 따로 돕니다.

```bash
sudo bash /opt/crewrun/infra/data-node/deploy.sh <커밋 SHA>   # main에 머지된 커밋만
```

운영 DB 서버라서 리뷰를 거치지 않은 브랜치(dev, PR 브랜치)는 배포하지 않습니다. 머지 전 설정은 로컬에서 `docker compose -f backend/compose.yml -f infra/data-node/compose.data.yml --env-file <시험용 값> config`로 검사합니다.

GitHub Actions의 `data-node-cd.yml`(손으로 실행)이 SSM으로 이 명령을 보냅니다(BUK-220 D5).

## 서비스별 DB로 옮기기 (BUK-238, 한 번)

서비스마다 자기 DB(`crewrun_<서비스>`)를 쓰도록 바뀌었습니다. 앱 계정(`.env`의 `MYSQL_USER`) 하나가 모든 서비스 DB 권한을 가집니다.
새 앱은 `crewrun_<서비스>`에, 옛 앱은 `bukang-db`에 접속하므로 **표를 옮기는 것과 새 앱 배포가 함께 일어나야 합니다.**
main에 머지하면 k8s-cd가 바로 배포하므로, 머지 전에 앱을 멈추고 표를 옮깁니다. 사이트는 옮기기부터 새 앱이 뜰 때까지 멈춥니다(배포 15~30분).

1. 메인 EC2에서 앱을 멈춥니다. 옛 앱이 옮긴 뒤 빈 `bukang-db`에 표를 새로 만들지 않게 합니다.
   ```bash
   sudo k3s kubectl -n crewrun scale deploy -l app.kubernetes.io/component=spring --replicas=0
   ```
2. 보조 EC2에서 dev에 들어간 스크립트만 꺼내 미리 보고, 옮깁니다. 서버 저장소(`/opt/crewrun`)는 main 커밋 그대로 둡니다(deploy.sh가 고친 파일이 있으면 멈춤).
   ```bash
   cd /opt/crewrun && git fetch origin dev
   rm -rf /tmp/split && mkdir /tmp/split
   git archive origin/dev backend/scripts/split-bukang-db.sh backend/docker/mysql | tar -x -C /tmp/split
   sudo bash /tmp/split/backend/scripts/split-bukang-db.sh --dry-run   # 표 92개 정도가 서비스별로 나뉘는지 본다
   sudo bash /tmp/split/backend/scripts/split-bukang-db.sh
   ```
3. dev → main PR을 머지하고 k8s-cd가 끝나기를 기다린 뒤, 메인 EC2에서 앱을 다시 켭니다.
   매니페스트의 replicas(1)가 바뀌지 않아 `kubectl apply`는 1단계에서 줄인 0을 되돌리지 않습니다. 그래서 손으로 켭니다.
   ```bash
   sudo k3s kubectl -n crewrun scale deploy -l app.kubernetes.io/component=spring --replicas=1
   sudo k3s kubectl -n crewrun get pods -w   # 모두 Running·1/1이 될 때까지 (몇 분)
   ```
4. 확인: 로그인, 대회 목록·검색, 예치금, 주문. `bukang-db`에는 표가 남지 않습니다.
   ```bash
   sudo docker exec bukang-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT table_schema, COUNT(*) FROM information_schema.tables WHERE table_schema LIKE \"crewrun%\" OR table_schema = \"bukang-db\" GROUP BY 1"'
   ```
5. 나중에 data-node-cd를 돌리면 MySQL 컨테이너가 새 설정(initdb 폴더)으로 다시 만들어집니다(몇 초 끊김, 데이터는 볼륨에 남음).
   initdb는 빈 볼륨에서만 돌아 지금 데이터에는 영향이 없습니다.

되돌리기(새 앱을 옛 커밋으로 되돌릴 때): 앱을 멈추고 표를 `bukang-db`로 다시 옮긴 뒤 옛 커밋을 배포합니다.

```bash
sudo docker exec bukang-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SET SESSION group_concat_max_len = 1000000; SELECT CONCAT(\"RENAME TABLE \", GROUP_CONCAT(CONCAT(\"\`\", table_schema, \"\`.\`\", table_name, \"\` TO \`bukang-db\`.\`\", table_name, \"\`\")), \";\") FROM information_schema.tables WHERE table_schema LIKE \"crewrun%\""' \
  | sudo docker exec -i bukang-db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
```

## 메모리

메인 EC2 compose에서 잰 값(2026-11-14): MySQL 244MB, ES 747MB(힙 512MB 고정), Redpanda 27MB(최대 512MB로 제한), Redis 5MB. 합계 약 1GB로, 3.7GB 서버에 여유가 있습니다.

## S3Mock을 옮기지 않는 이유

운영 `backend/.env`(2026-11-15)의 `S3_ENDPOINT`·`S3_PUBLIC_ENDPOINT`가 빈 값입니다. file-service는 이 값이 비면 실제 AWS S3를 씁니다.
지금 메인 EC2에 떠 있는 S3Mock 컨테이너는 쓰이지 않으므로 옮기지 않습니다. 앱의 S3 설정은 `k8s/overlays/aws/app-config.yaml`에 있습니다.
