package com.bukang.file.out.storage;

import java.time.Instant;
import java.util.Map;

/**
 * presigned PUT URL과, 업로드할 때 함께 보내야 하는 헤더
 */
public record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
}
