# 시나리오 B 부하 테스트용 실행 값. cash를 띄울 터미널에서 먼저 source 한다 (시나리오 A의 run-env.sh와 같은 값)
# 설정 파일을 고치지 않고 이번 실행에서만 덮어쓴다 (환경 변수가 application.yaml·observability.yaml보다 우선한다)

# 추적·로그를 모니터링 컨테이너(Alloy)로 보낸다. monitoring 프로필이 있어야 보낼 주소가 들어간다
export SPRING_PROFILES_ACTIVE=dev,monitoring

# 로그: debug를 끄고 info만 남긴다. 로그 찍는 시간이 측정에 섞이지 않게 한다
export LOGGING_LEVEL_COM_BUKANG=info
export LOGGING_LEVEL_ORG_HIBERNATE_SQL=info
export LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND=info

# 추적: 요청 10개 중 1개만 기록한다 (dev 기본 1.0)
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.1

# B-3만: 가짜 PG 결제취소를 실제 PG처럼 늦춘다. 다른 시나리오에서는 0으로 둔다
export CASH_CLIENTS_PG_FAKE_CANCEL_DELAY="${CASH_CLIENTS_PG_FAKE_CANCEL_DELAY:-0ms}"
