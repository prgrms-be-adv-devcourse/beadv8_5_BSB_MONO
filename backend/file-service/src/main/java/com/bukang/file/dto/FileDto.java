package com.bukang.file.dto;

import java.time.LocalDateTime;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.file.domain.FileStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class FileDto {
	private final int id;
	private final LocalDateTime createDate;
	private final LocalDateTime modifyDate;
	// 올린 회원 ID
	private final int createUser;
	private final String originFileName;
	private final String contentType;
	private final long fileSize;
	private final FileStatus status;
	// 연결 전에는 null
	private final String refType;
	private final Integer refId;
	private final FileType fileType;
	private final int sortNo;
	// 이미지를 볼 수 있는 presigned GET URL (짧게 만료된다). 업로드가 확인되지 않은 파일은 null
	private final String url;
}
