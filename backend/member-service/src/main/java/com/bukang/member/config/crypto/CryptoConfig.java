package com.bukang.member.config.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
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
