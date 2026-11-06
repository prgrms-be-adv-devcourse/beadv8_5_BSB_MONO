package com.bukang.common.global.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * 민감정보 암호화(AES-256-GCM)에 쓰는 TextEncryptor 빈 설정.
 * 모든 서비스가 common을 스캔하므로, crypto.enabled: true로 켠 서비스(member, cash, order)에서만 만든다.
 * crypto.password 유무로 정하지 않는 이유: compose는 모든 컨테이너에 .env를 넣고, 환경 변수 CRYPTO_PASSWORD가
 * crypto.password로 읽혀 켜지 않은 서비스에서도 조건이 맞아 버린다
 */
@Configuration
@ConditionalOnProperty(prefix = "crypto", name = "enabled", havingValue = "true")
public class CryptoConfig {
	@Value("${crypto.password}")
	private String cryptoPassword;
	@Value("${crypto.salt}")
	private String cryptoSalt;

	// AES-256-GCM (password와 hex salt로 PBKDF2 키 유도)
	// 키 유도가 일부러 느리게 설계돼 있으므로 빈으로 한 번만 만들어 재사용한다
	// Encryptors.delux()는 지원 중단(deprecated)되어 AesGcmBytesEncryptor + Base64TextEncryptor 사용
	@Bean
	public TextEncryptor textEncryptor() {
		return new Base64TextEncryptor(AesGcmBytesEncryptor.withPassword(cryptoPassword, cryptoSalt).build());
	}
}
