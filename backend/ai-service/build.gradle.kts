// 크루런 AI 서비스: 판매자·관리자 센터의 챗봇. 운영 숫자는 각 서비스 API로 가져와 답한다 (BUK-165, 뼈대)
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

// Spring AI 버전 묶음. 2.0.x가 Spring Boot 4.0.x·4.1.x를 지원한다
dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:2.0.2")
	}
}

dependencies {
	// webmvc, data-jpa, validation, springdoc, querydsl-jpa(테스트는 webmvc-test)는 common이 넘겨 준다 (common/build.gradle.kts)
	implementation(project(":common"))

	// 모델 연결. 둘 다 넣고 어느 쪽을 쓸지는 설정(spring.ai.model.chat)이 정한다: 서버는 openai, 로컬·테스트는 ollama
	implementation("org.springframework.ai:spring-ai-starter-model-openai")
	implementation("org.springframework.ai:spring-ai-starter-model-ollama")
	// 대화 기록을 Redis에 둔다 (DB 표 없음)
	implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-redis")

	developmentOnly("org.springframework.boot:spring-boot-devtools") // dev-tools
	// App 실행시 compose.yml 인프라 자동 실행 (bootRun/IDE 실행에만 포함, 운영 jar에서는 제외)
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	testImplementation(testFixtures(project(":common"))) // GlobalConfigTestExecutionListener (테스트마다 EventPublisher 재설정)
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("com.redis:testcontainers-redis") // RedisContainer (Boot BOM 관리 버전). 대화 기록은 진짜 Redis(검색·JSON 기능)로 확인한다
}
