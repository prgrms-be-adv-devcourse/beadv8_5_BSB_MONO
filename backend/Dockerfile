# 서비스 하나를 실행 이미지로 빌드한다. 빌드 컨텍스트는 backend/ 이고, 어떤 서비스인지는 MODULE 인자로 정한다
# 예) docker build --build-arg MODULE=member-service -t bukang-member-service .
#     (compose.yml의 각 앱 서비스가 build.args.MODULE로 넘긴다)

# ── 1단계: 빌드 (JDK 이미지에서 실행용 jar 생성) ──
FROM eclipse-temurin:25-jdk AS build
ARG MODULE
WORKDIR /workspace

# 빌드 설정 파일을 먼저 복사한다 (이 파일들이 그대로면 아래 단계는 Docker 캐시를 재사용)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew

# Gradle(래퍼 배포본·의존성)을 이미지 층에 받아 둔다 (CRU-125)
# 예전에는 RUN --mount=type=cache에 받았는데, 그 캐시는 GitHub Actions의 이미지 캐시로 내보내지지 않아
# 소스가 하나만 바뀌어도 CI에서 매번 라이브러리를 처음부터 받았다. 층에 두면 build.gradle.kts가 그대로인 동안 다시 받지 않는다
ENV GRADLE_USER_HOME=/gradle-home

# 의존성 받기: 모듈의 build.gradle.kts만 먼저 복사한다. 소스가 바뀌어도 이 층은 캐시를 그대로 쓴다
# settings.gradle.kts에 등록된 모듈 폴더가 모두 있어야 Gradle이 설정 단계를 통과한다
COPY common/build.gradle.kts common/
COPY jwt/build.gradle.kts jwt/
COPY gateway/build.gradle.kts gateway/
COPY member-service/build.gradle.kts member-service/
COPY cash-service/build.gradle.kts cash-service/
COPY payout-service/build.gradle.kts payout-service/
COPY file-service/build.gradle.kts file-service/
COPY order-service/build.gradle.kts order-service/
COPY product-service/build.gradle.kts product-service/
COPY queue-service/build.gradle.kts queue-service/
COPY notification-service/build.gradle.kts notification-service/
COPY inquiry-service/build.gradle.kts inquiry-service/
COPY board-service/build.gradle.kts board-service/
COPY ai-service/build.gradle.kts ai-service/
COPY observability-dev/build.gradle.kts observability-dev/
# dependencies 작업은 그 모듈(과 common·jwt)의 모든 의존성 목록을 풀면서 라이브러리를 내려받는다. 출력은 버린다
RUN ./gradlew :${MODULE}:dependencies --no-daemon -q > /dev/null

# 모듈 소스를 복사한다
COPY common common
COPY jwt jwt
COPY gateway gateway
COPY member-service member-service
COPY cash-service cash-service
COPY payout-service payout-service
COPY file-service file-service
COPY order-service order-service
COPY product-service product-service
COPY queue-service queue-service
COPY notification-service notification-service
COPY inquiry-service inquiry-service
COPY board-service board-service
COPY ai-service ai-service
# bootRun 전용(developmentOnly) 모듈. jar에는 빠지지만 Gradle 설정 단계를 통과하려면 폴더가 있어야 한다
COPY observability-dev observability-dev

# :<모듈>:bootJar 는 그 서비스와 의존하는 라이브러리 모듈(common, jwt)만 빌드한다. 의존성은 위 층에 이미 있다
# 앱마다 자기 층을 쓰므로 compose가 여러 이미지를 동시에 빌드해도 Gradle 캐시 잠금을 기다리지 않는다
RUN ./gradlew :${MODULE}:bootJar --no-daemon

# ── 2단계: 실행 (JRE 이미지에 jar만 복사) ──
FROM eclipse-temurin:25-jre
ARG MODULE
WORKDIR /app

# 컨테이너는 서버(EC2)·PC의 시간대와 상관없이 UTC로 돈다. JVM 시각을 한국 시간으로 맞춘다
# (LocalDateTime으로 남기는 createDate·modifyDate, 배치 기준 시각이 MySQL(compose의 TZ: Asia/Seoul)과 같은 시계를 쓴다)
ENV TZ=Asia/Seoul

# root 대신 앱 전용 시스템 계정으로 실행 (앱이 탈취돼도 컨테이너 내 권한을 최소화)
RUN useradd --system --no-create-home --shell /usr/sbin/nologin spring

# bootJar는 실행용 jar 하나만 만들므로 파일명 대신 패턴으로 복사 (version이 바뀌어도 동작)
COPY --from=build /workspace/${MODULE}/build/libs/*.jar app.jar

USER spring
# 실제 포트는 서비스마다 다르다 (gateway 8000, member-service 8080, cash-service 8081, payout-service 8082, file-service 8083, order-service 8084, product-service 8085, queue-service 8086, notification-service 8087, inquiry-service 8088, board-service 8089, ai-service 8092 / 각 application.yaml의 server.port)
EXPOSE 8000 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
