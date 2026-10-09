package com.bukang.file.domain;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 받을 수 있는 이미지 형식 (정책: JPG·PNG·WebP)
 * 클라이언트가 보낸 Content-Type은 바꿔 보낼 수 있으므로, 업로드 뒤 파일 앞부분(매직 바이트)으로 다시 확인한다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageFormat {
	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	// 형식 판별에 필요한 앞부분 길이 (WebP: "RIFF" 4바이트 + 파일 크기 4바이트 + "WEBP" 4바이트)
	public static final int SIGNATURE_LENGTH = 12;

	private static final int[] JPEG_SIGNATURE = {0xFF, 0xD8, 0xFF};
	private static final int[] PNG_SIGNATURE = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
	private static final int[] RIFF = {'R', 'I', 'F', 'F'};
	private static final int[] WEBP_MARK = {'W', 'E', 'B', 'P'};
	private static final int WEBP_MARK_OFFSET = 8;

	private final String contentType;
	private final String extension;

	public static Optional<ImageFormat> fromContentType(String contentType) {
		if (contentType == null) {
			return Optional.empty();
		}
		return Arrays.stream(values())
			.filter(format -> format.contentType.equalsIgnoreCase(contentType.trim()))
			.findFirst();
	}

	public boolean matches(byte[] head) {
		return switch (this) {
			case JPEG -> startsWith(head, 0, JPEG_SIGNATURE);
			case PNG -> startsWith(head, 0, PNG_SIGNATURE);
			case WEBP -> startsWith(head, 0, RIFF) && startsWith(head, WEBP_MARK_OFFSET, WEBP_MARK);
		};
	}

	private static boolean startsWith(byte[] bytes, int offset, int[] signature) {
		if (bytes.length < offset + signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if ((bytes[offset + i] & 0xFF) != signature[i]) {
				return false;
			}
		}
		return true;
	}
}
