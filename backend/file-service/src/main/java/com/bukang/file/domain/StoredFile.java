package com.bukang.file.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.util.Comparator;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.common.shared.file.domain.FileType;
import com.bukang.file.dto.FileDto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 첨부 파일. 본문은 S3에 있고 여기에는 객체 키와 정보만 둔다.
 * 용도(fileType)는 업로드할 때 정하고, 어느 대상에 쓰일지는 모르므로 refType·refId는 대상에 연결할 때 채운다.
 * 이름은 java.io.File과 헷갈리지 않도록 StoredFile로 짓는다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "FILE_FILE",
	uniqueConstraints = @UniqueConstraint(name = "uk_file_s3_key", columnNames = "s3_key"),
	indexes = {
		// 대상별 파일 조회 (대회 상세의 대표·소개 이미지)
		@Index(name = "idx_file_ref", columnList = "ref_type, ref_id, file_type, status"),
		// 오래된 미연결 파일 정리
		@Index(name = "idx_file_status_create", columnList = "status, create_date")
	}
)
public class StoredFile extends BaseIdAndTime {
	// 화면에 보여 줄 순서: 용도(FileType에 적은 순서) → 노출 순서 → ID
	public static final Comparator<StoredFile> DISPLAY_ORDER = Comparator
		.comparing(StoredFile::getFileType)
		.thenComparingInt(StoredFile::getSortNo)
		.thenComparingInt(StoredFile::getId);

	// 연결한 대상의 종류 (대상 엔티티의 getModelTypeCode(), 예: Race)
	@Column(name = "ref_type", length = 50)
	private String refType;

	// 연결한 대상의 ID
	@Column(name = "ref_id")
	private Integer refId;

	// 대상 안에서의 용도. 값은 common의 FileType이 정한다
	// 컬럼 타입을 직접 적으면 Hibernate가 MySQL enum 타입이나 허용 값 CHECK 제약을 만들지 않는다
	// 허용 값은 애플리케이션(Enum)이 지키므로, 값을 추가해도 DB를 고치지 않아도 된다
	@Enumerated(STRING)
	@Column(name = "file_type", nullable = false, columnDefinition = "varchar(20)")
	private FileType fileType;

	@Column(name = "s3_key", nullable = false, length = 500)
	private String s3Key;

	@Column(nullable = false)
	private String originFileNm;

	@Column(nullable = false, length = 100)
	private String contentType;

	// 업로드 URL을 받을 때 신고한 크기 (바이트). 업로드 확인 때 실제 크기와 비교한다
	@Column(nullable = false)
	private long fileSize;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private FileStatus status;

	@Column(nullable = false)
	private int sortNo;

	// 올린 회원 ID
	@Column(nullable = false)
	private int createUser;

	private StoredFile(String s3Key, String originFileNm, FileType fileType, FileFormat format, long fileSize,
		int memberId) {
		this.s3Key = s3Key;
		this.originFileNm = originFileNm;
		this.fileType = fileType;
		this.contentType = format.getContentType();
		this.fileSize = fileSize;
		this.createUser = memberId;
		this.status = FileStatus.PENDING;
	}

	public static StoredFile pending(String s3Key, String originFileNm, FileType fileType, FileFormat format,
		long fileSize, int memberId) {
		return new StoredFile(s3Key, originFileNm, fileType, format, fileSize, memberId);
	}

	public boolean isOwnedBy(int memberId) {
		return createUser == memberId;
	}

	public boolean isPending() {
		return status == FileStatus.PENDING;
	}

	public boolean isDeleted() {
		return status == FileStatus.DELETED;
	}

	public boolean isUploaded() {
		return status == FileStatus.UPLOADED;
	}

	public boolean isActive() {
		return status == FileStatus.ACTIVE;
	}

	public boolean isLinkedTo(String targetRefType, int targetRefId) {
		return isActive() && targetRefType.equals(refType) && refId != null && refId == targetRefId;
	}

	// 확정 경로에 객체가 있는 상태 (이미지 URL을 만들 수 있다)
	public boolean hasConfirmedObject() {
		return isUploaded() || isActive();
	}

	public void linkTo(String targetRefType, int targetRefId, int targetSortNo) {
		this.refType = targetRefType;
		this.refId = targetRefId;
		this.sortNo = targetSortNo;
		this.status = FileStatus.ACTIVE;
	}

	public FileFormat getFormat() {
		return FileFormat.fromContentType(contentType)
			.orElseThrow(() -> new IllegalStateException("저장된 형식을 알 수 없습니다: " + contentType));
	}

	// 확인을 마친 파일은 업로드 경로에서 확정 경로로 옮겨진다
	public void confirmUpload(String confirmedS3Key) {
		this.s3Key = confirmedS3Key;
		this.status = FileStatus.UPLOADED;
	}

	public void delete() {
		this.status = FileStatus.DELETED;
	}

	public FileDto toDto(String url) {
		return new FileDto(
			getId(),
			getCreateDate(),
			getModifyDate(),
			createUser,
			originFileNm,
			contentType,
			fileSize,
			status,
			refType,
			refId,
			fileType,
			sortNo,
			url
		);
	}
}
