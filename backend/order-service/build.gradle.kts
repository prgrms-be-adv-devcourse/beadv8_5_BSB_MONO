// 주문 서비스: 참가 신청, 바로 결제 예매(임시 확보·결제 기한), 장바구니, 주문 상태·내역, 결제·취소 흐름 조립
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":common"))

	implementation("org.springframework.boot:spring-boot-starter-webmvc") // spring-web
	implementation("org.springframework.boot:spring-boot-starter-data-jpa") // dataJpa
	// 표 변경은 Flyway SQL(db/migration)로만 한다 (BUK-29). MySQL 지원은 별도 모듈
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-validation") // @Valid 요청 검증
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka (결제·취소 이벤트 발행)
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0") //swagger and openapi

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools
	// App 실행시 compose.yml 인프라 자동 실행 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation(testFixtures(project(":common"))) // GlobalConfigTestExecutionListener (테스트마다 EventPublisher 재설정)

	// QueryDSL (Jakarta 버전, 버전은 Spring Boot BOM의 querydsl.version 사용)
	val querydslVersion = dependencyManagement.importedProperties["querydsl.version"]
	implementation("com.querydsl:querydsl-jpa:${querydslVersion}:jakarta")
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
