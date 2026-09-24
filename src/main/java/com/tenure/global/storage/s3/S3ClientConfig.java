package com.tenure.global.storage.s3;

import com.tenure.global.storage.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ClientConfig {

	@Bean
	public S3Client s3Client(StorageProperties properties) {
		return S3Client.builder()
				.region(Region.of(properties.s3().region()))
				.credentialsProvider(DefaultCredentialsProvider.builder().build())
				.build();
	}
}
