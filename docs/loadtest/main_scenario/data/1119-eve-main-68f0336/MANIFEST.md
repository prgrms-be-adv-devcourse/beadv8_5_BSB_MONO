# 실행 기록: 1119-eve-main-68f0336 (기준선 재측정, 조건 정리판)

## 대상
- 서버: team05 (2 vCPU·7.6GB, k3s), https://crewrun.duckdns.org
- **main 커밋: 68f033688ed5b8e9af71ec665fe6f2532802895c**. 이미지 구성은 오후·실제 흐름 측정과 같다(deploy-state.txt): order·web 68f0336 / member·product·board·inquiry·payout 4ff7581 / gateway·queue·cash·file·notification·ai 36b083f
- nginx: 측정 동안 부하 PC IP만 `/auth` 로그인 제한에서 뺐다(19:45경 재시작). 측정 뒤 되돌렸다(20:06경).

## 시나리오: funnel.js (기준선: 대기자·구매자 분리, 순번 조회를 일정하게 유지)
- 단계마다 새 대회 2개(구매용·대기용). 부하 모양은 오후 실행(1119-pm-main-68f0336)과 같다.
- 계정: loadtest1~1000, 로그인 19:45:51~19:50:53 KST (간격 0.2초)

## 실행 (UTC) — 단계 사이 60초·90초 쉼
| 단계 | 시작 | 끝 | 구매 대회/종목 | 대기 대회/종목 | k6 종료 | Grafana testid |
|---|---|---|---|---|---|---|
| s1 (조회 100/s, 구매 2/s) | 10:51:11 | 10:54:44 | 80 / 200 | 81 / 201 | 0 (기준 통과) | 1119-eve-main-68f0336-s1 |
| s2 (조회 200/s, 구매 3/s) | 10:56:06 | 10:59:40 | 82 / 202 | 83 / 203 | 99 (기준 초과) | 1119-eve-main-68f0336-s2 |
| s3 (조회 330/s, 구매 2/s) | 11:01:29 | 11:05:03 | 84 / 204 | 85 / 205 | 99 (기준 초과) | 1119-eve-main-68f0336-s3 |

## 오후 실행(1119-pm-main-68f0336)과 다른 조건
1. 단계 사이에 쉬었다(오후는 쉬는 명령이 실패해 연속 실행).
2. 로그인을 5분에 끝내 토큰 만료가 없다(오후 s3는 401 543건).
3. k6 지표를 수집 서버로도 보냈다(Grafana `k6 Prometheus` 대시보드, testid로 조회). 발생기 부담이 조금 늘 수 있다.
4. CPU는 15초 단위(node_cpu_pct), 오후와 비교할 때는 node_cpu_pct_1m을 쓴다.
5. 같은 날 앞선 측정들로 DB에 주문·참가 행이 더 쌓여 있다.

## 파일
`<stage>.json` 요약 / `<stage>-raw.csv` 요청별 원시 / `<stage>.tps.json` / `<stage>.prom.json` / `<stage>.window.txt` / `deploy-state.txt`
