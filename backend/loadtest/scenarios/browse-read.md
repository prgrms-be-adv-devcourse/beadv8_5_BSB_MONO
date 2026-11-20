# 시나리오: 둘러보기 (대회·크루·파티 조회, read)

엑셀의 읽기 구간(대회 목록 → 상세 → 크루 목록 → 크루 홈 → 파티)을 묶은 **조회 지배** 시나리오.
"2코어 노드가 조회 부하를 어디까지 받나"(guide F-1)를 본다. 공통 틀은 `../main-scenario/guide.md` B~G.

## H절

- **대상 서비스·엔드포인트** (한 iteration이 차례로 호출, 응답에서 다음 호출에 쓸 값 표시):
  - `GET /api/v1/product/races?sort=DEADLINE&page=0&size=20` — 대회 목록 → 다음: 목록에서 `raceId` 하나
  - `GET /api/v1/product/races/{raceId}` — 대회 상세
  - `GET /api/v1/product/races/{raceId}/similar` — 비슷한 대회 (검색 서버 벡터 검색 + DB)
  - `GET /api/v1/order/parties/me/race-status?raceId={raceId}` — 이 대회에서 내 파티 상태 (파티 없어도 200)
  - `GET /api/v1/member/crews/me` — 내 크루 목록 → 다음: `crewId`
  - `GET /api/v1/member/crews/{crewId}` — 크루 정보·크루원
  - `GET /api/v1/order/parties/crews/{crewId}/active` — 크루의 진행 중 파티 → 다음: `partyId`
  - `GET /api/v1/order/parties/{partyId}` — 파티 정보·파티원
  - 보조(선택): `GET /api/v1/file/files?refType=Race&refId={raceId}`, `.../similar`는 검색 서버를 타므로 끄고 켜며 분리 측정

- **부하 축**: [x] 조회(read, RPS) [ ] 쓰기 — **조회가 지배적.** 한 iteration이 GET 8개쯤이므로 **보고에 "iteration/s"와 "총 RPS(≈ iteration/s × 8)"를 함께** 적는다(guide E). 서비스가 product·member·order 셋으로 갈린다.

- **필요 시드**:
  - 회원 N명 (C 공통: 본인인증 주입 → join → 비번 DB 통일, 지갑 자동).
  - 전용 데이터: 공개(접수 중) 대회 몇 개 — 상태는 스케줄러가 바꾸게 **날짜만 당기고** 둔다(guide C). 크루 1개 + 그 크루에 **진행 중 파티 1~2개**(`parties/crews/{crewId}/active`가 비면 뒤 호출이 생략돼 측정이 얕아짐). 시드 회원 일부를 그 크루에 넣어 `crews/me`가 비지 않게.
  - 소비성 여부: **없음(순수 읽기).** 같은 계정·같은 대회/크루를 반복해도 되므로 **토큰만 있으면 램프를 높게** 가져갈 수 있다.

- **인증**: Bearer 토큰(D). 선행 호출 없음(토큰 발급뿐). `race-status`·`crews/me`·`parties/{partyId}`는 토큰 필요, `races`·`races/{raceId}`·`similar`는 공개일 수 있으니 토큰 유무로 나눠 재면 캐시/인가 비용도 분리된다.

- **판정 기준(재기 전)**: 조회 p95 **300ms** / 쓰기 p95 — (없음) / 실패율 **1%** / 정상거절 코드 — (없음. 모두 200 기대. 파티 없는 대회의 `race-status`도 200).

- **단계(목표 iteration/s)**: smoke **3** (30s, 도는지·값 흐르는지) → load **20** (2m) → stress **0→60**(2m 램프). 총 RPS로는 ≈24 → 160 → 480. funnel enter(최대 33/s)보다 읽기라 더 높게 밀어 노드 CPU 포화점을 찾는다. 단계마다 쓸 인원만 로그인(D).

- **예상 병목 가설 + 볼 지표**:
  - 1순위 **노드 CPU(2코어)** — 조회 부하는 보통 여기서 먼저 포화(F-1). `node_cpu`, 부하 늘릴 때 전 서비스 p95가 같이 뜨면 CPU/스왑.
  - 2순위 **`/similar` 검색 서버 벡터 검색** — 외부 호출 지연. `/similar` 뺀 run과 비교해 기여분 분리. 검색 서버 CPU·응답.
  - 3순위 **서비스별 hikari**(`hikaricp_connections_pending`, 기본 10). order가 product/member를 HTTP로 부르면 커넥션 쥠(F-2).

- **정리**: 읽기라 데이터 변경 없음 → 삭제 SQL 불필요.
  - 확인 SQL(선택, 읽기가 부작용 없음 재확인): run 전후 `SELECT COUNT(*) FROM crewrun_order.orders;` / `... participation;` 값이 같아야 한다(조회가 쓰기를 유발하지 않음).

## 비고

- `/similar`가 검색 서버를 타므로, 순수 DB 조회 한계를 보려면 그 호출을 뺀 run을 기준으로 삼고 켠 run을 비교군으로 둔다.
- 응답 `data.*` 키(목록의 `raceId`, `crews/me`의 `crewId`, `active`의 `partyId`)는 실측 전 실제 응답으로 한 번 확인(초안 js는 가정값).
