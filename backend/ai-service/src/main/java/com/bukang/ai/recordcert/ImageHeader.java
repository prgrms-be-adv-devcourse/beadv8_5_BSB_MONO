package com.bukang.ai.recordcert;

/**
 * 사진을 풀기 전에 머리 부분만 보고 픽셀 수를 구한다. 작은 파일이 아주 큰 그림으로 풀려 메모리를 다 쓰는 것을 막는다
 * 받는 형식은 파일 서비스와 같은 JPG · PNG · WebP. 모르는 형식이나 머리가 깨졌으면 -1
 */
final class ImageHeader {
	private ImageHeader() {
	}

	static long pixels(byte[] img) {
		if (img.length >= 24 && (img[0] & 0xff) == 0x89 && img[1] == 'P' && img[2] == 'N' && img[3] == 'G') {
			return (long)be32(img, 16) * be32(img, 20); // IHDR 너비 · 높이
		}
		if (img.length >= 4 && (img[0] & 0xff) == 0xFF && (img[1] & 0xff) == 0xD8) {
			return jpeg(img);
		}
		if (img.length >= 30 && ascii(img, 0, "RIFF") && ascii(img, 8, "WEBP")) {
			return webp(img);
		}
		return -1;
	}

	// 표시 조각(마커)을 따라가 SOF(크기 정보)를 찾는다. DHT(C4) · JPG(C8) · DAC(CC)는 SOF가 아니다
	private static long jpeg(byte[] img) {
		int pos = 2;
		while (pos + 9 < img.length) {
			if ((img[pos] & 0xff) != 0xFF) {
				return -1;
			}
			int marker = img[pos + 1] & 0xff;
			if (marker == 0xFF) {
				pos++;
				continue;
			}
			boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
			if (sof) {
				return (long)be16(img, pos + 5) * be16(img, pos + 7);
			}
			pos += 2 + be16(img, pos + 2);
		}
		return -1;
	}

	private static long webp(byte[] img) {
		if (ascii(img, 12, "VP8 ")) {
			return (long)(le16(img, 26) & 0x3fff) * (le16(img, 28) & 0x3fff);
		}
		if (ascii(img, 12, "VP8L")) {
			long bits = le16(img, 21) | (long)le16(img, 23) << 16;
			return ((bits & 0x3fff) + 1) * (((bits >> 14) & 0x3fff) + 1);
		}
		if (ascii(img, 12, "VP8X")) {
			return (le24(img, 24) + 1L) * (le24(img, 27) + 1L);
		}
		return -1;
	}

	private static boolean ascii(byte[] img, int at, String text) {
		for (int i = 0; i < text.length(); i++) {
			if (img[at + i] != text.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static int be16(byte[] img, int at) {
		return (img[at] & 0xff) << 8 | img[at + 1] & 0xff;
	}

	private static long be32(byte[] img, int at) {
		return (long)be16(img, at) << 16 | be16(img, at + 2);
	}

	private static int le16(byte[] img, int at) {
		return img[at] & 0xff | (img[at + 1] & 0xff) << 8;
	}

	private static int le24(byte[] img, int at) {
		return le16(img, at) | (img[at + 2] & 0xff) << 16;
	}
}
