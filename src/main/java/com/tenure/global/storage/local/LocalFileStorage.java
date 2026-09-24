package com.tenure.global.storage.local;

import com.tenure.global.storage.FileStorage;
import com.tenure.global.storage.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

	private final Path baseDir;
	private final String baseUrl;

	public LocalFileStorage(StorageProperties properties) {
		this.baseDir = Path.of(properties.local().baseDir()).toAbsolutePath().normalize();
		this.baseUrl = stripTrailingSlash(properties.local().baseUrl());
	}

	@Override
	public String store(String key, InputStream content, long size, String contentType) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("파일 저장 실패: " + key, e);
		}
		return key;
	}

	@Override
	public String toUrl(String key) {
		return baseUrl + "/" + key;
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		} catch (IOException e) {
			throw new UncheckedIOException("파일 삭제 실패: " + key, e);
		}
	}

	/** 경로 탈출(../)을 막는다. */
	private Path resolve(String key) {
		Path target = baseDir.resolve(key).normalize();
		if (!target.startsWith(baseDir)) {
			throw new IllegalArgumentException("허용되지 않는 키: " + key);
		}
		return target;
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
