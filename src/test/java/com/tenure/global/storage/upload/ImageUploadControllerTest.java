package com.tenure.global.storage.upload;

import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.MediaErrorCode;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageUploadController.class)
@DisplayName("ImageUploadController")
class ImageUploadControllerTest extends ControllerSliceTestSupport {

	private static final String IMAGES_URL = "/api/v1/images";
	private static final Long USER_ID = 1L;

	@MockitoBean private ImageUploadService imageUploadService;

	@Test
	@DisplayName("키와 미리보기 URL 을 돌려준다")
	void upload_validImage_returnsKeyAndUrl() throws Exception {
		given(imageUploadService.upload(eq(ImageDomain.POST), any()))
				.willReturn(ImageUploadResponse.builder()
						.key("post/2026/09/ab12.jpg")
						.url("/uploads/post/2026/09/ab12.jpg")
						.build());

		mvc.perform(multipart(IMAGES_URL).file(jpeg())
						.param("domain", "POST")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.key").value("post/2026/09/ab12.jpg"))
				.andExpect(jsonPath("$.data.url").value("/uploads/post/2026/09/ab12.jpg"));
	}

	@Test
	@DisplayName("용도를 주지 않으면 400 이다 — 키의 첫 구간이 정해지지 않는다")
	void upload_noDomain_returnsBadRequest() throws Exception {
		mvc.perform(multipart(IMAGES_URL).file(jpeg())
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"));

		verifyNoInteractions(imageUploadService);
	}

	@Test
	@DisplayName("허용하지 않는 형식이면 400 과 MEDIA_400_TYPE 이다")
	void upload_unsupportedType_returnsMediaTypeError() throws Exception {
		willThrow(new CustomException(MediaErrorCode.MEDIA_400_TYPE))
				.given(imageUploadService).upload(eq(ImageDomain.ITEM), any());

		mvc.perform(multipart(IMAGES_URL)
						.file(new MockMultipartFile("file", "resume.pdf", "application/pdf", "x".getBytes()))
						.param("domain", "ITEM")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MEDIA_400_TYPE"));
	}

	@Test
	@DisplayName("토큰이 없으면 401 이다 — 익명 업로드를 허용하면 저장소가 쓰레기통이 된다")
	void upload_noAuth_returnsUnauthorized() throws Exception {
		mvc.perform(multipart(IMAGES_URL).file(jpeg()).param("domain", "POST"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(imageUploadService);
	}

	private static MockMultipartFile jpeg() {
		return new MockMultipartFile("file", "ootd.jpg", "image/jpeg", "bytes".getBytes());
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}
}
