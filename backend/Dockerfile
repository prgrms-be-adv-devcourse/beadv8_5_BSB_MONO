# ── 1단계: 빌드 (JDK 이미지에서 실행용 jar 생성) ──
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 빌드 설정 파일을 먼저 복사한다 (이 파일들이 그대로면 아래 단계는 Docker 캐시를 재사용)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew

# 소스는 나중에 복사한다 (소스만 바뀌면 여기서부터 다시 실행)
COPY src src

# Gradle 캐시(~/.gradle)를 빌드 간에 유지해서, 의존성은 변경된 것만 새로 받는다
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

# ── 2단계: 실행 (JRE 이미지에 jar만 복사) ──
FROM eclipse-temurin:25-jre
WORKDIR /app

# root 대신 앱 전용 시스템 계정으로 실행 (앱이 탈취돼도 컨테이너 내 권한을 최소화)
RUN useradd --system --no-create-home --shell /usr/sbin/nologin spring

# bootJar는 실행용 jar 하나만 만들므로 파일명 대신 패턴으로 복사 (version이 바뀌어도 동작)
COPY --from=build /workspace/build/libs/*.jar app.jar

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
