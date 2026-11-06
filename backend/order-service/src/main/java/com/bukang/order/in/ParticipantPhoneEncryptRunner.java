package com.bukang.order.in;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 암호화를 붙이기 전에 평문으로 저장된 참가자·비상 연락처 휴대폰 번호를 앱이 뜰 때 한 번 암호화한다 (BUK-92)
 * Flyway SQL로는 암호화할 수 없어 여기서 한다. 평문은 옛 칸 길이(20자) 안이고 암호문은 44자 이상이라 길이로 가린다
 * 그래서 다시 돌려도 이미 암호화한 값은 건너뛴다. 여러 대가 함께 떠도 읽은 평문이 그대로일 때만 바꿔 두 번 암호화하지 않는다
 * 평문 행을 읽으면 복호화가 실패하므로, 웹 서버·Kafka 리스너·스케줄러가 시작하기 전(모든 빈을 만든 직후, Flyway 뒤)에 돈다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipantPhoneEncryptRunner implements SmartInitializingSingleton {
	private static final int PLAINTEXT_MAX_LENGTH = 20;
	private static final List<String> COLUMNS = List.of("participant_phone", "emergency_contact_phone");

	private final JdbcTemplate jdbcTemplate;
	private final TextEncryptor textEncryptor;

	@Override
	public void afterSingletonsInstantiated() {
		// 실패해도 앱은 띄운다. 남은 평문 행만 읽을 때 실패하고, 다음에 뜰 때 다시 바꾼다
		try {
			int encrypted = encryptPlaintextPhones();
			if (encrypted > 0) {
				log.info("평문 휴대폰 번호 {}칸을 암호화했습니다", encrypted);
			}
		} catch (RuntimeException exception) {
			log.error("평문 휴대폰 번호를 암호화하지 못했습니다", exception);
		}
	}

	public int encryptPlaintextPhones() {
		int encrypted = 0;
		for (String column : COLUMNS) {
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"select id, " + column + " as phone from order_participation where char_length(" + column + ") <= ?",
				PLAINTEXT_MAX_LENGTH);
			for (Map<String, Object> row : rows) {
				String plain = (String)row.get("phone");
				encrypted += jdbcTemplate.update(
					"update order_participation set " + column + " = ? where id = ? and " + column + " = ?",
					textEncryptor.encrypt(plain), row.get("id"), plain);
			}
		}
		return encrypted;
	}
}
