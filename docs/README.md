# 문서 지도

크루런 저장소의 문서가 어디에 있는지 알려 주는 얇은 안내 문서입니다.
사람과 AI가 필요한 문서만 골라 읽도록, 내용은 옮기지 않고 위치와 한 줄 설명만 적습니다.

- **읽는 법:** 아래 「무엇을 하려는가」에서 할 일을 찾고, 그 줄의 문서만 엽니다. 전체 목록은 그 아래에 있습니다.
- **고치는 법:** 문서를 새로 만들거나 옮기면 이 파일에 한 줄을 더하거나 고칩니다.

## 무엇을 하려는가

| 하려는 일 | 먼저 읽을 문서 |
|---|---|
| 프로젝트가 무엇인지 알기 | [../README.md](../README.md), [../CONTEXT.md](../CONTEXT.md) (용어) |
| 로컬에서 띄우기 | [SETUP.md](SETUP.md), [../backend/README.md](../backend/README.md) |
| 백엔드 코드 고치기 | [../backend/AGENTS.md](../backend/AGENTS.md), [../.claude/rules/](../.claude/rules/) |
| 프론트엔드 코드 고치기 | [../frontend/AGENTS.md](../frontend/AGENTS.md) |
| 기능의 규칙(정책) 확인하기 | [policy/README.md](policy/README.md) |
| 이슈 만들기, PR 열기 | [LINEAR.md](LINEAR.md), [../.github/pull_request_template.md](../.github/pull_request_template.md) |
| 커밋 메시지 쓰기 | [../.claude/rules/git-commit-convention.md](../.claude/rules/git-commit-convention.md) |
| 배포, 운영 서버 보기 | [../infra/README.md](../infra/README.md), [../k8s/README.md](../k8s/README.md) |
| 부하 테스트 돌리기 | [loadtest/README.md](loadtest/README.md) |
| 성능 측정 결과 찾기 | [measurements/](measurements/) (아래 목록) |
| 과거 장애 · 병목의 원인 찾기 | [troubleshooting/](troubleshooting/) (아래 목록) |
| 문제 해결 과정 문서 쓰기 | [templates/troubleshooting.md](templates/troubleshooting.md) |

## 저장소 안내 문서

| 문서 | 내용 |
|---|---|
| [../README.md](../README.md) | 프로젝트 소개 (밖에서 보는 사람용) |
| [../CLAUDE.md](../CLAUDE.md) | AI 작업 규칙의 입구. 폴더별 규칙 문서로 이어짐 |
| [../CONTEXT.md](../CONTEXT.md) | 도메인 용어 |
| [SETUP.md](SETUP.md) | 개발 환경 설정 (도구, git, 환경변수, MCP) |
| [LINEAR.md](LINEAR.md) | Linear 사용 규칙, 브랜치 · PR 규칙, CI 필수 체크 |
| [LINEAR-API-KEY.md](LINEAR-API-KEY.md) | Linear API 키를 저장소 시크릿으로 등록하는 방법 |

## 컨벤션 (`.claude/rules/`)

| 문서 | 내용 |
|---|---|
| [git-commit-convention.md](../.claude/rules/git-commit-convention.md) | 커밋 메시지 (Conventional Commits) |
| [java-convention.md](../.claude/rules/java-convention.md) | Java 코드 스타일, Checkstyle |
| [kafka-topic-convention.md](../.claude/rules/kafka-topic-convention.md) | Kafka 토픽 이름 |
| [redis-key-convention.md](../.claude/rules/redis-key-convention.md) | Redis 키 이름 |
| [swagger-convention.md](../.claude/rules/swagger-convention.md) | Swagger 애노테이션 |

## 백엔드

| 문서 | 내용 |
|---|---|
| [backend/README.md](../backend/README.md) | 실행 방법, 컨테이너 구성, 서비스별 DB |
| [backend/AGENTS.md](../backend/AGENTS.md) | 모듈 구조와 백엔드 작업 규칙 |
| [backend/file-service/README.md](../backend/file-service/README.md) | 파일 서비스 |
| [backend/file-service/ROADMAP.md](../backend/file-service/ROADMAP.md), [BATCH-ROADMAP.md](../backend/file-service/BATCH-ROADMAP.md) | 파일 서비스와 파일 정리 배치의 결정 기록 |
| [backend/product-service/ROADMAP.md](../backend/product-service/ROADMAP.md) | 상품 서비스의 결정 기록 |
| [backend/queue-service/ROADMAP.md](../backend/queue-service/ROADMAP.md), [PARTY_WAITING_ROADMAP.md](../backend/queue-service/PARTY_WAITING_ROADMAP.md) | 대기열과 파티 대기열의 결정 기록 |
| [backend/scripts/payout-e2e.md](../backend/scripts/payout-e2e.md) | 정산 E2E 시나리오 |
| [erd/cash.md](erd/cash.md) | 결제 · 예치금 ERD |
| [adr/0001-payout-fee-tax-invoice.md](adr/0001-payout-fee-tax-invoice.md) | 결정 기록: 판매자 수수료 세금계산서 |
| [api-gateway-comparison.md](api-gateway-comparison.md) | 완성품 API 게이트웨이와 Spring Cloud Gateway 비교 |

## 프론트엔드

| 문서 | 내용 |
|---|---|
| [frontend/AGENTS.md](../frontend/AGENTS.md) | 프론트엔드 작업 규칙 |
| [frontend/docs/common-component-audit.md](../frontend/docs/common-component-audit.md) | 공통 컴포넌트 점검표 |
| [frontend/e2e/record-cert/README.md](../frontend/e2e/record-cert/README.md) | 기록증 심사 E2E |
| [frontend/e2e/smoke/README.md](../frontend/e2e/smoke/README.md) | 운영 스모크 테스트 |
| [architecture/](architecture/) | 회원가입 · 인증 흐름 그림 (PNG) |

## 기획과 정책

| 문서 | 내용 |
|---|---|
| [policy/README.md](policy/README.md) | 정책 목차. 1~10장(회원, 대회 등록, 예매, 결제, 취소 · 환불, 정산, 크루, 대기열, 추첨, 문의) |
| [policy/payout.md](policy/payout.md) | 판매자 정산 상세안 |
| [policy/party-lottery.md](policy/party-lottery.md) | 파티 신청 · 추첨 수정안 |
| [policy/listing-gaps.md](policy/listing-gaps.md) | 입점 기준 보강안 |
| [policy/questions.md](policy/questions.md) | 정책 질문 모음 |
| [ideation/](ideation/) | 기획 자료 16개: 경쟁사 분석, 문제 정의, 페르소나, 시장 규모, 기능 우선순위 |

## 인프라와 배포

| 문서 | 내용 |
|---|---|
| [infra/README.md](../infra/README.md) | 운영 서버 전체 구성, AWS 자원, 전환 절차 |
| [infra/main-node/README.md](../infra/main-node/README.md) | 메인 EC2 (k3s, 앱) |
| [infra/data-node/README.md](../infra/data-node/README.md) | 보조 EC2 (MySQL, Redis, Redpanda, Elasticsearch) |
| [infra/monitoring-node/README.md](../infra/monitoring-node/README.md) | 모니터링 서버 (Prometheus, Grafana) |
| [k8s/README.md](../k8s/README.md) | k8s 매니페스트, 로컬 k3d 스크립트 |

## 부하 테스트 (`loadtest/`)

스크립트와 실행 방법입니다. `data/`, `results/` 폴더는 원본 측정 자료라 필요할 때만 엽니다.

| 문서 | 내용 |
|---|---|
| [loadtest/README.md](loadtest/README.md) | 부하 테스트 전체 안내 |
| [loadtest/ab-measurement-guide.md](loadtest/ab-measurement-guide.md) | 고치기 전후(A · B) 측정 따라 하기 |
| [loadtest/main-scenario/README.md](loadtest/main-scenario/README.md) | 예매 몰림 운영 부하 테스트. [guide.md](loadtest/main-scenario/guide.md), [runbook.md](loadtest/main-scenario/runbook.md), [results.md](loadtest/main-scenario/results.md) |
| [loadtest/real-flow/README.md](loadtest/real-flow/README.md) | 화면과 같은 요청 순서로 재는 예매 오픈 몰림 |
| [loadtest/scenario-b/README.md](loadtest/scenario-b/README.md) | 예치금 부하 테스트 |
| [loadtest/scenarios/README.md](loadtest/scenarios/README.md) | 화면별 시나리오: [둘러보기](loadtest/scenarios/browse-read.md), [개인 예매](loadtest/scenarios/individual-booking.md), [파티 예매](loadtest/scenarios/party-booking.md) |
| [loadtest/results/2026-11-16-member-crew-board.md](loadtest/results/2026-11-16-member-crew-board.md) | 회원 · 크루 · 게시판 운영 부하 결과 |

## 측정 결과 (`measurements/`)

파일 이름은 `날짜-주제`입니다. 같은 주제는 판정 기준 → 측정 → 전후 비교 순서로 읽습니다.

| 주제 | 문서 |
|---|---|
| 주문 결제 잠금 | [판정 기준](measurements/2026-11-13-결제-잠금-판정기준.md), [A · B 결과](measurements/2026-11-14-결제-잠금-A-B.md), [자동 정리](measurements/2026-11-14-결제-잠금-자동정리.md), [측정 도구](measurements/load/README.md) |
| 예치금 부하 1차 | [판정 기준](measurements/2026-11-16-예치금-부하-판정기준.md), [측정](measurements/2026-11-16-예치금-부하.md), [기준선](measurements/2026-11-17-예치금-부하-기준선.md), [전후 비교](measurements/2026-11-17-예치금-부하-전후.md), [계획서 · 보고서](measurements/result/2026-11-17-예치금-부하-1차-계획서-보고서.md), [상세](measurements/result/2026-11-17-예치금-부하-1차-상세.md) |
| 예치금 부하 2차 | [판정 기준](measurements/2026-11-18-예치금-부하-2차-판정기준.md), [측정](measurements/2026-11-19-예치금-부하-2차.md), [고칠 것](measurements/2026-11-20-예치금-부하-2차-고칠것.md), [전후 비교](measurements/2026-11-20-예치금-부하-2차-전후.md), [계획서 · 보고서](measurements/result/2026-11-20-예치금-부하-2차-계획서-보고서.md), [상세](measurements/result/2026-11-20-예치금-부하-2차-상세.md) |
| 예매 오픈 몰림 | [인덱스 5 · 6차](measurements/2026-11-20-예매-오픈-몰림-인덱스-5차-6차.md) |

## 문제 해결 기록 (`troubleshooting/`)

| 문서 | 무엇을 풀었나 |
|---|---|
| [2026-10-30 충전 확정 토스 중복 호출](troubleshooting/2026-10-30-충전-확정-토스-중복-호출-제거.md) | 같은 충전에 몰린 동시 확정 요청의 토스 승인 중복 호출 |
| [2026-11-03 토스 승인과 기록 맞추기](troubleshooting/2026-11-03-충전-확정-토스-승인과-기록-맞추기.md) | 토스 승인과 우리 기록이 어긋나던 문제 |
| [2026-11-17 알림 Kafka 전송 실패](troubleshooting/2026-11-17-알림-Kafka-전송-실패.md) | 공용 쓰레드에서 보낸 알림이 Kafka로 나가지 않던 문제 |
| [2026-11-17 홀딩 지갑 한 행 처리량](troubleshooting/2026-11-17-홀딩-지갑-한-행-처리량-천장.md) | 정산이 섞이면 결제 처리량이 떨어지던 원인 |
| [2026-11-17 환급 카드 결제취소](troubleshooting/2026-11-17-환급-카드-결제취소-동시에-부르기.md) | 카드 결제취소를 카드마다 동시에 부르기 |
| [2026-11-18 게이트웨이 요청당 CPU](troubleshooting/2026-11-18-게이트웨이-요청당-CPU.md) | 요청마다 연결을 새로 받아 CPU를 쓰던 문제 |
| [2026-11-19 막 띄운 직후 결제 지연](troubleshooting/2026-11-19-막-띄운-직후-결제-지연.md) | 섞인 부하에서만 결제 p95가 4~10초 되던 원인 |
| [2026-11-20 충전 확정 연속 문제](troubleshooting/2026-11-20-충전-확정-고칠-때마다-생긴-다음-문제.md) | 한 번 고칠 때마다 다음 문제가 생긴 과정 |
| [2026-11-21 기한 알림 배치 SQL](troubleshooting/2026-11-21-기한-알림-배치-SQL-줄이기.md) | 기한 알림 배치의 N+1 조회 묶기 ([11-17 판](troubleshooting/2026-11-17-기한-알림-배치-SQL-줄이기.md)) |

## 템플릿과 그 밖

| 위치 | 내용 |
|---|---|
| [templates/troubleshooting.md](templates/troubleshooting.md) | 문제 해결 기록 양식 |
| [templates/handoff.md](templates/handoff.md) | 전달문 양식 |
| [../.claude/skills/](../.claude/skills/) | 팀 공용 Claude Code 스킬 |
| `pr-captures/`, `screenshots/` | PR과 문서에 쓴 화면 사진 |
