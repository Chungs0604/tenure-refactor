package com.tenure.global.storage.s3;

import com.tenure.global.storage.FileStorage;
import com.tenure.global.storage.StorageProperties;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

	private final S3Client client;
	private final String bucket;
	private final String baseUrl;

	public S3FileStorage(S3Client client, StorageProperties properties) {
		this.client = client;
		StorageProperties.S3 s3 = properties.s3();
		this.bucket = s3.bucket();
		this.baseUrl = resolveBaseUrl(s3);
	}

	@Override
	public String store(String key, InputStream content, long size, String contentType) {
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType)
				.build();
		client.putObject(request, RequestBody.fromInputStream(content, size));
		return key;
	}

	@Override
	public String toUrl(String key) {
		return baseUrl + "/" + key;
	}

	@Override
	public void delete(String key) {
		client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
	}

	/** base-url 이 비어 있으면 S3 기본 엔드포인트를 쓴다. CDN 을 붙이면 설정만 바꾼다. */
	private static String resolveBaseUrl(StorageProperties.S3 s3) {
		String configured = s3.baseUrl();
		if (configured != null && !configured.isBlank()) {
			return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
		}
		return "https://%s.s3.%s.amazonaws.com".formatted(s3.bucket(), s3.region());
	}
}
