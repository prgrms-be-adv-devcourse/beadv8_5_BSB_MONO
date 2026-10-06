package com.bukang.global.config.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

public class Base64TextEncryptor implements TextEncryptor {
	private final BytesEncryptor bytesEncryptor;

	public Base64TextEncryptor(BytesEncryptor bytesEncryptor) {
		this.bytesEncryptor = bytesEncryptor;
	}

	@Override
	public String encrypt(String text) {
		byte[] encrypted = bytesEncryptor.encrypt(text.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(encrypted);
	}

	@Override
	public String decrypt(String encryptedText) {
		byte[] decrypted = bytesEncryptor.decrypt(Base64.getDecoder().decode(encryptedText));
		return new String(decrypted, StandardCharsets.UTF_8);
	}
}
