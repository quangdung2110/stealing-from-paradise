package com.flashsale.refundservice.config;

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

    /**
     * MinIO client for generating presigned upload URLs for refund evidence images.
     *
     * Uses the PUBLIC URL as endpoint so the AWS V4 signature matches the hostname
     * the browser will connect to (localhost:9000). The MinIO Java SDK tries to
     * reach the endpoint to detect the region — because the container cannot reach
     * localhost:9000, we set the region explicitly so all crypto stays local.
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(publicUrl)
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
