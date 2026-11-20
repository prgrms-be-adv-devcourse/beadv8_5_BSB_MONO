# 시나리오: 개인 예매 흐름 (funnel + 화면 부수 API, write)

엑셀 「개인·대회·참가하기」 전 화면을, 한 사용자가 처음부터 끝까지 지나가는 **예매 funnel**로 옮긴 것.
`../main_scenario/funnel.js`의 백엔드 흐름(대기열→입장→신청서→주문→결제)은 그대로 두고, **각 화면이 실제로 부르는
부수 조회(상세·이미지·잔액 등)와 화면 전환마다의 사용자 대기(think-time)**를 더해 현실 부하에 가깝게 만든다.
공통 틀은 `../main_scenario/guide.md` B~G.

> `../main_scenario/funnel.js`는 **순수 백엔드 funnel(부수 조회 없음) 기준선**으로 남겨 둔다.
> 이 문서/스크립트는 거기에 **화면 부수 API + think-time**을 입힌 버전이다. 둘을 비교하면 "화면 부수 조회가
> 노드 CPU·조회 부하에 얼마나 더 얹히나"가 보인다.

## 화면 → 호출 (엑셀 매핑, think-time은 화면 전환마다)

1. **대회 목록**(`/races`): `GET /api/v1/product/races?sort=DEADLINE&page=0&size=20` → `raceId`
2. **대회 상세**(`/races/{raceId}`): 한 화면에서 함께 —
   `GET /api/v1/product/races/{raceId}` + `GET .../similar`(검색 서버) + `GET /api/v1/order/parties/me/race-status?raceId=`
3. **신청하기 → 개인으로 신청**(바텀시트): API 없음
4. **대기열**(`/races/{raceId}/queue`): `POST /api/v1/queue`(이미 줄이면 409) + 화면 조회 `GET races/{raceId}` +
   `GET /api/v1/file/files?refType=Race&refId=` + `...refType=RaceCourse&refId=` + **순서 폴링**
   `GET /api/v1/queue/races/{raceId}/racecourses/{raceCourseId}`(3초마다, 줄에 없으면 404→재등록) +
   `GET /api/v1/order/queue/admission?raceId=&raceCourseId=`(입장 확인)
5. **참가 신청서**(`/races/{raceId}/apply`): `GET /api/v1/order/participations/latest-participant` +
   `GET /api/v1/product/races/{raceId}/kit-options` + `POST /api/v1/order/participations`(201) +
   `POST /api/v1/order/orders`(바디 `{participationId}`, 헤더 Idempotency-Key, 좌석 없으면 **409 ORDER_SOLD_OUT**)
6. **결제**(`/checkout`): `GET /api/v1/order/orders/{orderId}` + `GET /api/v1/cash/wallets/balance` +
   `GET /api/v1/order/participations/{participationId}` + (주소 직접 열면 races/file/admission) +
   `POST /api/v1/order/orders/{orderId}/payment`(200 PAID / 503 BUSY / 202 PAYING, 202면 주문 3초마다 재조회)
7. **결제 완료**(`/checkout/complete`): `GET orders/{orderId}` + `wallets/balance` + `participations/{participationId}` + `admission`

## H절

- **부하 축**: [ ] 조회 [x] **쓰기(write, TPS)** — 완결 거래 = **예매 1건/초(TPS)**. 다만 화면 부수 GET이 많아
  총 RPS는 TPS보다 훨씬 크다. 태그를 `write`(신청·주문·결제)·`read`(화면 조회)·`queue`(대기열)로 나눠 따로 본다(guide E).

- **필요 시드**:
  - 회원 N명 (C 공통) + **예치금 충분**(참가비 이상, `cash_wallet.balance`).
  - 전용 데이터: 좌석 남은 공개 대회(상태는 스케줄러가 바꾸게 날짜만 당김). 대회·종목 이미지(file) 있으면 더 현실적.
  - 소비성 여부: **있음.** 신청서·주문·결제는 1회용(좌석 소비) → 단계마다 **새 계정 범위**(funnel의 `buyerFirstNo`식). 폴링용 대기자 계정은 재사용.

- **인증**: Bearer 토큰(D). 선행: 토큰 발급 → 대기열 입장 → 입장 폴링. `/auth/reissue`는 새로고침 때만이라 부하에선 생략(필요하면 별도 태그).

- **판정 기준(재기 전)**: 조회 p95 **300ms** / 대기열 p95 **200ms** / 쓰기 p95 **1s** / 실패율 **1%** /
  정상거절·흐름 코드: **409 ORDER_SOLD_OUT**(매진), **503 ORDER_PAYMENT_BUSY**(몰림, 재시도), **202 PAYING**(비동기, 실패 아님).

- **단계(목표 예매/s = TPS)**: smoke **1**(30s) → load **3**(2m) → stress **0→15**(2m 램프). funnel buy/buystress와 같은 급.
  think-time이 들어가 iteration이 길어지므로 `maxVUs`를 넉넉히(guide G: 안 주면 목표 미달로 측정 무효).

- **예상 병목 가설 + 볼 지표**:
  - 1순위 **노드 CPU(2코어)** — 화면 부수 GET이 더해져 조회 부하가 funnel 기준선보다 커진다(F-1). `node_cpu`.
  - 2순위 **결제 cash 동시성 / order→cash hikari** — 503 비율, `hikaricp_connections_pending`(order·cash)(F-2).
  - 3순위 **`/similar` 검색 서버** + **file 조회** — 외부·이미지 부수 호출. 켜고 끈 run 비교로 기여분 분리.
  - 기준선(main_scenario funnel)과 **같은 단계로 돌려 차이**를 본다(부수 조회의 비용).

- **정리**(만든 역순, 넣기 전 작성 — 스키마·컬럼은 초안):
  - `DELETE FROM crewrun_order.payment/orders/participation WHERE ...;` + 예치금 원복(`cash_wallet`·ledger) + 좌석 리셋.
  - 확인 SQL: 매진 전까지 성공 주문 수 ≤ 정원(초과 판매 없음), 예치금 음수 없음, 이중 차감 없음(Idempotency-Key), 503/202가 PAID로 오집계 안 됨.

## 비고

- 응답 `data.*` 키(목록 `raceId`, participations `id`, orders `id`, payment `data.status`)는 초안 js 가정 — 실측 전 실제 응답으로 확인.
- 부수 조회(file·similar·balance 등)는 ENV 스위치로 끄고 켜 **기준선 funnel과 A/B** 할 수 있게 둔다.
