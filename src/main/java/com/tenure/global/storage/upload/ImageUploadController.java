package com.tenure.global.storage.upload;

import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageUploadController implements ImageUploadApi {

	private final ImageUploadService mediaService;

	// 이미지 업로드 → 저장소 키 반환
	@Override
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<BaseResponse<ImageUploadResponse>> upload(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam ImageDomain domain,
			@RequestParam MultipartFile file
	) {
		ImageUploadResponse response = mediaService.upload(domain, file);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
