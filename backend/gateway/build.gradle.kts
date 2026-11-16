// API 게이트웨이: Nginx 뒤에서 /api 요청을 받아 경로에 맞는 서비스로 넘긴다
// WebFlux(Netty) 기반이라 webmvc를 가져오는 common 모듈은 넣지 않는다
plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

// Spring Cloud 2025.1.x가 Spring Boot 4.x 대응 버전이다 (Boot 4.1.x는 2025.1.2 이상)
extra["springCloudVersion"] = "2025.1.3"

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

dependencies {
	implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
	// 토큰 검사(JwtProvider). 웹 의존성이 없는 모듈이라 WebFlux와 충돌하지 않는다
	implementation(project(":jwt"))
	// 로그아웃한 Access 토큰 표시를 reactive로 읽는다. member-service와 같은 Redis에 붙는다
	implementation("org.springframework.boot:spring-boot-starter-data-redis")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	// 테스트에서 JwtProvider가 만들지 않는 모양의 토큰(Refresh 등)을 직접 서명해 만든다
	testImplementation("io.jsonwebtoken:jjwt-api:0.13.0")
}
