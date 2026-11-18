package com.bukang.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

// common은 통째로 스캔하지 않고 쓰는 것만 고른다: 오류 응답(GlobalExceptionHandler)과 @LoginMemberId 리졸버
// (통째로 스캔하면 알림 보내기(NotificationKafkaSender)까지 뜬다. Kafka는 기록증 서버 확인(BUK-275)에만 쓴다)
// 기록증 서버 확인은 JSON 변환(JsonConverter)과 실패 알림(SlackNotifier)을 쓴다
// AI 서비스는 DB 표가 없다(대화 기록은 Redis). common이 JPA를 함께 가져오므로 DB 자동 설정을 끈다
// 제외 목록을 yaml(spring.autoconfigure.exclude)에 두면 프로파일 yaml이 통째로 덮어쓰므로 여기에 둔다
@SpringBootApplication(
	scanBasePackages = {"com.bukang.ai", "com.bukang.common.global.exception", "com.bukang.common.shared.member.web",
		"com.bukang.common.global.json", "com.bukang.common.global.notify"},
	exclude = {
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class,
		DataJpaRepositoriesAutoConfiguration.class
	}
)
public class AiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiApplication.class, args);
	}

}
