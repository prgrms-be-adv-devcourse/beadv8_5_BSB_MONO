# 실행 기록: 1119-pm-main-68f0336

재현용 기록. 이 폴더의 모든 파일은 아래 조건에서 나왔다.

## 대상
- 서버: team05 (AWS t3.large, 2 vCPU·7.6GB, k3s), https://crewrun.duckdns.org
- **main 커밋: 68f033688ed5b8e9af71ec665fe6f2532802895c** (k8s-cd 배포 완료 2026-11-19 14:35 KST)
- 서비스별 이미지 태그 (측정 시작 16:26 KST 시점 `kubectl get deploy`):
  | 서비스 | 이미지 커밋 | 파드 나이 |
  |---|---|---|
  | order-service, web | 68f0336 | 112분 |
  | member, product, board, inquiry, payout | 4ff7581 | 5시간 30분대 |
  | gateway, queue, cash, file, notification, ai | 36b083f | gateway 5시간 39분, 나머지 22시간 |
  | nginx | nginx:1.28-alpine (설정은 main의 keep-alive 포함) | 87분 |
- 주의: nginx 파드가 약 15:00 KST에 재기동돼 있었다. 같은 날 파티 부하 테스트에서 `/auth` 제한을 풀었다 되돌린 것(nginx-open.sh·nginx-close.sh)으로 추정한다. 설정은 원래대로다.
- 4ff7581에 들어간 성능 변경: `da4de0036 perf(nginx): 게이트웨이 연결을 keep-alive로 다시 쓰기`

## 부하 발생기
- 미니 PC, k6 v0.54.0, `C:\Users\user\loadtest\prod-run\`
- 스크립트: 레포 `docs/loadtest/main_scenario/k6/` (funnel.js, prepare-race.js, run-stage.ps1) — 브랜치 `docs/loadtest-main-scenario`
- **이전 실행과 달라진 점:** 단계마다 새 대회 2개(구매용·대기용)를 API로 만들어 쓴다. funnel.js는 신청 409를 실패로 센다. 부하 모양(도착률·시간·계정 범위)은 그대로다.
- 계정: loadtest1~1000 (1000번은 판매자), 로그인 16:07~16:27 KST (s1~s3), 16:38~16:57 KST (buystress)

## 실행 (UTC)
| 단계 | 시작 | 끝 | 구매 대회/종목 | 대기 대회/종목 | k6 종료 |
|---|---|---|---|---|---|
| s1 (조회 100/s, 구매 2/s) | 07:26:43 | 07:30:17 | 60 / 180 | 61 / 181 | 0 (기준 통과) |
| s2 (조회 200/s, 구매 3/s) | 07:30:18 | 07:33:52 | 62 / 182 | 63 / 183 | 99 (기준 초과) |
| s3 (조회 330/s, 구매 2/s) | 07:33:54 | 07:37:28 | 64 / 184 | 65 / 185 | 99 (기준 초과, 중단 없이 완주) |
| buystress (조회 133/s, 구매 도착 15/s) | 07:57:54 | 08:00:48 | 66 / 186 | 67 / 187 | 99 (기준 초과, 중단 없이 완주) |

## 알려진 한계 (비교할 때 주의)
- **단계 사이 쉬는 시간이 없었다.** 60초·90초 쉬게 했으나 SSH에서 `timeout` 명령이 실패해 s1→s2→s3가 연속으로 돌았다(앞 단계 여파가 남았을 수 있음).
- **s3 끝부분의 401 543건은 토큰 만료다.** 로그인에 20분이 걸려 먼저 로그인한 계정의 토큰(30분 수명)이 16:37에 만료됐다. 서버 문제가 아니다.
- 이전 실행(11/18, 11/19 오전)과는 커밋·대회·쉬는 시간이 다르다.

## 파일
- `<stage>.json` k6 요약, `<stage>-raw.csv` 요청별 원시 데이터, `<stage>.log` 콘솔 로그
- `<stage>.window.txt` 시작·끝 시각과 대회 ID, `<stage>.race.json` 만든 대회
- `<stage>.tps.json` 원시 CSV에서 계산한 초당 RPS·TPS와 엔드포인트별 p50·p95·p99 (`analyze.py`)
- `<stage>.prom.json` 실행 시간창의 Prometheus 시계열, 15초 간격 (`promrange.py`): 노드 CPU·메모리·스왑·load, 파드별 CPU·메모리, hikari 대기·활성, 힙, GC, 스레드, 서비스별 서버 RPS·5xx·p95
- `deploy-state.txt` 측정 시작 시점 배포 상태 원문
