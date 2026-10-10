package com.bukang.file.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.bukang.common.shared.file.domain.FileKind;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 받을 수 있는 파일 형식 (정책: 이미지 JPG·PNG·WebP, 동영상 MP4·MOV, 엑셀 XLSX·XLS)
 * 클라이언트가 보낸 Content-Type은 바꿔 보낼 수 있으므로, 업로드 뒤 파일 앞부분(매직 바이트)으로 다시 확인한다.
 * 엑셀은 앞부분이 다른 문서 파일과 같아서 엑셀인지까지는 알 수 없다. 명단을 읽을 때 다시 확인한다.
 */
@Getter
@RequiredArgsConstructor
public enum FileFormat {
	JPEG(FileKind.IMAGE, "JPG", "image/jpeg", "jpg"),
	PNG(FileKind.IMAGE, "PNG", "image/png", "png"),
	WEBP(FileKind.IMAGE, "WebP", "image/webp", "webp"),
	MP4(FileKind.VIDEO, "MP4", "video/mp4", "mp4"),
	MOV(FileKind.VIDEO, "MOV", "video/quicktime", "mov"),
	XLSX(FileKind.EXCEL, "XLSX", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
	XLS(FileKind.EXCEL, "XLS", "application/vnd.ms-excel", "xls");

	// 형식 판별에 필요한 앞부분 길이
	// WebP: "RIFF" 4바이트 + 파일 크기 4바이트 + "WEBP" 4바이트
	// MP4·MOV: 상자 크기 4바이트 + "ftyp" 4바이트 + 브랜드 4바이트
	public static final int SIGNATURE_LENGTH = 12;

	private static final int[] JPEG_SIGNATURE = {0xFF, 0xD8, 0xFF};
	private static final int[] PNG_SIGNATURE = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
	private static final int[] RIFF = {'R', 'I', 'F', 'F'};
	private static final int[] WEBP_MARK = {'W', 'E', 'B', 'P'};
	private static final int WEBP_MARK_OFFSET = 8;
	private static final int[] FTYP = {'f', 't', 'y', 'p'};
	private static final int FTYP_OFFSET = 4;
	private static final int BRAND_OFFSET = 8;
	private static final int BRAND_LENGTH = 4;
	// MOV의 브랜드는 "qt" 뒤에 공백 2칸이다
	private static final String MOV_BRAND = "qt  ";
	// 흔히 쓰는 MP4 브랜드. 정상 영상이 거절되면 그 브랜드를 추가한다
	private static final Set<String> MP4_BRANDS = Set.of("isom", "iso2", "mp41", "mp42", "avc1");
	// XLSX는 ZIP 파일이라 ZIP인지까지만 안다 (docx, zip도 같은 값)
	private static final int[] ZIP_SIGNATURE = {'P', 'K', 0x03, 0x04};
	// XLS는 옛 오피스 파일 형식이라 doc, ppt도 같은 값이다
	private static final int[] OLE_SIGNATURE = {0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1};

	private final FileKind kind;
	// 메시지에 쓰는 이름
	private final String label;
	private final String contentType;
	private final String extension;

	public static Optional<FileFormat> fromContentType(String contentType) {
		if (contentType == null) {
			return Optional.empty();
		}
		return Arrays.stream(values())
			.filter(format -> format.contentType.equalsIgnoreCase(contentType.trim()))
			.findFirst();
	}

	// 메시지에 쓰는 형식 이름 목록 (예: "MP4, MOV")
	public static String labelsOf(FileKind kind) {
		return Arrays.stream(values())
			.filter(format -> format.kind == kind)
			.map(FileFormat::getLabel)
			.collect(Collectors.joining(", "));
	}

	public boolean matches(byte[] head) {
		return switch (this) {
			case JPEG -> startsWith(head, 0, JPEG_SIGNATURE);
			case PNG -> startsWith(head, 0, PNG_SIGNATURE);
			case WEBP -> startsWith(head, 0, RIFF) && startsWith(head, WEBP_MARK_OFFSET, WEBP_MARK);
			case MP4 -> startsWith(head, FTYP_OFFSET, FTYP) && MP4_BRANDS.contains(brandOf(head));
			case MOV -> startsWith(head, FTYP_OFFSET, FTYP) && MOV_BRAND.equals(brandOf(head));
			case XLSX -> startsWith(head, 0, ZIP_SIGNATURE);
			case XLS -> startsWith(head, 0, OLE_SIGNATURE);
		};
	}

	// ftyp 바로 뒤 4바이트(주 브랜드). 앞부분이 짧으면 빈 문자열
	private static String brandOf(byte[] head) {
		if (head.length < BRAND_OFFSET + BRAND_LENGTH) {
			return "";
		}
		return new String(head, BRAND_OFFSET, BRAND_LENGTH, StandardCharsets.US_ASCII);
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
