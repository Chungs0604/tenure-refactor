package com.tenure.global.storage.upload;

import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.exception.MediaErrorCode;
import com.tenure.global.storage.FileStorage;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageUploadService")
class ImageUploadServiceTest {

	@Mock private FileStorage fileStorage;

	@InjectMocks private ImageUploadService imageUploadService;

	@Test
	@DisplayName("용도와 연·월로 키를 만들고 URL 을 함께 돌려준다")
	void upload_jpeg_returnsKeyUnderDomainPath() {
		given(fileStorage.store(anyString(), any(InputStream.class), anyLong(), anyString()))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(fileStorage.toUrl(anyString())).willAnswer(invocation -> "/uploads/" + invocation.getArgument(0));

		ImageUploadResponse response = imageUploadService.upload(ImageDomain.POST, jpeg());

		String expectedPrefix = "post/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/";
		assertThat(response.getKey()).startsWith(expectedPrefix).endsWith(".jpg");
		assertThat(response.getUrl()).isEqualTo("/uploads/" + response.getKey());
	}

	@Test
	@DisplayName("사용자가 준 파일명을 키에 쓰지 않는다 — 경로 문자가 섞이고 남의 파일을 덮어쓴다")
	void upload_maliciousFilename_doesNotUseIt() {
		given(fileStorage.store(anyString(), any(InputStream.class), anyLong(), anyString()))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(fileStorage.toUrl(anyString())).willReturn("/uploads/ignored");

		MockMultipartFile file = new MockMultipartFile(
				"file", "../../etc/passwd.jpg", "image/jpeg", "bytes".getBytes());

		ImageUploadResponse response = imageUploadService.upload(ImageDomain.PROFILE, file);

		assertThat(response.getKey()).doesNotContain("..").doesNotContain("passwd");
	}

	@Test
	@DisplayName("허용하지 않는 형식이면 MEDIA_400_TYPE 이고 저장하지 않는다")
	void upload_unsupportedType_throwsTypeError() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "resume.pdf", "application/pdf", "bytes".getBytes());

		assertThatThrownBy(() -> imageUploadService.upload(ImageDomain.POST, file))
				.isInstanceOf(CustomException.class)
				.extracting(ImageUploadServiceTest::errorCodeOf)
				.isEqualTo(MediaErrorCode.MEDIA_400_TYPE);

		verify(fileStorage, times(0)).store(anyString(), any(), anyLong(), anyString());
	}

	@Test
	@DisplayName("빈 파일도 막는다 — 저장은 되지만 화면에서 깨진 이미지가 된다")
	void upload_emptyFile_throwsTypeError() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "empty.jpg", "image/jpeg", new byte[0]);

		assertThatThrownBy(() -> imageUploadService.upload(ImageDomain.POST, file))
				.isInstanceOf(CustomException.class)
				.extracting(ImageUploadServiceTest::errorCodeOf)
				.isEqualTo(MediaErrorCode.MEDIA_400_TYPE);
	}

	@Test
	@DisplayName("저장소가 실패하면 MEDIA_500_STORAGE 로 바꿔 던진다")
	void upload_storageFails_throwsStorageError() {
		willThrow(new RuntimeException("disk full"))
				.given(fileStorage).store(anyString(), any(InputStream.class), anyLong(), eq("image/png"));

		MockMultipartFile file = new MockMultipartFile(
				"file", "ootd.png", "image/png", "bytes".getBytes());

		assertThatThrownBy(() -> imageUploadService.upload(ImageDomain.ITEM, file))
				.isInstanceOf(CustomException.class)
				.extracting(ImageUploadServiceTest::errorCodeOf)
				.isEqualTo(MediaErrorCode.MEDIA_500_STORAGE);
	}

	private static MockMultipartFile jpeg() {
		return new MockMultipartFile("file", "ootd.jpg", "image/jpeg", "bytes".getBytes());
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
