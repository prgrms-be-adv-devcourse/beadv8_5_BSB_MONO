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

# 모듈 소스를 복사한다. settings.gradle.kts에 등록된 모듈 폴더가 모두 있어야 Gradle이 설정 단계를 통과한다
COPY common common
COPY member-service member-service
COPY cash-service cash-service

# Gradle 캐시(~/.gradle)를 빌드 간에 유지해서, 의존성은 변경된 것만 새로 받는다
# :<모듈>:bootJar 는 그 서비스와 의존하는 common만 빌드한다
RUN --mount=type=cache,target=/root/.gradle ./gradlew :${MODULE}:bootJar --no-daemon

# ── 2단계: 실행 (JRE 이미지에 jar만 복사) ──
FROM eclipse-temurin:25-jre
ARG MODULE
WORKDIR /app

# root 대신 앱 전용 시스템 계정으로 실행 (앱이 탈취돼도 컨테이너 내 권한을 최소화)
RUN useradd --system --no-create-home --shell /usr/sbin/nologin spring

# bootJar는 실행용 jar 하나만 만들므로 파일명 대신 패턴으로 복사 (version이 바뀌어도 동작)
COPY --from=build /workspace/${MODULE}/build/libs/*.jar app.jar

USER spring
# 실제 포트는 서비스마다 다르다 (member-service 8080, cash-service 8081 / 각 application.yaml의 server.port)
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
