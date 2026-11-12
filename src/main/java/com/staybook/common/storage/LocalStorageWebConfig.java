package com.staybook.common.storage;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 디스크에 저장한 파일을 /uploads/** 로 내려준다.
 */
@Configuration
public class LocalStorageWebConfig implements WebMvcConfigurer {

    private final LocalDiskStorageClient storageClient;

    public LocalStorageWebConfig(LocalDiskStorageClient storageClient) {
        this.storageClient = storageClient;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storageClient.rootDir().toUri().toString();
        registry.addResourceHandler(StorageProperties.PUBLIC_PATH + "/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
