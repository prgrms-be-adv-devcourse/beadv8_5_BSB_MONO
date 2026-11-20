# 부하 테스트

| 폴더 | 대상 | 보내는 곳 |
|---|---|---|
| `scenario-a/` | 대회 접수 오픈 (대기열·주문·결제) | 로컬만 |
| `member/` | 회원: 내 정보, 마케팅 동의 조회, 토큰 재발급 | 운영 (`https://crewrun.duckdns.org`) |
| `crew/` | 크루: 내 크루 목록, 크루 홈, 초대 링크 만들기 | 운영 |
| `inquiry/` | 문의: 내 문의 목록, 상세, 문의 남기기 | 운영 |
| `board/` | 게시판: 글 목록·상세·댓글 목록, 댓글·좋아요·글 쓰기 | 운영 |
| `e2e/member.js` | 회원 화면 흐름: 앱 열기·마이페이지, 정보 수정, 알림 설정, 로그아웃과 로그인 | 운영 |
| `e2e/crew.js` | 크루 화면 흐름: 크루 탭, 초대, 초대 받기, 크루 나가기, 크루 관리 | 운영 |

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

## 화면 흐름 부하 테스트 (`e2e/`)

가상 사용자 한 명이 화면 순서대로 움직이며, 화면이 지금 보내는 요청을 그대로 보냅니다. 화면 하나가 k6 `group` 하나이고, 화면 사이에 3~5초 쉽니다.
주문을 건마다 상세 조회하는 것, 저장 뒤 다시 조회하는 것처럼 줄일 수 있는 요청도 일부러 넣었습니다. 고친 뒤 좋아진 만큼을 재기 위해서입니다.
화면별 p95 1초 기준이 추가되고, 결과에서 화면(group) 이름으로 나뉘어 보입니다. 프론트 RSC 요청, 카카오 초대, 파티, 대기열, 결제, 가입은 뺐습니다.

| 단계 | 사용자 수 | 시간 |
|---|---|---|
| `smoke` | 1명 | 30초 |
| `load` | 100명 | 5분 |
| `stress` | 0에서 200명까지 | 10분 |

- 계정은 가상 사용자 번호로 고르고, 계정 하나는 가상 사용자 한 명만 씁니다. Refresh Token을 두 번 쓰면 서버가 로그인을 끊기 때문입니다.
  계정이 모자라면 사용자 수를 줄이고 경고를 냅니다. `crew.js`는 계정 두 개를 짝으로 쓰므로 계정 200개면 최대 100명입니다.
- 401·403을 받으면 그 사용자는 그 반복을 끝내고 5초 쉽니다. 재발급이 401이면 로그인이 끊긴 계정이라 더 쓰지 않습니다.
- `member.js`는 Refresh Token을 써 버려서 다시 돌리기 전에 `accounts/login.js`를 다시 돌립니다. `crew.js`는 Access Token만 써서 토큰 30분 안에서는 이어서 돌릴 수 있습니다.
- `crew.js`의 초대받는 사람은 가입하고 바로 나갑니다. 크루 인원이 쌓이지 않고, 나간 사람이 다시 가입하는 데 제한이 없어서 반복할 수 있습니다.
  초대 링크는 취소하지 않아 24시간 동안 크루장의 초대 목록에 쌓입니다.

```bash
LOADTEST_PASSWORD='…' k6 run -e STAGE=smoke loadtest/e2e/member.js
k6 run -e STAGE=smoke loadtest/e2e/crew.js
```

환경 변수:

| 이름 | 기본값 | 뜻 |
|---|---|---|
| `LOADTEST_PASSWORD` | 없음 | `member.js`의 로그인 화면에 쓰는 비밀번호(필수). `accounts/login.js`와 같은 값 |
| `LOGIN_EVERY` | 0(안 함) | `member.js`가 로그아웃과 로그인 화면을 몇 번 반복마다 한 번 할지 |
| `ORDER_DETAIL_MAX` | 10 | 마이페이지에서 상세를 조회할 주문 수 상한 |
| `MANAGE_EVERY` | 5 | `crew.js`가 크루 관리 화면을 몇 번 반복마다 한 번 할지 |

운영 nginx가 로그아웃·로그인을 IP당 초당 1건(몰리면 20건까지)으로 막고 있습니다. 100명이면 10번에 한 번만 해도 이 제한을 넘어 사용자가 하나씩 멈춥니다.
그래서 로그아웃과 로그인 화면은 기본으로 하지 않습니다. CRU-83 스위치로 제한을 끈 실행에서만 `-e LOGIN_EVERY=10`처럼 켭니다.
스위치는 `k8s/overlays/aws/nginx-upstreams.conf`의 `map "false" $auth_limit_key` 줄이고, `"true"`로 바꿔 배포하면 꺼집니다. 절차는 `docs/loadtest/main_scenario/runbook.md` 8절에 있습니다.
끄면 모든 IP의 로그인·가입 제한이 함께 풀리므로 부하가 끝나면 다시 `"false"`로 되돌립니다.
