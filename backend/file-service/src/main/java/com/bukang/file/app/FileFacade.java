package com.bukang.file.app;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.domain.exception.FileAccessDeniedException;
import com.bukang.file.domain.exception.InvalidFileException;
import com.bukang.file.domain.exception.StoredFileNotFoundException;
import com.bukang.file.dto.FileDto;
import com.bukang.file.dto.FileLinkRequestDto;
import com.bukang.file.dto.FileUploadDto;
import com.bukang.file.dto.FileUploadRequestDto;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.out.storage.FileStorage;
import com.bukang.file.out.storage.PresignedUpload;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FileFacade {
	private static final String UPLOAD_METHOD = "PUT";

	private final FileUploadUseCase fileUploadUseCase;
	private final FileLinkUseCase fileLinkUseCase;
	private final StoredFileRepository storedFileRepository;
	private final FileStorage fileStorage;

	@Transactional
	public FileUploadDto issueUploadUrl(FileUploadRequestDto request, int memberId) {
		StoredFile file = fileUploadUseCase.issue(
			request.getFileName(),
			request.getContentType(),
			request.getFileSize(),
			memberId
		);
		PresignedUpload upload = fileStorage.presignPut(file.getS3Key(), file.getContentType(), file.getFileSize());
		return new FileUploadDto(file.getId(), upload.url(), UPLOAD_METHOD, upload.headers(), upload.expiresAt());
	}

	// 확인에 실패해도 삭제 상태는 남겨야 하므로 InvalidFileException에는 롤백하지 않는다
	@Transactional(noRollbackFor = InvalidFileException.class)
	public FileDto completeUpload(int fileId, int memberId) {
		return toDto(fileUploadUseCase.complete(fileId, memberId));
	}

	@Transactional
	public List<FileDto> link(String refType, int refId, FileLinkRequestDto request) {
		return fileLinkUseCase.link(refType, refId, request.getOwnerId(), request.getFiles()).stream()
			.map(this::toDto)
			.toList();
	}

	// 대상에 연결된 파일만 돌려준다
	public List<FileDto> findByRef(String refType, int refId) {
		return storedFileRepository
			.findAllByRefTypeAndRefIdAndStatusOrderByImageTypeAscSortNoAscIdAsc(refType, refId, FileStatus.ACTIVE)
			.stream()
			.map(this::toDto)
			.toList();
	}

	// 대상에 연결된 파일은 누구나, 연결 전 파일은 올린 회원만 볼 수 있다
	public FileDto findById(int fileId, Integer memberId) {
		StoredFile file = storedFileRepository.findById(fileId)
			.filter(found -> !found.isDeleted())
			.orElseThrow(() -> new StoredFileNotFoundException("존재하지 않는 파일입니다."));
		if (!file.isActive() && (memberId == null || !file.isOwnedBy(memberId))) {
			throw new FileAccessDeniedException("본인이 올린 파일만 처리할 수 있습니다.");
		}
		return toDto(file);
	}

	// 업로드 확인 전 파일은 확정 경로에 객체가 없으므로 URL을 만들지 않는다
	private FileDto toDto(StoredFile file) {
		String url = file.hasConfirmedObject() ? fileStorage.presignGet(file.getS3Key()) : null;
		return file.toDto(url);
	}
}
