package com.bukang.file.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 이미지 파일의 머리 부분에서 가로·세로를 읽는다. 이미지 전체를 풀지 않으므로 큰 이미지도 메모리를 거의 쓰지 않는다.
 * 형식(매직 바이트)은 FileFormat.matches로 이미 확인한 바이트를 받는다. 머리 부분이 깨졌으면 빈 값을 돌려준다.
 */
final class ImageSizeReader {
	// PNG: 서명 8바이트 + IHDR 상자(길이 4 + 이름 4) 뒤에 가로 4바이트, 세로 4바이트
	private static final int PNG_WIDTH_OFFSET = 16;
	private static final int PNG_HEIGHT_OFFSET = 20;

	// JPEG: 0xFF 뒤에 오는 표시(마커). SOF0~SOF15 안에 크기가 있다
	private static final int JPEG_MARKER_PREFIX = 0xFF;
	private static final int JPEG_SOF_FIRST = 0xC0;
	private static final int JPEG_SOF_LAST = 0xCF;
	// C4(허프만 표), C8(예약), CC(산술 부호 표)는 SOF 범위 안이지만 크기를 담지 않는다
	private static final int JPEG_DHT = 0xC4;
	private static final int JPEG_JPG = 0xC8;
	private static final int JPEG_DAC = 0xCC;
	// 길이 칸 없이 표시만 있는 것: TEM, RST0~RST7, SOI
	private static final int JPEG_TEM = 0x01;
	private static final int JPEG_RST_FIRST = 0xD0;
	private static final int JPEG_RST_LAST = 0xD7;
	private static final int JPEG_SOI = 0xD8;
	// 이미지 끝(EOI)이나 본문 시작(SOS)까지 SOF가 없으면 읽지 못한 것으로 본다
	private static final int JPEG_EOI = 0xD9;
	private static final int JPEG_SOS = 0xDA;
	// SOF 칸: 길이 2바이트 + 정밀도 1바이트 뒤에 세로 2바이트, 가로 2바이트
	private static final int JPEG_SOF_HEIGHT_OFFSET = 3;
	private static final int JPEG_SOF_WIDTH_OFFSET = 5;

	// WebP: "RIFF" + 크기 + "WEBP" 뒤 12바이트부터 첫 상자 이름(4바이트)
	private static final int WEBP_CHUNK_OFFSET = 12;
	private static final int WEBP_CHUNK_DATA_OFFSET = 20;
	// 손실 압축(VP8): 프레임 머리 3바이트 + 시작 표시 3바이트 뒤에 가로·세로 각 2바이트(아래 14비트)
	private static final int VP8_WIDTH_OFFSET = 26;
	private static final int VP8_HEIGHT_OFFSET = 28;
	private static final int VP8_SIZE_MASK = 0x3FFF;
	// 무손실 압축(VP8L): 서명 1바이트(0x2F) 뒤 4바이트에 (가로-1) 14비트, (세로-1) 14비트
	private static final int VP8L_SIGNATURE = 0x2F;
	private static final int VP8L_BITS_OFFSET = 21;
	// 확장(VP8X): 플래그 4바이트 뒤에 (가로-1) 3바이트, (세로-1) 3바이트
	private static final int VP8X_WIDTH_OFFSET = 24;
	private static final int VP8X_HEIGHT_OFFSET = 27;

	private ImageSizeReader() {
	}

	static Optional<ImageSize> png(byte[] bytes) {
		if (bytes.length < PNG_HEIGHT_OFFSET + 4) {
			return Optional.empty();
		}
		return sizeOf(bigEndian(bytes, PNG_WIDTH_OFFSET, 4), bigEndian(bytes, PNG_HEIGHT_OFFSET, 4));
	}

	// 사진 정보(EXIF) 같은 칸이 SOF보다 앞에 올 수 있어, 칸 길이를 따라 SOF가 나올 때까지 건너뛴다
	static Optional<ImageSize> jpeg(byte[] bytes) {
		int position = 2;
		while (position + 1 < bytes.length) {
			if (unsigned(bytes, position) != JPEG_MARKER_PREFIX) {
				return Optional.empty();
			}
			int marker = unsigned(bytes, position + 1);
			// 표시 앞에 0xFF가 여러 개 붙을 수 있다 (채움 바이트)
			if (marker == JPEG_MARKER_PREFIX) {
				position++;
				continue;
			}
			position += 2;
			if (isStandalone(marker)) {
				continue;
			}
			if (marker == JPEG_EOI || marker == JPEG_SOS || position + 2 > bytes.length) {
				return Optional.empty();
			}
			if (isStartOfFrame(marker)) {
				if (position + JPEG_SOF_WIDTH_OFFSET + 2 > bytes.length) {
					return Optional.empty();
				}
				int height = bigEndian(bytes, position + JPEG_SOF_HEIGHT_OFFSET, 2);
				int width = bigEndian(bytes, position + JPEG_SOF_WIDTH_OFFSET, 2);
				return sizeOf(width, height);
			}
			position += bigEndian(bytes, position, 2);
		}
		return Optional.empty();
	}

	static Optional<ImageSize> webp(byte[] bytes) {
		if (bytes.length < WEBP_CHUNK_DATA_OFFSET) {
			return Optional.empty();
		}
		String chunk = new String(bytes, WEBP_CHUNK_OFFSET, 4, StandardCharsets.US_ASCII);
		return switch (chunk) {
			case "VP8 " -> webpLossy(bytes);
			case "VP8L" -> webpLossless(bytes);
			case "VP8X" -> webpExtended(bytes);
			default -> Optional.empty();
		};
	}

	private static Optional<ImageSize> webpLossy(byte[] bytes) {
		if (bytes.length < VP8_HEIGHT_OFFSET + 2) {
			return Optional.empty();
		}
		int width = littleEndian(bytes, VP8_WIDTH_OFFSET, 2) & VP8_SIZE_MASK;
		int height = littleEndian(bytes, VP8_HEIGHT_OFFSET, 2) & VP8_SIZE_MASK;
		return sizeOf(width, height);
	}

	private static Optional<ImageSize> webpLossless(byte[] bytes) {
		if (bytes.length < VP8L_BITS_OFFSET + 4 || unsigned(bytes, WEBP_CHUNK_DATA_OFFSET) != VP8L_SIGNATURE) {
			return Optional.empty();
		}
		int bits = littleEndian(bytes, VP8L_BITS_OFFSET, 4);
		int width = (bits & VP8_SIZE_MASK) + 1;
		int height = ((bits >>> 14) & VP8_SIZE_MASK) + 1;
		return sizeOf(width, height);
	}

	private static Optional<ImageSize> webpExtended(byte[] bytes) {
		if (bytes.length < VP8X_HEIGHT_OFFSET + 3) {
			return Optional.empty();
		}
		int width = littleEndian(bytes, VP8X_WIDTH_OFFSET, 3) + 1;
		int height = littleEndian(bytes, VP8X_HEIGHT_OFFSET, 3) + 1;
		return sizeOf(width, height);
	}

	// 0 이하(깨진 머리, PNG의 4바이트 값이 int 범위를 넘음)는 읽지 못한 것으로 본다
	private static Optional<ImageSize> sizeOf(int width, int height) {
		if (width <= 0 || height <= 0) {
			return Optional.empty();
		}
		return Optional.of(new ImageSize(width, height));
	}

	private static boolean isStandalone(int marker) {
		return marker == JPEG_TEM || marker == JPEG_SOI || (marker >= JPEG_RST_FIRST && marker <= JPEG_RST_LAST);
	}

	private static boolean isStartOfFrame(int marker) {
		return marker >= JPEG_SOF_FIRST && marker <= JPEG_SOF_LAST
			&& marker != JPEG_DHT && marker != JPEG_JPG && marker != JPEG_DAC;
	}

	private static int unsigned(byte[] bytes, int index) {
		return bytes[index] & 0xFF;
	}

	private static int bigEndian(byte[] bytes, int offset, int length) {
		int value = 0;
		for (int i = 0; i < length; i++) {
			value = (value << 8) | unsigned(bytes, offset + i);
		}
		return value;
	}

	private static int littleEndian(byte[] bytes, int offset, int length) {
		int value = 0;
		for (int i = length - 1; i >= 0; i--) {
			value = (value << 8) | unsigned(bytes, offset + i);
		}
		return value;
	}
}
