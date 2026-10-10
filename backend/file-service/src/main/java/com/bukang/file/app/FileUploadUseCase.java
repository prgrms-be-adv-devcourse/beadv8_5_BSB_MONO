package com.bukang.file.app;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.file.domain.FileKind;
import com.bukang.common.shared.file.domain.FileType;
import com.bukang.file.domain.FileFormat;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.domain.exception.FileAccessDeniedException;
import com.bukang.file.domain.exception.InvalidFileException;
import com.bukang.file.domain.exception.StoredFileNotFoundException;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.out.storage.FileStorage;
import com.bukang.file.out.storage.StoredObject;

import lombok.RequiredArgsConstructor;

/**
 * 업로드 URL 발급과 업로드 확인.
 * 파일 본문은 브라우저가 presigned URL로 S3에 직접 올리므로, 서버는 올라간 뒤에 형식과 크기를 다시 확인한다.
 *
 * presigned URL은 만료 전까지 여러 번 쓸 수 있어서, 확인을 마친 뒤 같은 URL로 다른 내용을 덮어쓸 수 있다.
 * 그래서 브라우저는 업로드 경로(uploads/)에만 올리고, 서버가 확인할 때 확정 경로(files/)로 복사한 뒤 복사본을 검사한다.
 * 확정 경로에는 업로드 URL을 주지 않으므로 확인한 내용이 바뀌지 않는다.
 */
@Service
@RequiredArgsConstructor
public class FileUploadUseCase {
	// 업로드 경로에 남은 객체는 S3 수명 주기 규칙으로 정리할 수 있다
	static final String UPLOAD_PREFIX = "uploads/";
	static final String CONFIRMED_PREFIX = "files/";
	private static final long MEGABYTE = 1024L * 1024;

	private final StoredFileRepository storedFileRepository;
	private final FileStorage fileStorage;

	public StoredFile create(FileType fileType, String fileName, String contentType, long fileSize, int memberId) {
		FileKind kind = fileType.getKind();

		FileFormat format = FileFormat.fromContentType(contentType)
			.filter(f -> f.getKind() == kind)
			.orElseThrow(() -> new InvalidFileException(
					"%s 파일은 %s만 올릴 수 있습니다.".formatted(kind.getLabel(), FileFormat.labelsOf(kind))));

		if (fileSize > kind.getMaxSize()) {
			throw new InvalidFileException(
				"%s 파일은 %dMB 이하만 올릴 수 있습니다.".formatted(kind.getLabel(), kind.getMaxSize() / MEGABYTE));
		}

		// 키에는 원본 파일명을 넣지 않는다 (한글·특수문자, 같은 이름 충돌)
		String uploadKey = UPLOAD_PREFIX + UUID.randomUUID() + "." + format.getExtension();
		return storedFileRepository.save(
			StoredFile.pending(uploadKey, baseName(fileName), fileType, format, fileSize, memberId));
	}

	// 형식·크기가 신고와 다르면 확정 경로의 객체를 지우고 파일을 삭제 상태로 바꾼 뒤 예외를 던진다
	// (호출하는 트랜잭션은 이 예외에도 삭제 상태를 커밋해야 한다)
	public StoredFile complete(int fileId, int memberId) {
		// 본인 파일인지 확인하고, 이미 확인을 마친 파일이면 그대로 돌려준다 (다시 불러도 같은 결과)
		StoredFile file = findOwnedFile(fileId, memberId);
		if (!file.isPending()) {
			return file;
		}

		// 브라우저가 업로드 경로(uploads/)에 실제로 올렸는지 확인한다
		String uploadKey = file.getS3Key();
		if (fileStorage.head(uploadKey).isEmpty()) {
			throw new InvalidFileException("업로드된 파일이 없습니다. 업로드 URL로 파일을 먼저 올려주세요.");
		}

		// 확정 경로로 옮긴다: uploads/abc.png → files/abc.png
		// S3에는 이동이 없어서 S3 안에서 복사한 뒤 원본을 지운다
		String confirmedKey = CONFIRMED_PREFIX + uploadKey.substring(UPLOAD_PREFIX.length());
		fileStorage.copy(uploadKey, confirmedKey);
		fileStorage.delete(uploadKey);

		// 복사본의 실제 크기·Content-Type과 앞부분(매직 바이트)을 가져온다
		StoredObject confirmed = fileStorage.head(confirmedKey)
			.orElseThrow(() -> new IllegalStateException("복사한 파일을 찾을 수 없습니다: " + confirmedKey));
		byte[] head = fileStorage.readFirstBytes(confirmedKey, FileFormat.SIGNATURE_LENGTH);

		// 신고한 값과 다르면 복사본을 지우고 삭제 상태로 바꾼다
		if (!matchesDeclared(file, confirmed, head)) {
			fileStorage.delete(confirmedKey);
			file.delete();
			throw new InvalidFileException("올린 파일이 신고한 형식이나 크기와 다릅니다. 업로드 URL을 다시 받아 올려주세요.");
		}

		// PENDING → UPLOADED, 키를 확정 경로로 바꾼다
		file.confirmUpload(confirmedKey);
		return file;
	}

	// 없거나 삭제된 파일은 404, 다른 회원이 올린 파일은 403
	private StoredFile findOwnedFile(int fileId, int memberId) {
		StoredFile file = storedFileRepository.findById(fileId)
			.filter(found -> !found.isDeleted())
			.orElseThrow(() -> new StoredFileNotFoundException("존재하지 않는 파일입니다."));
		if (!file.isOwnedBy(memberId)) {
			throw new FileAccessDeniedException("본인이 올린 파일만 처리할 수 있습니다.");
		}
		return file;
	}

	// 발급 때 신고한 값(DB)과 S3에 실제로 올라간 값을 비교한다
	// 크기가 같은지 → 종류별 최대 크기 이하인지 → Content-Type이 같은지
	// → 앞부분이 신고한 형식의 매직 바이트와 맞는지 (FileFormat.matches)
	private boolean matchesDeclared(StoredFile file, StoredObject uploaded, byte[] head) {
		return uploaded.size() == file.getFileSize()
			&& uploaded.size() <= file.getFileType().getKind().getMaxSize()
			&& file.getContentType().equalsIgnoreCase(uploaded.contentType())
			&& file.getFormat().matches(head);
	}

	// 일부 브라우저는 경로(C:\fakepath\a.png)를 함께 보내므로 파일명만 남긴다
	private String baseName(String fileName) {
		String trimmed = fileName.trim();
		int lastSeparator = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
		return trimmed.substring(lastSeparator + 1);
	}
}
