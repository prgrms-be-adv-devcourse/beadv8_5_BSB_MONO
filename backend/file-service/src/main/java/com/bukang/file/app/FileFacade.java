package com.bukang.file.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.file.domain.StoredFile;
import com.bukang.file.domain.exception.InvalidFileException;
import com.bukang.file.dto.FileDto;
import com.bukang.file.dto.FileUploadDto;
import com.bukang.file.dto.FileUploadRequestDto;
import com.bukang.file.out.storage.FileStorage;
import com.bukang.file.out.storage.PresignedUpload;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FileFacade {
	private static final String UPLOAD_METHOD = "PUT";

	private final FileUploadUseCase fileUploadUseCase;
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
		StoredFile file = fileUploadUseCase.complete(fileId, memberId);
		return file.toDto(fileStorage.presignGet(file.getS3Key()));
	}
}
