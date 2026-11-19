package com.bukang.member.recordcert.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기록증: 참가자가 올린 이미지 한 장과, 참가자가 확정한 값 (BUK-273)
 * 원본 사진은 file-service에 그대로 두고, 참가자가 짚은 읽을 부분과 칸별 상자를 원본 픽셀 좌표로 남긴다
 * 기록증은 회원에게 속해 제출이 여러 개일 수 있고(다음 단계의 재사용), 참가권이 환불돼도 남는다
 * 제출 API(BUK-274)가 제출과 함께 만든다
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "member_record_cert", indexes = @Index(name = "idx_record_cert_member", columnList = "member_id"))
public class RecordCert {
	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "record_cert_id")
	private long id;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "file_id", nullable = false)
	private long fileId;

	// 읽을 부분 (원본 픽셀)
	@Column(name = "roi_x", nullable = false)
	private int roiX;

	@Column(name = "roi_y", nullable = false)
	private int roiY;

	@Column(name = "roi_width", nullable = false)
	private int roiWidth;

	@Column(name = "roi_height", nullable = false)
	private int roiHeight;

	@Column(name = "net_time_seconds", nullable = false)
	private int netTimeSeconds;

	@Column(name = "race_date", nullable = false)
	private LocalDate raceDate;

	@Enumerated(STRING)
	@Column(name = "course_class", nullable = false, columnDefinition = "varchar(10)")
	private CourseClass courseClass;

	@Column(name = "bib", length = 30)
	private String bib;

	// 서버 확인할 칸별 상자 (원본 픽셀 네 점, JSON). 참가자가 직접 입력한 칸은 빠진다
	@Column(name = "field_boxes", columnDefinition = "text")
	private String fieldBoxes;

	// 브라우저 OCR 명세 버전과 인식 모델 sha256. 서버 확인이 같은 명세 · 모델일 때만 읽는다 (server-read-spec)
	@Column(name = "spec_version", length = 20)
	private String specVersion;

	@Column(name = "rec_model_sha256", length = 64)
	private String recModelSha256;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	// 끝난 지 30일 지나 원본 사진을 file-service에서 지운 시각 (BUK-278). 값은 남는다
	@Column(name = "file_deleted_at")
	private LocalDateTime fileDeletedAt;

	private RecordCert(long memberId, long fileId, Roi roi, CertValues values, String fieldBoxes,
		String specVersion, String recModelSha256, LocalDateTime now) {
		this.memberId = memberId;
		this.fileId = fileId;
		this.roiX = roi.x();
		this.roiY = roi.y();
		this.roiWidth = roi.width();
		this.roiHeight = roi.height();
		this.netTimeSeconds = values.netSeconds();
		this.raceDate = values.raceDate();
		this.courseClass = values.courseClass();
		this.bib = values.bib();
		this.fieldBoxes = fieldBoxes;
		this.specVersion = specVersion;
		this.recModelSha256 = recModelSha256;
		this.createdAt = now;
	}

	// 확정 값 세 개(넷타임 · 대회일 · 종목)는 제출 요청 검사가 이미 확인했다
	public static RecordCert create(long memberId, long fileId, Roi roi, CertValues values, String fieldBoxes,
		String specVersion, String recModelSha256, LocalDateTime now) {
		return new RecordCert(memberId, fileId, roi, values, fieldBoxes, specVersion, recModelSha256, now);
	}

	public CertValues values() {
		return new CertValues(netTimeSeconds, raceDate, courseClass, bib);
	}
}
