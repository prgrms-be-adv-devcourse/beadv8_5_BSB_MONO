# 정산 E2E 시나리오 (PRO-62)

주문 서비스에서 결제한 참가권이 정산 생성 배치를 거쳐 정산 내역·정산 항목이 되고, 지급 배치에서 지급 완료가 되기까지를
실제 앱 두 개(주문 8084, 정산 8082)를 띄워 API로 확인한다. 스크립트는 [`payout-e2e.mjs`](payout-e2e.mjs)다.

단위·통합 테스트(`PayoutCreateUseCaseTest`, `OrderSoldEntryAdapterTest` 등)는 서비스 하나 안에서 확인하고,
이 시나리오는 두 서비스 사이의 계약(`GET /internal/v1/order/sold-entries?raceId=`)이 실제로 맞물리는지 본다.

## 준비

```bash
./gradlew :order-service:bootRun    # 8084, dev 프로파일
./gradlew :payout-service:bootRun   # 8082, dev 프로파일 (dev 배치 실행 API가 dev에서만 열린다)
node scripts/payout-e2e.mjs         # backend/에서 실행. Node 18 이상 (fetch 내장)
```

- MySQL 컨테이너 `bukang-db`가 떠 있어야 한다. 스크립트가 `docker exec`로 대회일을 고친다 (아래 "DB를 직접 고치는 단계").
- 주소나 컨테이너 이름이 다르면 `ORDER_URL`, `PAYOUT_URL`, `DB_CONTAINER` 환경 변수로 바꾼다.
- 실패한 검사가 있으면 종료 코드 1로 끝난다.

## 날짜 잡는 법

정산 생성 배치는 오늘 실행하면 **어제부터 7일치**(오늘-7 ~ 오늘-1)에 열린 대회를 주문 서비스에 묻는다 (PRO-61).
스크립트는 실행한 날(한국 날짜)을 오늘로 보고 대회일을 그 앞으로 잡는다. 배치의 `today`도 실제 오늘을 넘기므로,
정산 시각 · 보류 시각 · 환불 판단에 쓰이는 실제 시계와 배치 날짜가 어긋나지 않는다.

예: 2026-10-13에 실행

| 대회 | 대회일 | 정산 대상일 | 정산 월 | 지급 예정일 | 넣는 데이터 |
|---|---|---|---|---|---|
| A | 10/12 (어제) | 10/13 (오늘) | 2026-10 | 11/20 | 결제 2장, 결제 전 1장, 결제 후 취소 1장 |
| C | 10/06 (범위 첫날) | 10/07 | 2026-10 | 11/20 | 결제 1장 |
| B | 10/05 (범위 밖) | - | - | - | 결제 1장 |

- 지급 예정일은 정산 월(1차는 환불 마감일의 달, 최종은 대회일의 달)의 다음 달 20일(주말이어도 그날)이다. 환불 마감일은 스크립트에 없으므로 응답의 정산 월로 계산해 검사한다. 보류 해제 뒤 지급 예정일은 max(원래 지급 예정일, 해제일 + 1일)로 검사한다.
- 대회 · 종목 ID는 실행마다 새로 골라, dev DB에 이미 있는 주문 · 정산과 섞이지 않게 한다.
- 판매자는 1번, 참가권은 한 장 60,000원(참가비 50,000 + 키트 10,000)이다. 주문 서비스의 `FakeRaceCourseAdapter` 값이다.

## DB를 직접 고치는 단계

`FakeRaceCourseAdapter`는 대회일을 늘 "지금 + 60일"로 준다. 지난 대회를 API로 주문할 방법이 없어서,
참가 신청 → 주문 → 결제(→ 취소)를 API로 마친 뒤 그 주문 상품의 `order_order_item.race_date`만 과거로 옮긴다.
"결제하고 시간이 지나 대회가 열렸다"는 상황을 흉내 내는 것이고, 나머지 단계는 모두 API로 부른다.
상품 서비스가 연결되면 이 단계는 지난 대회를 가진 종목으로 바꿀 수 있다.

## 단계와 기대 결과

| # | 단계 | 호출 | 기대 결과 |
|---|---|---|---|
| 1 | 데이터 만들기 | 주문 `POST /participations`, `/orders`, `/orders/{id}/payment`, `/items/{id}/cancel` | A 4건, C 1건, B 1건 주문 |
| 2 | 판매 내역 | 주문 `GET /internal/v1/order/sold-entries?raceId=A`, `raceId=C` | A의 결제 2장과 C만 나온다. 결제 전 · 취소 · B는 빠진다 |
| 2-1 | 판매 내역 입력 검증 | 대회 ID 없음 | 400 |
| 3 | 정산 생성 배치 | 정산 `POST /api/v1/payout/dev/batches/create?today=오늘` | 범위 오늘-7 ~ 오늘-1, A · C 생성, 실패 0. 다시 돌리면 생성 0 (중복 정산 없음) |
| 4 | 판매자 정산 내역 | 정산 `GET /api/v1/payout/seller/payouts` (`X-Member-Id: 1`) | A: 매출 120,000 / 수수료 6,600 / 지급액 113,400, C: 60,000 / 3,300 / 56,700. 둘 다 CALCULATED, B는 없음 |
| 4-1 | 정산 항목 | `GET /seller/payout-items` | 참가권 1장 = 1행. 정산 대상일은 A 오늘, C 대회 다음 날. 결제 전 · 취소분은 없음 |
| 5 | 환불 막기 | 정산 `GET /internal/v1/payout/refund-eligibility`, 주문 취소 | 오늘 대회는 true, 어제 대회는 false. 정산된 참가권 취소는 409 |
| 6 | 지급보류 · 해제 | 정산 `POST /payouts/{id}/holds`, `/holds/{id}/release` (`X-Member-Id: 3`) | 보류 201, 다시 보류 409, 상태 HELD → 해제 200, CALCULATED. 해제 뒤 지급 예정일 = max(원래 지급 예정일, 해제일 + 1일) |
| 7 | 지급 배치 | 정산 `POST /api/v1/payout/dev/batches/pay?today=` | 해제 뒤 · 생성 때 받은 지급 예정일 기준으로, 전날은 그대로 당일에 PAID |

수수료는 참가권마다 5.5%를 원 단위 내림으로 계산해 더한다 (`PayoutItem`).

## 주의

- **dev DB에 데이터가 남는다.** 실행마다 주문 6건, 정산 내역 2건, 지급보류 1건이 늘어난다. 지우지 않는다.
- **지급 배치는 dev DB 전체에 영향을 준다.** 이 스크립트 데이터만이 아니라 지급 예정일이 지난 CALCULATED 정산 내역을 모두
  지급 완료로 바꾼다. `PayoutDataInit` 샘플처럼 남겨 두고 싶은 지급 예정 데이터가 있으면 `--skip-pay`로 돌린다.
- 대회 이름(`FakeRaceAdapter`, "대회 {ID}"), 정산 계좌(`NoSellerAccountAdapter`, 항상 있음), 주문 서비스의 환불 가능 판단
  (`FakeRefundEligibilityAdapter`)은 아직 가짜 어댑터다. 계좌 미등록 자동 보류는 이 시나리오로 확인하지 못한다.
- 게이트웨이(8000)를 거치지 않고 서비스 포트로 직접 부른다. PRO-20 전에는 게이트웨이가 `X-Member-Id`를 지워 401이 된다.
