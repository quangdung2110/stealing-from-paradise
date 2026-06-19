package com.flashsale.orderservice.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.url}")
    private String url;

    @Value("${minio.public-url:${minio.url}}")
    private String publicUrl;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        // Use internal URL (http://minio:9000) so putObject() can actually
        // reach MinIO from within the Docker network.  publicUrl
        // (http://localhost:9000) is only for building browser-facing URLs
        // and presigned-URL signatures — it is NOT reachable from containers.
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .region("us-east-1")
                .build();
    }

    public String getInternalUrl() {
        return url;
    }

    public String getPublicUrl() {
        return publicUrl;
    }
}
