# 시나리오 A 부하 테스트용 실행 값. 서비스를 띄울 터미널마다 먼저 source 한다
# 설정 파일을 고치지 않고 이번 실행에서만 덮어쓴다 (환경 변수가 application.yaml·observability.yaml보다 우선한다)

# 추적·로그를 모니터링 컨테이너(Alloy)로 보낸다. monitoring 프로필이 있어야 보낼 주소가 들어간다
export SPRING_PROFILES_ACTIVE=dev,monitoring

# 로그: debug를 끄고 info만 남긴다. 로그 찍는 시간과 Loki로 보내는 양이 측정에 섞이지 않게 한다
export LOGGING_LEVEL_COM_BUKANG=info
export LOGGING_LEVEL_ORG_HIBERNATE_SQL=info
# SQL에 넣는 값 로그도 끈다. 이메일·비밀번호 해시가 Loki에 쌓이는 것도 같이 막힌다
export LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND=info

# 추적: 요청 10개 중 1개만 기록한다 (dev 기본 1.0). 기록 비용이 p95를 부풀리지 않게 하면서 느린 trace는 찾을 수 있다
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.1
