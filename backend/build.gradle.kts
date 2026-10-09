import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

// 루트 프로젝트에는 코드가 없고, 모든 모듈에 공통으로 적용할 설정만 둔다
// 플러그인 버전은 여기서 한 번만 정하고, 각 모듈은 버전 없이 id만 적는다
plugins {
	id("org.springframework.boot") version "4.1.1" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
	group = "com"
	version = "0.0.1-SNAPSHOT"

	repositories {
		mavenCentral()
	}
}

subprojects {
	apply(plugin = "java")
	apply(plugin = "checkstyle")
	apply(plugin = "io.spring.dependency-management")

	configure<JavaPluginExtension> {
		toolchain {
			languageVersion = JavaLanguageVersion.of(25)
		}
	}

	// Spring Boot BOM을 모든 모듈에 적용한다
	// (boot 플러그인을 쓰지 않는 common 모듈도 버전 없이 Spring 의존성을 선언할 수 있다)
	configure<DependencyManagementExtension> {
		imports {
			mavenBom(SpringBootPlugin.BOM_COORDINATES)
		}
	}

	dependencies {
		"compileOnly"("org.projectlombok:lombok") // lombok
		"annotationProcessor"("org.projectlombok:lombok")
		"testCompileOnly"("org.projectlombok:lombok")
		"testAnnotationProcessor"("org.projectlombok:lombok")

		"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
	}

	// 캠퍼스 핵데이 Java 코딩 컨벤션 검사 (규칙 파일은 원격 저장소에서 직접 참조)
	configure<CheckstyleExtension> {
		toolVersion = "14.3.0"
		config = resources.text.fromUri(
			"https://raw.githubusercontent.com/naver/hackday-conventions-java/master/rule-config/naver-checkstyle-rules.xml"
		)
		// 규칙 파일이 참조하는 suppression 파일 경로 (optional 이므로 파일이 없으면 무시된다)
		configProperties["suppressionFile"] = "${rootDir}/config/checkstyle/suppressions.xml"
		// 위반 시 빌드를 실패시키지 않고 경고만 출력한다
		isIgnoreFailures = true
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}
}
