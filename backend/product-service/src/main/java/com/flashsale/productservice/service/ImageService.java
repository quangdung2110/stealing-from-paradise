package com.flashsale.productservice.service;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.image.ImageResponse;
import com.flashsale.productservice.dto.image.ImageUploadResponse;
import com.flashsale.productservice.dto.image.RegisterImageRequest;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductImage;
import com.flashsale.productservice.repository.ProductImageRepository;
import com.flashsale.productservice.repository.ProductRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private static final int PRESIGNED_URL_TTL_MINUTES = 15;

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final MinioClient minioClient;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.public-url:http://localhost:9000}")
    private String minioPublicUrl;

    @Value("${minio.bucket}")
    private String bucket;

    @Transactional(readOnly = true)
    public ApiResponse<List<ImageResponse>> getImagesByProduct(UUID productId) {
        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
        List<ImageResponse> responses = images.stream()
                .map(this::toImageResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @Transactional
    public ApiResponse<ImageUploadResponse> generatePresignedUrl(UUID productId, String filename, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to upload images for this product");
        }

        String extension = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = filename.substring(dotIndex);
        }
        UUID imageId = UUID.randomUUID();
        String objectName = String.format("products/%d/%s/%s%s",
                user.getId(), productId, imageId, extension);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new java.io.ByteArrayInputStream(new byte[0]), 0, -1)
                            .build()
            );

            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(PRESIGNED_URL_TTL_MINUTES * 60)
                            .build()
            );
            // Replace internal Docker hostname with browser-accessible URL
            String publicUploadUrl = uploadUrl.replace(minioUrl, minioPublicUrl);

            String objectUrl = String.format("%s/%s/%s", minioPublicUrl, bucket, objectName);

            ImageUploadResponse response = ImageUploadResponse.builder()
                    .uploadUrl(publicUploadUrl)
                    .presignedUrl(publicUploadUrl)
                    .objectUrl(objectUrl)
                    .imageId(imageId)
                    .expiresAt(LocalDateTime.now().plusMinutes(PRESIGNED_URL_TTL_MINUTES))
                    .expiresIn(PRESIGNED_URL_TTL_MINUTES * 60)
                    .build();

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("Failed to generate presigned URL", e);
            throw new AppException(ErrorCode.BAD_REQUEST, "Failed to generate upload URL: " + e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<ImageResponse> registerImage(UUID productId, RegisterImageRequest request, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to register images for this product");
        }

        try {
            boolean objectExists = minioClient.statObject(
                    io.minio.StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(getObjectNameFromUrl(request.getUrl()))
                            .build()
            ) != null;
            
            if (!objectExists) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Image does not exist in storage");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not verify image existence in MinIO: {}", e.getMessage());
        }

        if (imageRepository.existsByUrl(request.getUrl())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Image with this URL already registered");
        }

        ProductImage image = ProductImage.builder()
                .productId(productId)
                .url(request.getUrl())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        image = imageRepository.save(image);
        return ApiResponse.success(toImageResponse(image));
    }

    @Transactional
    public ApiResponse<Void> deleteImage(UUID imageId, UserDetailsImpl user) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Image not found"));

        Product product = productRepository.findById(image.getProductId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to delete this image");
        }

        imageRepository.delete(image);
        return ApiResponse.success(null);
    }

    private String getObjectNameFromUrl(String url) {
        if (url == null) {
            return null;
        }
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return url;
    }

    private ImageResponse toImageResponse(ProductImage image) {
        return ImageResponse.builder()
                .id(image.getId())
                .productId(image.getProductId())
                .variantId(image.getVariantId())
                .url(image.getUrl())
                .sortOrder(image.getSortOrder())
                .build();
    }
}
