# 주문 결제 잠금 부하 측정 도구

판정 기준과 시나리오는 [../2026-11-13-결제-잠금-판정기준.md](../2026-11-13-결제-잠금-판정기준.md)에 있어요.
운영·dev 서버에는 돌리지 않아요. 자기 컴퓨터나 GitHub 실행 머신 안에 컨테이너를 띄워 재요.

| 파일 | 하는 일 |
|---|---|
| `compose.load.yml` | 주문·예치금·상품 + MySQL·Kafka·Redis·Elasticsearch·Toxiproxy·Prometheus (프로젝트 이름 `orderload`, 자원 제한 포함) |
| `seed.sql` | 대회 1개·종목 5개·키트 옵션 20개, 측정 회원 지갑(잔액 1,000만 원) |
| `order-pay.js` | k6 시나리오 (흐름 `MODE=flow`, 동시 결제 `MODE=burst`) |
| `check.sql` | 정확성 확인 C1~C5 |
| `run.sh` | 한 바퀴: 빈 DB로 띄우기 → 데이터 → 시나리오 → 정확성 → 지표 받기 → 내리기 |
| `report.py` | Prometheus 구간 지표 받기, 결과 표·그래프 만들기 |
| `.github/workflows/order-load-test.yml` | GitHub Actions에서 A·B를 번갈아 3번 |

## 로컬에서 돌리기

필요한 것: Docker(compose v2, 메모리 8GB 이상), curl, python3(그래프는 matplotlib).

```bash
cd docs/measurements/load
QUICK=1 SCENARIOS="warmup s2 s4-300-flow" OUT=/tmp/orderload ./run.sh   # 확인용 짧게 (지금 코드를 LABEL=local로 빌드)
LABEL=local REP=1 OUT=/tmp/orderload ./run.sh                             # 전체 한 바퀴 (약 25분)
python3 report.py summary /tmp/orderload /tmp/orderload/report            # 표·그래프
```

다른 커밋을 재려면 그 커밋에서 이미지를 만들고 `LABEL`을 맞춰요.

```bash
docker build --build-arg MODULE=order-service -t orderload-order:b <그 커밋의 backend 폴더>   # cash-service, product-service도 같이
LABEL=b REP=1 OUT=/tmp/orderload ./run.sh
```

## GitHub Actions에서 돌리기

- 기본 브랜치에 들어간 뒤: Actions → `order-load-test` → Run workflow. `ref_a`(변경 전), `ref_b`(변경 후), `repeat`를 넣어요.
- 그 전: PR에 `load-test` 라벨을 붙이면 그 PR 커밋을 A로 한 번 재요.
- 결과는 실행 요약(표)과 아티팩트 `order-load-results`(k6 요약 JSON, 지표 JSON, 그래프 PNG, 정확성 SQL 결과, 서비스 로그)에 있어요.

## 다른 모니터링 환경에서 쓰기

- 주문은 `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus` 환경변수를 주면 `/actuator/prometheus`를 열어요. 기본은 health만 열려요.
- 그 환경의 Prometheus에 `prometheus.yml`의 `order` 수집 설정을 더하고, `order-pay.js`를 `BASE`만 바꿔 돌리면 돼요.
- 회원 ID는 `X-Member-Id`로 바로 보내므로 게이트웨이 앞에서는 돌릴 수 없어요(게이트웨이가 헤더를 지워요).
