// 게시판 서비스: 로그인 없이 읽는 커뮤니티 자유 게시판(글, 댓글, 좋아요). 회원 이벤트로 닉네임·판매자 복사본을 만든다 (BUK-156)
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	// webmvc, data-jpa, validation, springdoc, querydsl-jpa(테스트는 webmvc-test)는 common이 넘겨 준다 (common/build.gradle.kts)
	implementation(project(":common"))

	// 표 변경은 Flyway SQL(db/migration)로만 한다 (알림·주문 서비스와 같은 방식). MySQL 지원은 별도 모듈
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-kafka") // kafka (회원 이벤트 받기, 사진 연결 요청 보내기)
	implementation("org.springframework.boot:spring-boot-starter-data-redis") // redis (도배 제한)

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools
	// App 실행시 compose.yml 인프라 자동 실행 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation(testFixtures(project(":common"))) // GlobalConfigTestExecutionListener (테스트마다 EventPublisher 재설정)
}
