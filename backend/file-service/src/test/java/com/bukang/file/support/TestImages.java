package com.bukang.file.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 형식 판별·크기 검사 테스트용 파일 본문.
 * 이미지는 형식 판별 바이트와 가로·세로 칸까지만 실제 형식과 같다 (그림 데이터는 없다).
 * 크기가 관심 없는 테스트는 상수(100×100)를, 크기가 관심인 테스트는 png(가로, 세로) 같은 메서드를 쓴다.
 */
public final class TestImages {
	// 규격(최대 4000px, 소개 이미지 최소 가로 1080px)과 상관없는 테스트에 쓰는 크기. 대표 이미지로 올리면 통과한다
	private static final int DEFAULT_SIZE = 100;

	public static final byte[] PNG = png(DEFAULT_SIZE, DEFAULT_SIZE);
	public static final byte[] JPEG = jpeg(DEFAULT_SIZE, DEFAULT_SIZE);
	public static final byte[] WEBP = webpLossy(DEFAULT_SIZE, DEFAULT_SIZE);
	// 상자 크기 4바이트 + "ftyp" + 브랜드 4바이트 + 부 버전 4바이트
	public static final byte[] MP4 = {
		0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0x00, 0x00, 0x02, 0x00
	};
	public static final byte[] MOV = {
		0x00, 0x00, 0x00, 0x14, 'f', 't', 'y', 'p', 'q', 't', ' ', ' ', 0x00, 0x00, 0x00, 0x00
	};
	// MP4와 같은 상자 구조지만 오디오 파일이다
	public static final byte[] M4A = {
		0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' ', 0x00, 0x00, 0x00, 0x00
	};
	// ZIP 파일 시작 부분
	public static final byte[] XLSX = {
		'P', 'K', 0x03, 0x04, 0x14, 0x00, 0x06, 0x00, 0x08, 0x00, 0x00, 0x00
	};
	// 옛 오피스 파일(OLE) 시작 부분
	public static final byte[] XLS = {
		(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0, (byte)0xA1, (byte)0xB1, 0x1A, (byte)0xE1, 0x00, 0x00, 0x00, 0x00
	};
	// 확장자만 .png로 바꾼 텍스트 파일
	public static final byte[] TEXT = "hello, this is not an image".getBytes();

	private TestImages() {
	}

	// 서명 → IHDR 상자(길이, 이름, 가로, 세로, 비트 깊이 등 5바이트)
	public static byte[] png(int width, int height) {
		Bytes bytes = new Bytes();
		bytes.add(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
		bytes.bigEndian(13, 4).ascii("IHDR").bigEndian(width, 4).bigEndian(height, 4);
		bytes.add(0x08, 0x06, 0x00, 0x00, 0x00);
		return bytes.toArray();
	}

	// SOI → APP0(JFIF) → SOF0(세로, 가로) → EOI
	public static byte[] jpeg(int width, int height) {
		return jpegWithPhotoInfo(width, height, 0);
	}

	// 휴대폰 사진처럼 SOF 앞에 사진 정보(EXIF, APP1) 칸을 photoInfoLength바이트만큼 넣는다. 0이면 넣지 않는다
	public static byte[] jpegWithPhotoInfo(int width, int height, int photoInfoLength) {
		Bytes bytes = new Bytes();
		bytes.add(0xFF, 0xD8);
		if (photoInfoLength > 0) {
			bytes.add(0xFF, 0xE1).bigEndian(photoInfoLength + 2, 2).ascii("Exif").add(0x00, 0x00);
			bytes.zeros(photoInfoLength - 6);
		}
		bytes.add(0xFF, 0xE0).bigEndian(16, 2).ascii("JFIF");
		bytes.add(0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00);
		bytes.add(0xFF, 0xC0).bigEndian(17, 2).add(0x08).bigEndian(height, 2).bigEndian(width, 2);
		bytes.add(0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01);
		bytes.add(0xFF, 0xD9);
		return bytes.toArray();
	}

	// 손실 압축(VP8): 프레임 머리 3바이트 + 시작 표시(9D 01 2A) 뒤에 가로·세로 2바이트씩
	public static byte[] webpLossy(int width, int height) {
		Bytes chunk = new Bytes();
		chunk.add(0x00, 0x00, 0x00, 0x9D, 0x01, 0x2A).littleEndian(width, 2).littleEndian(height, 2);
		return webp("VP8 ", chunk);
	}

	// 무손실 압축(VP8L): 서명(0x2F) 뒤 4바이트에 (가로-1) 14비트, (세로-1) 14비트
	public static byte[] webpLossless(int width, int height) {
		Bytes chunk = new Bytes();
		chunk.add(0x2F).littleEndian((width - 1) | ((height - 1) << 14), 4);
		return webp("VP8L", chunk);
	}

	// 확장(VP8X): 플래그 4바이트 뒤에 (가로-1), (세로-1) 3바이트씩
	public static byte[] webpExtended(int width, int height) {
		Bytes chunk = new Bytes();
		chunk.add(0x00, 0x00, 0x00, 0x00).littleEndian(width - 1, 3).littleEndian(height - 1, 3);
		return webp("VP8X", chunk);
	}

	// "RIFF" + 파일 크기 + "WEBP" + 상자 이름 + 상자 크기 + 상자 내용
	private static byte[] webp(String chunkName, Bytes chunk) {
		byte[] data = chunk.toArray();
		Bytes bytes = new Bytes();
		bytes.ascii("RIFF").littleEndian(4 + 8 + data.length, 4).ascii("WEBP");
		bytes.ascii(chunkName).littleEndian(data.length, 4).add(data);
		return bytes.toArray();
	}

	private static final class Bytes {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		Bytes add(int... values) {
			for (int value : values) {
				out.write(value);
			}
			return this;
		}

		Bytes add(byte[] values) {
			out.writeBytes(values);
			return this;
		}

		Bytes ascii(String text) {
			out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
			return this;
		}

		Bytes zeros(int length) {
			out.writeBytes(new byte[length]);
			return this;
		}

		Bytes bigEndian(int value, int length) {
			for (int i = length - 1; i >= 0; i--) {
				out.write(value >>> (8 * i));
			}
			return this;
		}

		Bytes littleEndian(int value, int length) {
			for (int i = 0; i < length; i++) {
				out.write(value >>> (8 * i));
			}
			return this;
		}

		byte[] toArray() {
			return out.toByteArray();
		}
	}
}
