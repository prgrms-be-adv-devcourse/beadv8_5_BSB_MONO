package com.bukang.file.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.file.domain.FileKind;
import com.bukang.file.support.TestImages;

class FileFormatTest {

	@Test
	@DisplayName("Content-Type으로 형식을 찾는다 (대소문자·앞뒤 공백 무시)")
	void fromContentType() {
		assertThat(FileFormat.fromContentType("image/png")).contains(FileFormat.PNG);
		assertThat(FileFormat.fromContentType(" IMAGE/JPEG ")).contains(FileFormat.JPEG);
		assertThat(FileFormat.fromContentType("image/webp")).contains(FileFormat.WEBP);
		assertThat(FileFormat.fromContentType("video/mp4")).contains(FileFormat.MP4);
		assertThat(FileFormat.fromContentType("video/quicktime")).contains(FileFormat.MOV);
		assertThat(FileFormat.fromContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.contains(FileFormat.XLSX);
		assertThat(FileFormat.fromContentType("application/vnd.ms-excel")).contains(FileFormat.XLS);

		assertThat(FileFormat.fromContentType("image/gif")).isEmpty();
		assertThat(FileFormat.fromContentType("video/webm")).isEmpty();
		assertThat(FileFormat.fromContentType("text/csv")).isEmpty();
		assertThat(FileFormat.fromContentType(null)).isEmpty();
	}

	@Test
	@DisplayName("이미지는 파일 앞부분(매직 바이트)이 형식과 맞는지 확인한다")
	void matchesImage() {
		assertThat(FileFormat.PNG.matches(TestImages.PNG)).isTrue();
		assertThat(FileFormat.JPEG.matches(TestImages.JPEG)).isTrue();
		assertThat(FileFormat.WEBP.matches(TestImages.WEBP)).isTrue();

		assertThat(FileFormat.PNG.matches(TestImages.JPEG)).isFalse();
		assertThat(FileFormat.PNG.matches(TestImages.TEXT)).isFalse();
		assertThat(FileFormat.WEBP.matches(TestImages.PNG)).isFalse();
	}

	@Test
	@DisplayName("동영상은 ftyp 뒤 브랜드로 MP4와 MOV를 구분하고, 같은 구조의 오디오는 막는다")
	void matchesVideo() {
		assertThat(FileFormat.MP4.matches(TestImages.MP4)).isTrue();
		assertThat(FileFormat.MOV.matches(TestImages.MOV)).isTrue();

		assertThat(FileFormat.MP4.matches(TestImages.MOV)).isFalse();
		assertThat(FileFormat.MOV.matches(TestImages.MP4)).isFalse();
		assertThat(FileFormat.MP4.matches(TestImages.M4A)).isFalse();
		assertThat(FileFormat.MP4.matches(TestImages.TEXT)).isFalse();
	}

	@Test
	@DisplayName("엑셀은 XLSX(ZIP)와 XLS(옛 오피스 형식)의 앞부분을 확인한다")
	void matchesExcel() {
		assertThat(FileFormat.XLSX.matches(TestImages.XLSX)).isTrue();
		assertThat(FileFormat.XLS.matches(TestImages.XLS)).isTrue();

		assertThat(FileFormat.XLSX.matches(TestImages.XLS)).isFalse();
		assertThat(FileFormat.XLS.matches(TestImages.XLSX)).isFalse();
		assertThat(FileFormat.XLSX.matches(TestImages.TEXT)).isFalse();
	}

	@Test
	@DisplayName("앞부분이 시그니처보다 짧으면 맞지 않는 것으로 본다")
	void shortHead() {
		assertThat(FileFormat.PNG.matches(new byte[] {(byte)0x89, 0x50})).isFalse();
		assertThat(FileFormat.WEBP.matches(new byte[] {'R', 'I', 'F', 'F'})).isFalse();
		assertThat(FileFormat.JPEG.matches(new byte[0])).isFalse();
		// ftyp까지만 있고 브랜드가 없다
		assertThat(FileFormat.MP4.matches(Arrays.copyOf(TestImages.MP4, 8))).isFalse();
		assertThat(FileFormat.MOV.matches(Arrays.copyOf(TestImages.MOV, 10))).isFalse();
	}

	@Test
	@DisplayName("종류별 형식 이름을 메시지에 쓸 수 있게 잇는다")
	void labelsOf() {
		assertThat(FileFormat.labelsOf(FileKind.IMAGE)).isEqualTo("JPG, PNG, WebP");
		assertThat(FileFormat.labelsOf(FileKind.VIDEO)).isEqualTo("MP4, MOV");
		assertThat(FileFormat.labelsOf(FileKind.EXCEL)).isEqualTo("XLSX, XLS");
	}

	@Test
	@DisplayName("JPG·PNG·WebP(손실·무손실·확장) 파일 머리에서 가로·세로를 읽는다")
	void readSize() {
		// given
		byte[] jpeg = TestImages.jpeg(1200, 800);
		byte[] png = TestImages.png(1080, 3000);
		byte[] webpLossy = TestImages.webpLossy(1125, 1125);
		byte[] webpLossless = TestImages.webpLossless(4000, 1);
		byte[] webpExtended = TestImages.webpExtended(1500, 900);

		// when · then
		assertSoftly(softly -> {
			softly.assertThat(FileFormat.JPEG.readSize(jpeg)).contains(new ImageSize(1200, 800));
			softly.assertThat(FileFormat.PNG.readSize(png)).contains(new ImageSize(1080, 3000));
			softly.assertThat(FileFormat.WEBP.readSize(webpLossy)).contains(new ImageSize(1125, 1125));
			softly.assertThat(FileFormat.WEBP.readSize(webpLossless)).contains(new ImageSize(4000, 1));
			softly.assertThat(FileFormat.WEBP.readSize(webpExtended)).contains(new ImageSize(1500, 900));
		});
	}

	@Test
	@DisplayName("휴대폰 사진처럼 사진 정보(EXIF) 칸이 크기 칸보다 앞에 있는 JPEG도 가로·세로를 읽는다")
	void readSizeOfJpegWithPhotoInfo() {
		// given
		byte[] photo = TestImages.jpegWithPhotoInfo(800, 1600, 8 * 1024);

		// when
		Optional<ImageSize> size = FileFormat.JPEG.readSize(photo);

		// then
		assertThat(size).contains(new ImageSize(800, 1600));
	}

	// supports 검사를 뺐으므로, 형식이 없는 종류를 추가하면 여기서 알 수 있다
	@Test
	@DisplayName("모든 파일 종류에 받을 수 있는 형식이 하나 이상 있다")
	void everyKindHasFormat() {
		for (FileKind kind : FileKind.values()) {
			assertThat(FileFormat.labelsOf(kind)).as(kind.name()).isNotEmpty();
		}
	}
}
