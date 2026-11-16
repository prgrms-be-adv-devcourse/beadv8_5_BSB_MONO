# 모니터링 서버 (개인 AWS EC2)

운영 지표를 받아 저장하고 Grafana로 보여 주는 서버입니다(BUK-256, BUK-259).
메인 EC2는 메모리 여유가 약 2GB라 Prometheus 전체를 올리지 않고 수집기(에이전트 모드)만 둡니다. 저장과 화면은 이 서버가 맡습니다.

```
[학원 계정 VPC]
 메인 k3s 수집기(에이전트 모드) ──https + 비밀번호──┐
[내 PC] k6 ──────────────────────https + 비밀번호──┤
                                                  ▼
[개인 AWS] Caddy(443) ─ prom.<DOMAIN>    → Prometheus (받기·저장, 15일·6GB)
                     ─ grafana.<DOMAIN> → Grafana ◀ 브라우저
```

| 항목 | 값 |
|---|---|
| 인스턴스 | `bukang-devops`, t3.small(2GB) + swap 2GB, gp3 20GB, Ubuntu 26.04 |
| 주소 | 탄력적 IP 3.38.72.162, 도메인 `bukang-devops.duckdns.org` (하위 주소도 같은 IP) |
| 비용 | 무료 플랜 크레딧에서 월 약 $25. **2주쯤 쓰고 지웁니다** (아래 「지우기」) |
| 서버 위치 | `/opt/monitoring-node` |

무료 플랜 계정은 프리 티어 유형만 고를 수 있어 t3.medium이 없습니다. 메모리가 모자라면 중지 → 유형을 c7i-flex.large(4GB)로 변경 → 시작합니다. 탄력적 IP라 주소는 그대로입니다.

## 주소

| 주소 | 무엇 | 로그인 |
|---|---|---|
| `https://grafana.<DOMAIN>` | Grafana. 대시보드는 「크루런」 폴더 | Grafana 계정 (`admin` 비밀번호는 서버 `.env`) |
| `https://prom.<DOMAIN>` | Prometheus 화면과 API. 지표 받기는 `/api/v1/write` | 계정 `crewrun`, 비밀번호는 서버 `secrets/prom-password` |
| `https://<DOMAIN>` | Grafana로 넘김 | |

Prometheus에는 로그인 기능이 없어 Caddy가 비밀번호를 확인합니다. Prometheus·Grafana 포트는 밖에 열지 않습니다.

## 파일

| 파일 | 하는 일 |
|---|---|
| `compose.yml` | Caddy, Prometheus(받기 기능, 보관 15일·6GB), Grafana |
| `Caddyfile` | 주소별로 넘기기, https 인증서, Prometheus 비밀번호 |
| `prometheus.yml` | 자기 자신만 가져감. 운영 지표의 job 이름은 수집기 설정이 정함 |
| `grafana/provisioning/` | 데이터 소스(uid `prometheus`)와 대시보드 폴더를 시작할 때 등록 |
| `fetch-dashboards.sh` | grafana.com 대시보드 5개를 고정 버전으로 받아 `grafana/dashboards/`에 둠 (git 제외) |
| `setup.sh` | 처음 한 번: swap, Docker, `.env`, Prometheus 비밀번호·해시(`secrets/`, git 제외), 대시보드 받기 |
| `.env.example` | 도메인과 Grafana 관리자 비밀번호 키 |

| 대시보드 | grafana.com ID | 보는 것 |
|---|---|---|
| JVM (Micrometer) | 4701 | Spring 앱 메모리·GC·스레드. 위쪽 `application`에서 서비스를 고름 |
| Node Exporter Full | 1860 | 메인·보조 EC2의 CPU·메모리·디스크 |
| MySQL Exporter Quickstart | 14057 | MySQL 접속 수, 쿼리 |
| Elasticsearch Exporter Quickstart | 14191 | ES 힙·GC, 검색 지연, 거부된 요청 |
| k6 Prometheus | 19665 | 부하 테스트 결과 |

## 처음 준비

1. EC2 보안 그룹 인바운드: 80·443은 0.0.0.0/0 (인증서 확인과 화면·지표 받기), 22는 내 IP만.
2. DuckDNS에서 도메인의 IP를 탄력적 IP로 맞춥니다.
3. 서버에서:
   ```bash
   sudo git clone https://github.com/prgrms-be-adv-devcourse/beadv8_5_BSB_MONO.git /opt/crewrun
   sudo cp -r /opt/crewrun/infra/monitoring-node /opt/monitoring-node
   cd /opt/monitoring-node
   sudo bash setup.sh
   sudo docker compose up -d
   ```
   저장소 폴더를 그대로 쓰지 않고 복사하는 이유: `.env`·`secrets/`·받은 대시보드가 저장소 작업 폴더에 섞이지 않게 합니다.
4. 확인:
   ```bash
   curl -sI https://grafana.<DOMAIN> | head -1                                    # 302 (로그인 화면으로)
   curl -s -o /dev/null -w '%{http_code}\n' https://prom.<DOMAIN>/api/v1/status/buildinfo   # 401 (비밀번호 없으면 막힘)
   ```

인증서는 Caddy가 처음 요청을 받을 때 받습니다. `caddy-data` 볼륨을 지우면 다시 받는데, Let's Encrypt는 같은 주소에 주 5회까지만 내줍니다.

## 보내는 쪽 설정

| 보내는 쪽 | 설정 |
|---|---|
| 메인 k3s 수집기 (BUK-258) | `remote_write.url: https://prom.<DOMAIN>/api/v1/write`, `basic_auth`에 `crewrun`과 비밀번호 |
| k6 (내 PC) | `K6_PROMETHEUS_RW_SERVER_URL=https://prom.<DOMAIN>/api/v1/write`, `K6_PROMETHEUS_RW_USERNAME=crewrun`, `K6_PROMETHEUS_RW_PASSWORD=<비밀번호>` |

비밀번호는 서버에서 `sudo cat /opt/monitoring-node/secrets/prom-password`로 봅니다. 저장소·슬랙에 적지 않습니다.

## 보기 전용 계정 만들기

화면을 바꾸지 못하고 보기만 하는 계정입니다(팀원용). Grafana → Administration → Users → New user로 만들면 기본 권한이 Viewer입니다.

## 지우기 (2주 뒤)

1. 대시보드마다 화면 캡처와 JSON(공유 → Export)을 남깁니다. 부하 테스트 결과는 `backend/loadtest/<시나리오>/results/`에 정리합니다.
2. 메인 k3s 수집기를 먼저 멈춥니다(보낼 곳이 사라지면 수집기 로그에 오류가 쌓임).
3. EC2를 종료합니다. 루트 볼륨은 「종료 시 삭제」라 함께 지워집니다.
4. 탄력적 IP를 릴리스합니다. 연결 없이 두면 시간당 요금이 계속 나갑니다.
