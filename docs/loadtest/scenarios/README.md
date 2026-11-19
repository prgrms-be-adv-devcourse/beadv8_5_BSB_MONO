# 화면별 부하 테스트 시나리오 (엑셀 + funnel 통합)

「크루런 시나리오 URL 정리」 엑셀의 화면→API 매핑과, 기존 「예매 몰림 funnel」 키트를 **합친** 시나리오 모음입니다.
**공통 틀(운영 준비·시드 원칙·토큰·램프·판정·측정·함정)은 여기서 다시 쓰지 않습니다.**
`../main_scenario/guide.md`의 B~G절을 그대로 쓰고, 각 문서는 **H절 템플릿(대상·부하 축·시드·판정·램프·병목·정리)만** 채웁니다.

> `guide.md`·`funnel.js`와 `main_scenario` 키트는 아직 dev에 없고 **PR #637(브랜치 `docs/loadtest-main-scenario`)로 들어옵니다.**
> 아래 상대경로(`../main_scenario/...`)는 그 PR이 머지되면 맞습니다.

## 왜 합쳤나

기존 funnel(`../main_scenario/funnel.js`)은 **순수 백엔드 흐름**(대기열→신청서→주문→결제)만 재서 화면 부수 API가 빠져 있었고,
엑셀은 **화면→API 전체 매핑**은 있지만 부하 틀이 없었습니다. 둘을 합쳐, **실제 화면이 부르는 부수 조회 + 화면 전환 think-time**까지
반영한 예매 흐름을 만들었습니다. 예매 흐름은 **둘**(개인·파티), 거기에 조회 지배 **둘러보기(read)**를 별도로 둡니다.

| 시나리오 | 지배 축 | 문서 | k6 초안 |
|---|---|---|---|
| **개인 예매** (목록→상세→대기열→신청서→주문→결제, 화면 부수 API+think) | 쓰기(TPS) | [individual-booking.md](individual-booking.md) | [k6/individual-booking.js](k6/individual-booking.js) |
| **파티 예매** (크루목록→크루홈→파티→신청→주문→결제, 파티장+파티원 2역할) | 쓰기(TPS) | [party-booking.md](party-booking.md) | [k6/party-booking.js](k6/party-booking.js) |
| **둘러보기** (대회·크루·파티 조회만) | 읽기(RPS) | [browse-read.md](browse-read.md) | [k6/browse-read.js](k6/browse-read.js) |

- **개인 예매**는 funnel 백엔드 흐름 그대로 + 화면 부수 GET + think-time. 부수 조회는 `-e SIDE=off`로 꺼서 **기준선 funnel과 A/B** 가능.
- **파티 예매**는 파티장(`ROLE=leader`, 좌석 확보)과 파티원(`ROLE=member`, 결제)을 한 스크립트의 exec 둘로. 상태 의존이라 한 번에 하나씩.
- `../main_scenario/funnel.js`는 **순수 백엔드 기준선**으로 그대로 둡니다.

## 쓰는 순서

1. `../main_scenario/guide.md` B~G를 읽는다(공통 준비·시드·토큰·측정·함정).
2. 돌릴 시나리오 문서(H절)를 읽고 **판정 기준·단계 목표를 재기 전에** 확정한다.
3. `../main_scenario/k6/login-prod.js`로 토큰을 만든 뒤(30분 안) 해당 `k6/*.js`를 돌린다.
4. 결과는 `../main_scenario/results.md`처럼 단계별 p95/p99·병목으로 남긴다.

## 주의

- 운영(team05)·미니 PC에 **아무것도 실행하지 않은 설계 문서**입니다. 실행은 사용자 go 뒤에, 공용 서버라 밤/한가할 때.
- k6 스켈레톤은 **초안**입니다. 경로·바디·기대 코드는 엑셀과 코드로 맞췄지만, 응답 형태(`data.*` 키)는 실측 전 실제 응답으로 확인해야 합니다.
- 램프 목표 수치는 funnel 실측(`../main_scenario/results.md`)에서 유추한 **제안값**이며, 측정된 한계가 아닙니다. smoke부터 올려 가며 조정합니다.
