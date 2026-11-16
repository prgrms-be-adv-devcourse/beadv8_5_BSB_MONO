// 캐시 서비스: 회원 이벤트를 받아 CashMember로 복제하고, 캐시(예치금)를 관리한다
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	// webmvc, data-jpa, validation, springdoc, querydsl-jpa(테스트는 webmvc-test)는 common이 넘겨 준다 (common/build.gradle.kts)
	implementation(project(":common"))

	// 표 변경은 Flyway SQL(db/migration)로만 한다. MySQL 지원은 별도 모듈
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka (회원 이벤트 수신)
	implementation("org.springframework.boot:spring-boot-starter-data-redis") // redis (충전 이상 패턴 감지: 쿨다운·잠금)
	// 계좌번호·pgPaymentKey 암호화(AES-GCM)는 common global/crypto를 쓴다 (spring-security-crypto도 common이 가져온다)

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools
	// App 실행시 compose.yml 인프라 자동 실행 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation(testFixtures(project(":common"))) // GlobalConfigTestExecutionListener (테스트마다 EventPublisher 재설정)
	testImplementation("org.springframework.boot:spring-boot-testcontainers") // @ServiceConnection
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql") // H2와 동작이 다른 동시성은 진짜 MySQL로 확인한다
	testImplementation("com.redis:testcontainers-redis") // RedisContainer (Boot BOM 관리 버전). TTL·원자적 증가는 가짜 Redis로 확인할 수 없다

	// QueryDSL Q클래스 생성기 (querydsl-jpa는 common이 넘겨 준다. 처리기는 전달되지 않아 모듈마다 적는다)
	val querydslVersion = dependencyManagement.importedProperties["querydsl.version"]
	annotationProcessor("com.querydsl:querydsl-apt:${querydslVersion}:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")
}

// QueryDSL Q클래스 생성 위치 (javac가 같은 컴파일 단계에서 함께 컴파일하므로 sourceSets 등록은 하지 않는다)
val querydslDir = layout.buildDirectory.dir("generated/querydsl")

tasks.compileJava {
	options.generatedSourceOutputDirectory = querydslDir
}

tasks.clean {
	delete(querydslDir)
}
