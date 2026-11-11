# 결제·예치금 (cash-service)

## 정책

### 결제

1. 결제는 예치금으로만 한다.
2. 결제 전에 가격·판매 상태·재고·신청 기한을 다시 확인한다. 기한이 지난 상품은 결제할 수 없다.
3. 한 주문은 결제를 한 번에 하나만 진행하고, 같은 요청이 여러 번 와도 한 번만 처리한다(멱등성).
4. 결제 기한은 개인 10분(임시 확보부터)이다. 결제 실패·결제 기한 초과 시 임시 확보한 정원·재고를 해제한다.
5. 예치금이 부족하면 결제 기한 안에서는 자리를 유지한 채 결제 화면에서 바로 충전하고 이어서 결제를 마친다. 부족한 금액이 충전 최소 금액(1만 원)보다 적으면 1만 원을 충전한다. 충전은 됐는데 결제가 실패하면 충전한 금액은 예치금으로 남는다.
6. ~~개인이 충전하려고 PG사 결제창에 들어가면 결제 기한을 2분 늘려 준다. 그래도 기한 안에 결제를 마치지 못하면 자리를 해제한다.~~
   > 결제 하지 못한 고객 잘못 시간만료로 결제 실패로 해도 될듯

### 예치금

1. 예치금은 현금성 잔액(선불 충전금)으로 관리하고 충전·결제·환불·환급 내역을 기록한다. 충전 최소 금액은 1만 원.
2. PG사로 충전된 돈은 플랫폼 계좌 한 곳(예치금 보관 통장)에 모인 것으로 보고, 회원별 잔액은 서비스 장부로 관리한다.
3. 예치금 전액은 플랫폼 운영 자금과 나눠 관리한다(전자금융거래법: 신탁·예치·지급보증보험). 1차에서는 장부에서 운영 자금과 나눠 기록하는 데까지만 구현한다.
4. 회원은 남은 예치금 전부를 언제든 돌려받을 수 있다(5장).
5. 포인트는 별도 정책이 정해지기 전까지 예치금과 섞지 않는다.

### 환불 기준

1. 판매자는 대회를 등록할 때 환불 기한(날짜)을 정한다. 환불 기한까지 취소하면 전액 환불한다.
   > 예: 대회 9/25, 접수 5/10~5/20, 환불 기한 7/15
2. 환불 기한이 지나면 개인 사정으로는 환불할 수 없다. 대회 취소·일정 변경(4번)일 때만 환불한다. 환불 기한과 그 뒤 환불 불가를 결제 전에 보여 준다.
3. 기간 안의 환불 신청은 자동 승인한다.
4. 환불 기한이 지나면 판매자 1차 정산 대상이 된다(6장).
5. 참가권 환불은 구매자의 예치금으로 돌려주고 판매 가능 정원을 복구한다.

### 예치금 환급 (현금 환불)

1. 회원은 남은 예치금 전부의 환급을 언제든 신청할 수 있다.
2. 2차부터는 충전 건별로 PG 결제취소 기한(승인일 + 1년 − 1일) 안의 몫은 카드로 결제취소하고, 기한이 지난 몫과 충전으로 들어오지 않은 잔액(판매자 정산 대금)은 계좌로 송금한다.
   계좌 송금은 실제 송금 연동 없이 신청 즉시 완료로 처리한다. 자세한 규칙은 `docs/policy/05-취소환불.md` 「2차 · 1. 예치금 환급 › PG 결제취소와 기한」이 원본이다.

---

## ERD

엔티티(`backend/cash-service`의 `com.bukang.cash.domain`) 기준이다. 실제 테이블 이름은 `CASH_` 접두사를 붙인다(예: `CASH_WALLET`).

- **공통 컬럼:** 모든 테이블은 `BaseIdAndTime`을 상속해 `id`(BIGINT, AUTO_INCREMENT, PK), `create_date`, `modify_date`(DATETIME(6))를 가진다.
  ERD 초안의 `*_id` PK와 `created_at`/`updated_at`, 요청·시도·신청 일시(`requested_at`)는 이 컬럼으로 대신한다. `wallet`·`cash_member`·`cash_seller`는 ID를 직접 넣는다(아래 참고).
- **금액:** 금액 컬럼은 모두 BIGINT(`long`)다.
- **ID:** 다른 행을 가리키는 ID 컬럼(FK, `order_id`, `ref_id`, `payout_id`)은 모두 BIGINT(`long`)로, 엔티티 `id` 타입과 맞춘다.
- **ENUM:** 상태·사유·유형처럼 정해진 값만 들어가는 컬럼은 MySQL `ENUM` 타입이다(JPA `@Enumerated(STRING)`).
  값을 추가하면 `ddl-auto: update`가 컬럼을 고치지 않으므로 `ALTER TABLE`로 직접 바꾼다.
- **FK:** cash-service 안의 테이블(`cash_member`, `cash_wallet`)에만 건다. 다른 서비스의 테이블(주문 등)은 FK 없이 ID 값으로만 보관한다.

### wallet 예치금 지갑 (`CASH_WALLET`)

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 지갑 ID = 지갑 주인의 회원 ID (`BaseManualIdAndTime`, 자동 생성 안 함). PK라서 회원당 지갑 하나가 보장된다 |
| FK | holder_id | BIGINT | NOT NULL | → cash_member. 지갑 주인 |
| | balance | BIGINT | NOT NULL | 잔액 (생성 시 0) |
| | version | BIGINT | NOT NULL | 낙관적 락 (`@Version`) |
| | create_date / modify_date | DATETIME(6) | | 생성·수정 일시 |

지갑은 회원 가입 이벤트(`MemberJoinedEvent`)를 받을 때 `cash_member`를 저장한 뒤 함께 만든다.

- **중복·동시 생성:** 같은 이벤트가 다시 오면 이미 있는 지갑을 돌려준다. 동시에 두 번 만들려 하면 PK가 두 번째 insert를 막고,
  Kafka 재시도에서 다시 처리될 때 이미 있는 지갑을 찾아 끝난다.
- **지갑이 없을 때:** 조회 시점에 새로 만들지 않고 `WalletNotFoundException`(404, "존재하지 않는 지갑입니다.")으로 알린다.
  지갑이 없다는 건 회원 동기화가 아직 안 됐거나 실패했다는 뜻이라, 몰래 만들면 그 문제가 드러나지 않는다.
  가입 직후 이벤트가 처리되기 전에 조회하면 잠깐 404가 날 수 있다.

#### 홀딩 지갑 (1차)

- 1번 system: 플랫폼 수수료 수익용
- 2번 holding: 결제 후 판매자 정산 전 돈 보관
- 지갑 ID가 회원 ID이므로, 회원 ID 1·2번이 시스템 회원이어야 한다.
- 시스템 회원은 로그인을 막고 회원목록, 통계에서 제외
- 최종프로젝트: wallet_type(MEMBER / HOLDING / SYSTEM)을 도입해 시스템 지갑을 ID 대신 종류로 찾게 바꾼다.

### wallet_charge 예치금 충전 (`CASH_WALLET_CHARGE`)

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 충전 ID |
| FK | wallet_id | BIGINT | NOT NULL | → cash_wallet |
| | amount | BIGINT | NOT NULL | 충전 금액 (≥ 10,000) |
| | pg_provider | VARCHAR(30) | NOT NULL | PG사 (예: TOSS). enum이 아니라 문자열로 저장한다 |
| | pg_payment_key | VARCHAR(512) | NULL | PG 결제 키 (AES 암호문이라 평문보다 길다). PG 승인 전에는 비어 있다 |
| | idempotency_key | VARCHAR(64) | NOT NULL, UNIQUE | 중복 충전 방지 |
| | status | ENUM | NOT NULL | REQUESTED / IN_PROGRESS / COMPLETED / FAILED / CANCELED / UNCERTAIN (생성 시 REQUESTED). IN_PROGRESS는 PG 승인 요청 중, UNCERTAIN은 승인 결과를 끝내 확인하지 못해 재확인 배치가 맞추는 중 |
| | charge_date | DATETIME(6) | NULL | 충전 완료를 서버에 기록한 일시 |
| | approved_at | DATETIME(6) | NULL | PG가 승인한 일시(한국 시간). PG 결제취소 기한의 기준. 없으면 charge_date로 대신한다 |
| | canceled_amount | BIGINT | NOT NULL | PG 결제취소로 이미 돌려준 금액 (기본 0). 부분 취소를 여러 번 하면 더해진다 |
| | create_date / modify_date | DATETIME(6) | | 요청 일시 / 수정 일시 |

### payment 결제 (`CASH_PAYMENT`)

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 결제 ID |
| | order_id | BIGINT | NOT NULL, UNIQUE | 주문 ID. 다른 서비스의 테이블이라 FK 없이 값으로만 보관한다. 한 주문에 결제 하나라 중복 결제 방지 키를 겸한다 |
| FK | wallet_id | BIGINT | NOT NULL | → cash_wallet |
| | amount | BIGINT | NOT NULL | 결제 금액 |
| | status | ENUM | NOT NULL | PENDING / COMPLETED / FAILED / TIMED_OUT / REFUNDED / CANCELED (생성 시 PENDING). CANCELED는 고객 취소가 아니라 시스템 환원 |
| | fail_reason | ENUM | NULL | INSUFFICIENT_BALANCE / PRICE_CHANGED / OUT_OF_STOCK / NOT_ON_SALE / DEADLINE_PASSED. FAILED일 때만 채움 |
| | complete_date | DATETIME(6) | NULL | 완료 일시 |
| | create_date / modify_date | DATETIME(6) | | 시도 일시 / 수정 일시 |

### wallet_ledger 예치금 장부 (`CASH_WALLET_LEDGER`)

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 장부 ID |
| FK | wallet_id | BIGINT | NOT NULL | → cash_wallet |
| FK | holder_id | BIGINT | NOT NULL | → cash_member. 지갑 주인 |
| | ledger_type | ENUM | NOT NULL | 아래 "ledger_type 값" 참고 |
| | amount | BIGINT | NOT NULL | 변동 금액 (+/−) |
| | balance_after | BIGINT | NOT NULL | 변동 후 잔액 |
| | ref_type_code | ENUM | NOT NULL | WalletCharge / Order / Withdrawal / Payout / RecoveryDeposit. 돈의 이동을 일으킨 원인 객체. 한 이동의 두 줄(고객·홀딩)은 같은 ref를 가리킴. 충전은 CashMember 대신 WalletCharge(PG 거래 추적) |
| | ref_id | BIGINT | NOT NULL | 근거 ID (WalletCharge·WalletWithdrawal의 id, 주문 id) |
| | create_date / modify_date | DATETIME(6) | | 기록 일시 / 수정 일시 |

ref_type_code는 DTO에서 문자열로 받더라도 `LedgerRefType.valueOf(...)`로 바꿔 저장한다. 목록에 없는 값이면 예외가 나서 저장되지 않는다.

#### ledger_type 값

- ~~충전__무통장입금~~
- 충전__PG결제_토스페이먼츠
- 출금__통장입금
- 사용__주문결제
- 임시보관__주문결제
- 정산지급__상품판매_수수료
- 정산수령__상품판매_수수료
- 정산지급__상품판매_대금
- 정산수령__상품판매_대금 (2차부터 쓰지 않음, 대금은 은행으로 판매자 계좌에 송금)
- 정산지급취소__상품판매_대금 (2차, BUK-83)
- 환불__주문취소
- 임시보관반환__주문취소
- 출금취소__통장입금
- 출금__PG결제취소_토스페이먼츠 (2차, BUK-22)
- 출금취소__PG결제취소_토스페이먼츠 (2차, BUK-22)
- 회수입금__가상계좌_토스페이먼츠 (2차, BUK-146)

#### 장부 기록 규칙 (10/8)

돈이 고객 지갑과 홀딩 지갑 사이에서 움직이면 같은 ref_type_code·ref_id로 두 줄을 한 트랜잭션에서 남긴다(두 줄 amount 합 = 0). 충전·환급은 돈이 플랫폼 밖과 오가는 거라 고객 지갑만 기록한다.
판매자가 갚는 회수금도 플랫폼 밖에서 들어오는 돈이라 홀딩 지갑 한 줄만 남긴다.
정산 대금도 은행으로 판매자 계좌에 송금해 플랫폼 밖으로 나가는 돈이라 홀딩 지갑 한 줄만 남긴다(BUK-83).

| 상황 | 고객 지갑 | 홀딩 지갑 (2번) | ref_type_code |
| --- | --- | --- | --- |
| 충전 | (+) 충전__PG결제_토스페이먼츠 | - | WalletCharge |
| 결제 | (−) 사용__주문결제 | (+) 임시보관__주문결제 | Order |
| 환불 | (+) 환불__주문취소 | (−) 임시보관반환__주문취소 | Order |
| 환급 신청 | (−) 출금__통장입금 | - | Withdrawal |
| 환급 실패 | (+) 출금취소__통장입금 | - | Withdrawal |
| 환급 신청 · 카드 몫 (2차) | (−) 출금__PG결제취소_토스페이먼츠 (충전 건마다 한 줄) | - | Withdrawal |
| 환급 실패 · 카드 몫 (2차) | (+) 출금취소__PG결제취소_토스페이먼츠 (되돌린 몫마다 한 줄) | - | Withdrawal |
| 정산 대금 (2차) | - (판매자 지갑에 넣지 않고 은행으로 판매자 계좌에 송금) | (−) 정산지급__상품판매_대금 (송금 시도마다 한 줄) | Payout |
| 정산 대금 송금 실패 (2차) | - | (+) 정산지급취소__상품판매_대금 (실패한 시도마다 한 줄) | Payout |
| 정산 수수료 (2차) | (+) 정산수령__상품판매_수수료 (1번 system 지갑) | (−) 정산지급__상품판매_수수료 | Payout |
| 회수 입금 (2차) | - | (+) 회수입금__가상계좌_토스페이먼츠 (청구 하나에 한 줄) | RecoveryDeposit |

결제 화면에서 부족분을 충전한 경우도 WalletCharge로 통일한다. 정산 수수료는 홀딩 지갑(−)과 시스템 지갑(+) 두 줄을, 정산 대금은 홀딩 지갑 한 줄을 남긴다. 대금과 수수료는 각각 별도 payout_transfer 행이라 ref_id가 다르다(2차).
송금 결과를 모르면 대금을 되돌리지 않는다. 은행이 이미 보냈을 수 있어서다. 재확인이 실패로 확정할 때만 정산지급취소 줄을 남긴다.

### wallet_withdrawal 예치금 환급 (`CASH_WALLET_WITHDRAWAL`)

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 환급 ID (10/8: cash_out → wallet_withdrawal) |
| FK | wallet_id | BIGINT | NOT NULL | → cash_wallet |
| | amount | BIGINT | NOT NULL | 환급 금액 (신청 시점 잔액 전부) |
| | bank_code | VARCHAR(10) | NULL | 은행 코드. 계좌로 보낼 몫이 있을 때만 받는다 (카드로만 돌려주면 비어 있다) |
| | account_number | VARCHAR(512) | NULL | 계좌번호 (AES 암호문) |
| | account_holder | VARCHAR(50) | NULL | 예금주 |
| | status | ENUM | NOT NULL | REQUESTED / IN_PROGRESS / COMPLETED / FAILED (생성 시 REQUESTED). IN_PROGRESS는 PG 결제취소 결과를 기다리는 몫이 남아 있음 |
| | fail_reason | ENUM | NULL | INVALID_ACCOUNT. FAILED일 때만 채움 (사유가 정해지면 추가) |
| | idempotency_key | VARCHAR(64) | NOT NULL, UNIQUE | 중복 신청 방지 |
| | complete_date | DATETIME(6) | NULL | 완료 일시 |
| | create_date / modify_date | DATETIME(6) | | 신청 일시 / 수정 일시 |

신청 시 잔액을 바로 차감하고 wallet_ledger에 몫마다 기록하며(카드 몫은 충전 건마다, 계좌 몫은 한 줄), 실패한 몫은 잔액을 되돌리는 장부 기록을 남긴다.
한 지갑에 IN_PROGRESS인 환급이 있으면 새 신청을 막는다(409 WITHDRAWAL_IN_PROGRESS).

### cash_member 회원 복제본 (`CASH_MEMBER`)

member-service의 회원을 Kafka 이벤트(`MemberJoinedEvent`)로 복제해 둔다(`ReplicaMember`). 공개 정보만 갖는다.

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 회원 ID (member-service의 ID 그대로, 자동 생성 안 함) |
| | nickname | VARCHAR(255) | | 닉네임 |
| | create_date / modify_date | DATETIME(6) | | 원본 회원의 생성·수정 일시 |

### cash_seller 판매자 (`CASH_SELLER`)

판매자 신청서 ID를 회원과 잇는다. 정산 대금을 송금하기 전에 판매자가 있는지 이 표로 확인한다.

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 판매자 신청서 ID (자동 생성 안 함) |
| FK | member_id | BIGINT | NOT NULL | → cash_member. 판매자 회원 |
| | create_date / modify_date | DATETIME(6) | | 생성·수정 일시 |

### payout_transfer 정산 지급 (`CASH_PAYOUT_TRANSFER`)

정산 한 건의 돈 이동(대금 또는 수수료)을 기록하고, 그 id를 wallet_ledger의 ref_id(Payout)로 쓴다.
대금은 은행 송금이라 송금 상태를 함께 둔다. 수수료는 지갑끼리 옮겨 바로 완료다(BUK-83).

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 지급 ID |
| | payout_id | BIGINT | NOT NULL | payout-service의 정산 ID. 다른 서비스의 행이라 FK 없이 값으로만 보관한다 |
| | seller_id | BIGINT | NOT NULL | 판매자 신청서 ID (추적용) |
| | transfer_type | ENUM | NOT NULL | SETTLEMENT(대금) / FEE(수수료) |
| | amount | BIGINT | NOT NULL | 지급 금액 |
| | status | ENUM | NOT NULL | REQUESTED(홀딩 지갑에서 빼고 결과 반영 전) / UNKNOWN(은행이 보냈는지 모름) / COMPLETED / FAILED. 수수료는 생성 시 COMPLETED |
| | attempt | INT | NOT NULL | 송금 시도 횟수 (1부터). 은행에 보내는 송금 ID는 `payout-{id}-{attempt}` |
| | create_date / modify_date | DATETIME(6) | | 생성·수정 일시 |

(payout_id, transfer_type)에 UNIQUE를 걸어 같은 정산의 같은 이동이 두 번 기록되지 않게 한다.
같은 정산 ID로 다시 요청이 오면: COMPLETED는 그대로 성공, REQUESTED·UNKNOWN은 은행에 다시 보내지 않고 503, FAILED는 같은 행의 attempt를 올려 새 송금 ID로 다시 보낸다.
REQUESTED·UNKNOWN은 매일 03:30 재확인이 은행에 조회해 맞춘다. 은행이 "없음"이라고 답하면 마지막으로 바뀐 지 10분이 지난 건만 FAILED로 확정한다.

### fakebank_transfer 가짜 은행 송금 기록 (`CASH_FAKEBANK_TRANSFER`)

실제 은행 대신 cash-service 안의 가짜 은행(`/fakebank`)이 남기는 송금 기록이다. 진짜 은행처럼 재배포해도 남아야, 재확인이 은행의 "없음"을 "은행에 닿지 않음"으로 믿을 수 있다(BUK-83).
결제 쪽 표와 FK로 잇지 않는다. 결제는 HTTP로만 이 은행을 부른다.

| 키 | 컬럼 | 타입 | 제약 | 설명·값 |
| --- | --- | --- | --- | --- |
| PK | id | BIGINT | | 기록 ID |
| | transfer_id | VARCHAR(100) | NOT NULL, UNIQUE | 결제가 보낸 송금 ID. 같은 ID는 한 번만 송금한다 |
| | seller_id | BIGINT | NOT NULL | 판매자 신청서 ID |
| | amount | BIGINT | NOT NULL | 송금 금액 |
| | status | ENUM | NOT NULL | COMPLETED / FAILED / PROCESSING. 조회했을 때 보이는 최종 결과 |
| | result_mode | ENUM | NOT NULL | SUCCESS / FAIL / PENDING_THEN_SUCCESS / PENDING_THEN_FAIL / DROP. 송금 때의 결과 설정. 같은 ID로 다시 요청하면 처음과 같은 응답을 준다 |
| | create_date / modify_date | DATETIME(6) | | 생성·수정 일시 |
