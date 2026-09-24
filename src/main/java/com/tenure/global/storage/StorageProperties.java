package com.tenure.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String type, Local local, S3 s3) {

	public record Local(String baseDir, String baseUrl) {}

	public record S3(String bucket, String region, String baseUrl) {}
}
