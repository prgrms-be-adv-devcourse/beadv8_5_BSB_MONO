package com.bukang.file.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.file.support.TestImages;

class ImageFormatTest {

	@Test
	@DisplayName("Content-Type으로 형식을 찾는다 (대소문자·앞뒤 공백 무시)")
	void fromContentType() {
		assertThat(ImageFormat.fromContentType("image/png")).contains(ImageFormat.PNG);
		assertThat(ImageFormat.fromContentType(" IMAGE/JPEG ")).contains(ImageFormat.JPEG);
		assertThat(ImageFormat.fromContentType("image/webp")).contains(ImageFormat.WEBP);
		assertThat(ImageFormat.fromContentType("image/gif")).isEmpty();
		assertThat(ImageFormat.fromContentType(null)).isEmpty();
	}

	@Test
	@DisplayName("파일 앞부분(매직 바이트)이 형식과 맞는지 확인한다")
	void matches() {
		assertThat(ImageFormat.PNG.matches(TestImages.PNG)).isTrue();
		assertThat(ImageFormat.JPEG.matches(TestImages.JPEG)).isTrue();
		assertThat(ImageFormat.WEBP.matches(TestImages.WEBP)).isTrue();

		assertThat(ImageFormat.PNG.matches(TestImages.JPEG)).isFalse();
		assertThat(ImageFormat.PNG.matches(TestImages.TEXT)).isFalse();
		assertThat(ImageFormat.WEBP.matches(TestImages.PNG)).isFalse();
	}

	@Test
	@DisplayName("앞부분이 시그니처보다 짧으면 맞지 않는 것으로 본다")
	void shortHead() {
		assertThat(ImageFormat.PNG.matches(new byte[] {(byte)0x89, 0x50})).isFalse();
		assertThat(ImageFormat.WEBP.matches(new byte[] {'R', 'I', 'F', 'F'})).isFalse();
		assertThat(ImageFormat.JPEG.matches(new byte[0])).isFalse();
	}
}
