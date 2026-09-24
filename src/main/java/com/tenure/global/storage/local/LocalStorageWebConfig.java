package com.tenure.global.storage.local;

import com.tenure.global.storage.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 로컬 저장소일 때만 업로드 디렉터리를 정적 리소스로 노출한다. */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageWebConfig implements WebMvcConfigurer {

	private final StorageProperties properties;

	public LocalStorageWebConfig(StorageProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		StorageProperties.Local local = properties.local();
		registry.addResourceHandler(local.baseUrl() + "/**")
				.addResourceLocations("file:" + local.baseDir() + "/");
	}
}
