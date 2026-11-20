# 부하 테스트

| 폴더 | 대상 | 보내는 곳 |
|---|---|---|
| `scenario-a/` | 대회 접수 오픈 (대기열·주문·결제) | 로컬만 |
| `member/` | 회원: 내 정보, 마케팅 동의 조회, 토큰 재발급 | 운영 (`https://crewrun.duckdns.org`) |
| `crew/` | 크루: 내 크루 목록, 크루 홈, 초대 링크 만들기 | 운영 |
| `inquiry/` | 문의: 내 문의 목록, 상세, 문의 남기기 | 운영 |
| `board/` | 게시판: 글 목록·상세·댓글 목록, 댓글·좋아요·글 쓰기 | 운영 |
| `main-scenario/` | 예매 오픈 몰림 운영 측정 키트: 기준선(`funnel.js`)·실제 흐름(`rush.js`), 실행 절차, 결과, 수집 자료(`data/`) | 운영 |
| `scenarios/` | 화면 부수 조회를 넣은 시나리오 설계 문서와 스크립트 (둘러보기·개인 예매·파티 예매) | 운영 |

회원·크루·문의·게시판은 운영 서버로 보냅니다. 운영 DB는 발표 전에 비우기로 해서 테스트 데이터가 쌓여도 됩니다(2026-11-16).
다른 주소로 보내려면 `-e BASE_URL=http://localhost:8000`을 붙입니다.

## 순서

모든 명령은 `backend/`에서 실행합니다. k6가 필요합니다(`brew install k6`).

1. 테스트 회원 본인인증 결과 넣기 (처음 한 번만). 가입은 본인인증이 필수라 인증 결과를 운영 Redis에 미리 넣습니다.

   ```bash
   loadtest/accounts/identities.sh 200 | ssh <메인 서버> \
     "ssh -i ~/team05-bukang-EC2-private-key-pair.pem ubuntu@10.0.2.222 'sudo docker exec -i bukang-redis redis-cli'"
   ```

2. 로그인해서 토큰 파일 만들기. 처음에는 `-e JOIN=1`로 가입까지 합니다(1번 뒤 30분 안에).
   Nginx가 로그인·가입을 IP당 초당 1건으로 막아서 200명이면 약 4분, 가입까지 하면 약 8분 걸립니다.

   ```bash
   LOADTEST_PASSWORD='…' k6 run -e JOIN=1 loadtest/accounts/login.js
   ```

3. 서비스별로 단계를 올려 가며 돌립니다. 토큰은 30분짜리라 25분쯤 지나면 2번을 다시 돌립니다.

   ```bash
   k6 run -e STAGE=smoke loadtest/board/board.js
   k6 run -e STAGE=load --summary-export loadtest/board/results/$(date +%F)-load.json loadtest/board/board.js
   ```

## 지표 보기

- **k6 쪽 (응답 시간·실패율·초당 요청 수):** k6에 들어 있는 웹 대시보드를 켭니다. 실행하는 동안 `http://localhost:5665`에서 그래프로 보이고, 끝나면 HTML 리포트가 파일로 남습니다.

  ```bash
  K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT=loadtest/board/results/$(date +%F)-load.html \
    k6 run -e STAGE=load loadtest/board/board.js
  ```

- **서버 쪽 (CPU·메모리):** 운영에는 아직 Prometheus·Grafana도, `kubectl top`에 필요한 metrics-server도 없습니다.
  실행하는 동안 메인 서버에서 컨테이너별 사용량과 서버 메모리를 봅니다. 컨테이너 이름이 모두 `app`으로 나오므로 어느 서비스인지는 `sudo k3s crictl ps`의 POD 칸으로 맞춰 봅니다.

  ```bash
  ssh -t <메인 서버> "watch -n 5 'sudo k3s crictl stats; free -m'"
  ```

## 단계

| 단계 | 하는 일 | 시간 |
|---|---|---|
| `smoke` | 시나리오마다 초당 1번. 스크립트가 도는지만 봅니다 | 30초 |
| `load` | 스크립트마다 정한 목표 초당 횟수로 일정하게 | 5분 |
| `stress` | 0에서 목표의 4배까지 천천히 올립니다. 어디서 느려지는지 봅니다 | 10분 |

통과 기준은 `scenario-a`와 같습니다: 조회 p95 300ms, 쓰기 p95 1초, 실패율 1% 미만.
전체 실패율이 5%를 넘으면 30초 뒤 테스트가 스스로 멈춥니다. 운영 서버를 계속 두드리지 않게 하는 안전장치입니다.

## 알아 둘 것

- 로그인·가입은 Nginx 제한(IP당 초당 1건) 때문에 한 대에서는 부하를 줄 수 없어 시나리오에서 뺐습니다.
- `member.js`의 토큰 재발급은 토큰 파일의 Refresh Token을 써 버립니다. `member.js`를 다시 돌리기 전에 2번을 다시 돌립니다.
- 게시판 쓰기는 회원마다 글 1분에 1개, 댓글 10초에 1개로 막혀 있습니다. `board.js`는 계정 수에 맞춰 쓰기 횟수에 상한을 둡니다.
- 응답 시간에는 이 컴퓨터에서 운영 서버까지 오가는 네트워크 시간이 들어 있습니다.
