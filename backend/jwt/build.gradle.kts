// JWT 발급·검증 라이브러리 모듈 (게이트웨이와 member-service가 함께 쓴다)
// common은 webmvc·JPA를 함께 가져와 WebFlux 기반 게이트웨이와 충돌하므로, JWT는 웹 의존성 없이 따로 둔다
plugins {
	`java-library`
	id("io.spring.dependency-management")
}

dependencies {
	// JwtProvider의 public 메서드가 jjwt 타입을 노출하지 않으므로 implementation으로 충분하다
	implementation("io.jsonwebtoken:jjwt-api:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

	// jwt.* 설정 바인딩과 JwtProvider 빈 자동 등록
	implementation("org.springframework.boot:spring-boot-autoconfigure")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
}
