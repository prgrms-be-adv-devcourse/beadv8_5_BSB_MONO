# 보조 EC2 (데이터 서버)

MySQL·Redis·Redpanda(Kafka)·Elasticsearch를 docker compose로 띄우는 서버입니다. 파일 저장소는 실제 AWS S3를 써서 S3Mock은 띄우지 않습니다. 앱은 메인 EC2의 k3s에서 돌고, 이 서버의 사설 IP로 접속합니다.
k3s agent로 묶지 않은 이유: 데이터는 자주 바뀌지 않고, 앱을 배포할 때 DB가 다시 시작될 위험을 없애려고 따로 둡니다.

| 항목 | 값 |
|---|---|
| 주소 | 사설 10.0.2.222 (프라이빗 서브넷, 외부에서 직접 접속 불가. 인터넷은 메인 EC2 NAT로 나감) |
| 사양 | t3.medium, 2 vCPU, 3.7GB (+ swap 2GB, setup.sh가 만듦) |
| 저장소 위치 | `/opt/crewrun` |

## 파일

| 파일 | 하는 일 |
|---|---|
| `compose.data.yml` | `backend/compose.yml`의 인프라 정의 위에 덮어쓰기: 재시작, 이름 있는 볼륨, 사설 IP에만 포트 열기, MySQL 계정을 운영 값으로 |
| `.env.example` | 사설 IP와 MySQL 계정. 서버에서 `.env`로 복사해 채운다 (git 제외) |
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

- Redpanda는 접속한 앱에 `bukang-kafka:29092`로 다시 접속하라고 알려 줍니다. 메인 k3s의 Service `bukang-kafka`가 이 서버의 29092로 이어 주므로 이 포트를 엽니다. external 리스너(9092)는 열지 않습니다.
- k3s Pod가 이 서버로 나올 때 출발지는 메인 EC2 사설 IP(10.0.1.145)로 바뀝니다. 그래서 보안 그룹에는 메인 EC2(또는 그 보안 그룹)만 허용하면 됩니다.
- Redis·Elasticsearch에는 비밀번호가 없습니다(앱 설정이 비밀번호를 쓰지 않음). 보안 그룹이 유일한 보호막이므로 다른 출발지를 열지 않습니다.
- 관리 화면(Kafka Console 8091, Elasticvue 8090)은 띄우지 않습니다.

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

## 메모리

메인 EC2 compose에서 잰 값(2026-11-14): MySQL 244MB, ES 747MB(힙 512MB 고정), Redpanda 27MB(최대 512MB로 제한), Redis 5MB. 합계 약 1GB로, 3.7GB 서버에 여유가 있습니다.

## S3Mock을 옮기지 않는 이유

운영 `backend/.env`(2026-11-15)의 `S3_ENDPOINT`·`S3_PUBLIC_ENDPOINT`가 빈 값입니다. file-service는 이 값이 비면 실제 AWS S3를 씁니다.
지금 메인 EC2에 떠 있는 S3Mock 컨테이너는 쓰이지 않으므로 옮기지 않습니다. 앱의 S3 설정은 `k8s/overlays/aws/app-config.yaml`에 있습니다.
