plugins {
	java
	checkstyle
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {

	implementation("org.springframework.boot:spring-boot-h2console") // h2 콘솔
	implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch") // elastic search
	implementation("org.springframework.boot:spring-boot-starter-data-jpa") // dataJpa
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka
	implementation("org.apache.kafka:kafka-streams") // kafka topic join
	implementation("org.springframework.boot:spring-boot-starter-security") // spring security
	implementation("org.springframework.boot:spring-boot-starter-webmvc") // spring-web
	implementation("org.springframework.boot:spring-boot-starter-validation") // @Valid 요청 검증
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0") //swagger and openapi
	implementation("org.springframework.boot:spring-boot-starter-data-redis") // redis

	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

	compileOnly("org.projectlombok:lombok") // lombok
	annotationProcessor("org.projectlombok:lombok")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools


	testImplementation("org.springframework.boot:spring-boot-starter-data-elasticsearch-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test") // @DataRedisTest 지원

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	// 강의 교안 App 실행시 Docker 재시작 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	// 강의 교안 ElasticSearch TDD의존성 (BOM 관리 버전)
	testImplementation("org.springframework.boot:spring-boot-testcontainers") // @ServiceConnection
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")    // @Testcontainers, @Container
	testImplementation("org.testcontainers:testcontainers-elasticsearch")
	// 필요해질 때 추가 (테스트 시 임시 컨테이너 생성)
	// testImplementation("org.testcontainers:testcontainers-kafka")
	// testImplementation("org.testcontainers:testcontainers-mysql")
	// testImplementation("com.redis:testcontainers-redis")  // Boot BOM 관리 (2.2.4)

	// QueryDSL (Jakarta 버전, 버전은 Spring Boot BOM의 querydsl.version 사용)
	val querydslVersion = dependencyManagement.importedProperties["querydsl.version"]
	implementation("com.querydsl:querydsl-jpa:${querydslVersion}:jakarta")
	annotationProcessor("com.querydsl:querydsl-apt:${querydslVersion}:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")
}

// 캠퍼스 핵데이 Java 코딩 컨벤션 검사 (규칙 파일은 원격 저장소에서 직접 참조)
checkstyle {
	toolVersion = "14.3.0"
	config = resources.text.fromUri(
		"https://raw.githubusercontent.com/naver/hackday-conventions-java/master/rule-config/naver-checkstyle-rules.xml"
	)
	// 규칙 파일이 참조하는 suppression 파일 경로 (optional 이므로 파일이 없으면 무시된다)
	configProperties["suppressionFile"] = "${projectDir}/config/checkstyle/suppressions.xml"
	// 위반 시 빌드를 실패시키지 않고 경고만 출력한다
	isIgnoreFailures = true
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// QueryDSL Q클래스 생성 위치 (javac가 같은 컴파일 단계에서 함께 컴파일하므로 sourceSets 등록은 하지 않는다)
val querydslDir = layout.buildDirectory.dir("generated/querydsl")

tasks.compileJava {
	options.generatedSourceOutputDirectory = querydslDir
}

tasks.clean {
	delete(querydslDir)
}