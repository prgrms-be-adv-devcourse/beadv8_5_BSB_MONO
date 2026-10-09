package com.bukang.file.support;

/**
 * 형식 판별 테스트용 파일 본문 (앞부분만 실제 형식과 같다)
 */
public final class TestImages {
	public static final byte[] PNG = {
		(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
	};
	public static final byte[] JPEG = {
		(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
	};
	public static final byte[] WEBP = {
		'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
	};
	// 확장자만 .png로 바꾼 텍스트 파일
	public static final byte[] TEXT = "hello, this is not an image".getBytes();

	private TestImages() {
	}
}
