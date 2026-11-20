package com.bukang.member.recordcert.dto;

import com.bukang.member.recordcert.domain.RecordCert;
import com.bukang.member.recordcert.domain.RecordCertSubmission;

import io.swagger.v3.oas.annotations.media.Schema;

// 판매자 심사 상세 (BUK-276). 목록 한 줄 + 원본 사진 보기 주소 · 읽을 부분 · 칸별 상자 · 배번
public record SellerRecordCertDetailDto(
	@Schema(description = "목록과 같은 값")
	SellerRecordCertSubmissionDto submission,
	@Schema(description = "원본 사진 보기 주소. 30분 뒤 만료되므로 화면을 다시 열면 새로 받는다",
		example = "https://bucket.s3.ap-northeast-2.amazonaws.com/prod/files/77.jpg?X-Amz-Expires=1800")
	String fileUrl,
	@Schema(description = "참가자가 짚은 읽을 부분 (원본 픽셀)")
	Roi roi,
	@Schema(description = "칸별 서버 확인 상자 (원본 픽셀 네 점, JSON 문자열). 참가자가 직접 입력한 칸은 빠진다",
		example = "{\"NET_TIME\":[[120,340],[380,340],[380,390],[120,390]]}")
	String fieldBoxes,
	@Schema(description = "배번. 기록증에 없으면 null", example = "M-8363")
	String bib
) {
	public static SellerRecordCertDetailDto of(RecordCertSubmission submission, RecordCert cert, String fileUrl) {
		return new SellerRecordCertDetailDto(SellerRecordCertSubmissionDto.of(submission, cert), fileUrl,
			new Roi(cert.getRoiX(), cert.getRoiY(), cert.getRoiWidth(), cert.getRoiHeight()), cert.getFieldBoxes(),
			cert.getBib());
	}

	public record Roi(int x, int y, int width, int height) {
	}
}
