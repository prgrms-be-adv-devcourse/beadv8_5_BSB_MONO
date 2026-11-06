// 알림 서비스: 다른 서비스가 보낸 알림 요청(push.notification.requested)을 회원별로 저장하고, 내 알림 조회·읽음 API를 연다 (BUK-69)
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":common"))

	implementation("org.springframework.boot:spring-boot-starter-webmvc") // spring-web
	implementation("org.springframework.boot:spring-boot-starter-data-jpa") // dataJpa
	// 표 변경은 Flyway SQL(db/migration)로만 한다 (주문 서비스와 같은 방식). MySQL 지원은 별도 모듈
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka (알림 요청 받기)
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
}
