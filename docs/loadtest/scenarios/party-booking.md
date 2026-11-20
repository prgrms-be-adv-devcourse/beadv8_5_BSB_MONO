# 시나리오: 파티 예매 흐름 (파티장 신청 + 파티원 결제, write)

엑셀 「크루·대회·참가」(파티장)와 「파티원·개별·결제」를 **한 파티 예매 흐름**으로 합친 것.
파티장이 크루에서 파티를 찾아 신청(좌석 한꺼번에 확보)하고, 이어 파티원들이 각자 결제한다.
개인 예매처럼 **화면별 부수 조회 + 화면 전환 think-time**을 반영한다. 공통 틀은 `../main-scenario/guide.md` B~G.

> 두 역할(파티장·파티원)이라 k6는 **exec 두 개**(`applyLeader`, `payMember`)를 한 스크립트에 둔다
> (funnel의 enter/poll/buy처럼). 파티장 신청이 끝나야 파티원이 결제하므로, 시드로 상태를 맞춰 **각각 따로** 돌리거나
> payMember를 applyLeader보다 늦게 시작한다.

## 화면 → 호출 (엑셀 매핑, think-time은 화면 전환마다)

### 파티장 (applyLeader)
1. **크루 목록**(`/crews`): `GET /api/v1/member/crews/me` → `crewId`
2. **크루 홈**(`/crews/{crewId}`): `GET /api/v1/member/crews/{crewId}` + `GET /api/v1/order/parties/crews/{crewId}/active` + `GET /api/v1/product/races/{raceId}`(파티 카드마다)
3. **파티**(`/parties/{partyId}`, 모집 중): `GET /api/v1/order/parties/{partyId}` → `partyId`
4. **파티 신청**(`/parties/{partyId}/apply`): `POST /api/v1/queue/parties`(바디 `{raceId,raceCourseId}`, 줄에 있으면 409) +
   폴링 `GET /api/v1/queue/parties/races/{raceId}/racecourses/{raceCourseId}`(3초) +
   `POST /api/v1/order/parties/{partyId}/apply`(본문 없음, 좌석 한꺼번에 확보, 모자라면 **409 ORDER_PARTY_CREW_SEAT_SHORTAGE**) +
   `GET /api/v1/order/parties/{partyId}`(신청 뒤 상태)

### 파티원 (payMember)
1. **크루 목록·크루 홈**: 파티장 1~2와 동일(crews/me → crews/{crewId} → parties/crews/{crewId}/active → races/{raceId})
2. **파티**(결제 진행 중): `GET /api/v1/order/parties/{partyId}` + `GET /api/v1/order/parties/me/race-status?raceId=`(결제할 `participationId`) + `GET /api/v1/member/members/me`(처음 1회)
3. **주문**: `POST /api/v1/order/orders`(바디 `{participationId}`, 헤더 Idempotency-Key, **좌석은 파티 때 확보돼 409 없음**, 201) → `orderId`
4. **결제**(`/checkout`): `GET /api/v1/order/orders/{orderId}` + `GET /api/v1/cash/wallets/balance` + `GET /api/v1/order/participations/{participationId}` + (주소 직접 열면 races/file/admission) + `POST /api/v1/order/orders/{orderId}/payment`(200 / 503 BUSY / 202 PAYING)
5. **결제 완료**: `GET orders/{orderId}` + `wallets/balance` + `participations/{participationId}` + `admission`

## H절

- **부하 축**: [ ] 조회 [x] **쓰기(write, TPS)** — 두 완결 거래를 **나눠 보고**: 파티 신청/s(TPS, applyLeader)와 결제/s(TPS, payMember).
  좌석 경쟁은 **신청(apply)** 한 곳, 결제 몰림은 **payMember** 한 곳. 개인 예매와 달리 둘이 분리돼 있어 병목을 떼어 볼 수 있다.

- **필요 시드**:
  - 회원: 파티장 N명 + 파티원 M명 (C 공통), 파티원 **예치금 충분**.
  - **예치금 사전 시드(결정)**: 결제는 **예치금 결제로 한정**하고 Toss 충전은 측정에서 뺀다(외부 PG 지연이 섞임).
    잔액은 **DB로 미리 넣는다** — 부하 전용 회원의 `cash_wallet.balance`를 큰 값으로 UPDATE.
    **지갑 ID = 회원 ID**, `cash_member`·`cash_wallet` 행이 **이미 있어야** 한다(없으면 404). 원장(`cash_wallet_ledger`)은
    어긋나지만 결제 동작엔 영향 없음(`CashCompleteOrderPaymentUseCase.java:66-75`, `CashSupport.java:29,62-65`). **부하 전용 회원·환경에서만.**
    대안: team05 cash-service가 `cash.clients.pg.mode=fake`면 충전 API(FakePaymentClient)로도 채울 수 있음 — 단 운영(prod)은 http라
    team05 현재 값은 **미확인**. (사전 시드든 fake 전환이든 **운영 서버 변경 → team05 스킬 절차 + 사용자 확인 필요.**)
  - 전용 데이터: 크루 N개 + 각 크루에 모집 중 파티(파티장 소유). payMember를 돌리려면 파티가 **「결제 진행 중」**(apply 완료)이고 파티원마다 **결제 대기 신청서**가 있어야 함. 좌석 경쟁을 보려면 여러 파티를 같은 종목에 몰고 정원을 좁힌다.
  - 소비성 여부: **있음.** apply는 좌석을, 결제는 신청서를 1회 소비 → 반복 run마다 새 파티·새 신청서. 좌석/신청서 소진 뒤는 409·"이미 결제됨"뿐이라 측정 무의미.

- **결제 몰림 모델(코드 사실 반영)**: 결제를 24시간에 고르게 퍼뜨린다고 **가정하지 않는다.**
  - 신청 직후 파티원 전원에게 **결제 시작 알림이 동시에** 나가고(`PartyApplyUseCase.java:247-251`), 마감 3시간 전 한 번 더 감(`Party.java:51`).
  - 결제 기한 = **min(신청+24h, 크루 마감일 다음날 0시, 접수 마감)**(`PartyApplyUseCase.java:270-282`).
  - → 현실 모델은 **"신청 직후 수 분 안에 파티원 전원이 몰려 결제"**(burst). payMember 단계를 이 burst에 맞춰 짧고 급하게 올린다.

- **인증**: Bearer 토큰(D). applyLeader는 파티장 계정, payMember는 파티원 계정. 선행: 파티장은 파티 대기열 입장+폴링, 파티원은 race-status로 participationId 확보. 파티가 **미리 시드**돼 있어야 함(엑셀 전제).

- **판정 기준(재기 전)**: 조회 p95 **300ms** / 대기열 p95 **200ms** / 쓰기 p95 **1s** / 실패율 **1%** /
  정상거절·흐름 코드: **409 ORDER_PARTY_CREW_SEAT_SHORTAGE**(좌석 소진), **409**(대기열 중복), **503 ORDER_PAYMENT_BUSY**(결제 몰림), **202 PAYING**. 파티원 주문의 좌석 409는 **기대 안 함**.

- **단계(목표 TPS)**:
  - applyLeader: smoke **1**(30s) → load **3**(2m) → stress **0→10**(2m). apply가 좌석 다건 트랜잭션이라 보수적.
  - payMember: smoke **1** → load **3** → stress **0→15**(funnel buystress 급). 결제 붕괴점 확인.
    위 burst 모델대로 **"한 파티 전원이 신청 직후 수 분 내 몰리는"** 형태(짧은 램프업·높은 순간 TPS)로 재는 걸 권장.
  - 각 단계 `maxVUs` 넉넉히(think-time·폴링으로 iteration이 길다, guide G).

- **예상 병목 가설 + 볼 지표**:
  - 신청(apply): **상품 서비스 좌석 확보 경쟁**(같은 종목 행 잠금), **order→product hikari**. 상품 p95·DB 락, `hikaricp_connections_pending`(order·product).
    파티 신청 1건은 크루 자리 전체를 상품 서비스에 **한 번에 요청(holdAll)하는 외부 호출**이라, 신청 단계 TPS가 따로 병목일 수 있음.
  - 결제(pay): **cash 예치금 동시성**(503 BUSY, `OrderPaySplitTest`), **order→cash hikari**. cash p95·503 비율, `hikaricp_connections_pending`(order·cash).
    **핵심 관찰 포인트**: 모든 결제가 **보관 지갑(holdingMemberId) 한 줄을 비관적 락**으로 잠근다(`CashSupport.java:81-84`) →
    결제 TPS가 이 한 줄에서 **직렬화**될 가능성. 결제 지연·cash-service DB 락 대기를 함께 볼 것(상한 여부는 실측 필요, 미확인).
    결제 본체는 개인 결제와 같은 `OrderPayUseCase.pay`를 타므로 **개인 시나리오와 직접 비교 가능**.
  - 공통: **노드 CPU(2코어)**(F-1), 게이트웨이 10초 vs DB 대기 불일치(F-3). 에러율만 보지 말 것(느린 붕괴).

- **정리**(만든 역순, 넣기 전 작성 — 스키마·컬럼 초안):
  - `DELETE FROM crewrun_order.payment/orders WHERE ...;` + 파티 상태 되돌리기/좌석 리셋 + 예치금 원복(`cash_wallet`·ledger).
  - 확인 SQL: 좌석 **정원 초과·이중 확보 없음**(성공 apply × 파티원 수 == 줄어든 좌석), 예치금 **음수·이중 차감 없음**, 성공 결제 수 == PAID 주문 수.

## 비고

- 응답 `data.*` 키(crews `0.id`, active `0.id`, parties `partyId`·`participationId`, orders `id`, payment `data.status`)는 초안 js 가정 — 실측 전 실제 응답으로 확인.
- 폴링 간격은 프론트(대기 화면 3초마다 `GET /api/v1/queue/races/{r}/racecourses/{c}`, `use-queue-position.ts:64-69`)와 맞춘다 — 초안 js는 3초(일치).
- applyLeader와 payMember는 상태 의존(신청→결제)이라, 깨끗이 재려면 **파티를 「결제 진행 중」으로 시드해 두고 payMember만** 돌리거나, 두 exec을 시간차(startTime)로 둔다. 초안 js는 ENV `ROLE`로 한 번에 하나만 돌린다.
