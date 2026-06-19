package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.productservice.dto.banner.BannerRequest;
import com.flashsale.productservice.dto.banner.BannerResponse;
import com.flashsale.productservice.entity.Banner;
import com.flashsale.productservice.repository.BannerRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/banners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminBannerController {

    private final BannerRepository bannerRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> listBanners() {
        List<Banner> banners = bannerRepository.findAllByOrderBySortOrderAsc();
        List<BannerResponse> response = banners.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BannerResponse>> createBanner(
            @Valid @RequestBody BannerRequest request) {
        Banner banner = Banner.builder()
                .title(request.getTitle())
                .imageUrl(request.getImageUrl())
                .position(request.getPosition())
                .active(request.getActive() != null ? request.getActive() : true)
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .build();
        banner = bannerRepository.save(banner);
        log.info("Admin created banner: {} ({})", banner.getTitle(), banner.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(banner), "Banner created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerResponse>> updateBanner(
            @PathVariable UUID id,
            @Valid @RequestBody BannerRequest request) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner not found: " + id));
        banner.setTitle(request.getTitle());
        banner.setImageUrl(request.getImageUrl());
        banner.setPosition(request.getPosition());
        banner.setActive(request.getActive() != null ? request.getActive() : true);
        banner.setStartsAt(request.getStartsAt());
        banner.setEndsAt(request.getEndsAt());
        banner = bannerRepository.save(banner);
        log.info("Admin updated banner: {} ({})", banner.getTitle(), banner.getId());
        return ResponseEntity.ok(ApiResponse.success(toResponse(banner), "Banner updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable UUID id) {
        if (!bannerRepository.existsById(id)) {
            throw new RuntimeException("Banner not found: " + id);
        }
        bannerRepository.deleteById(id);
        log.info("Admin deleted banner: {}", id);
        return ResponseEntity.ok(ApiResponse.success(null, "Banner deleted"));
    }

    private BannerResponse toResponse(Banner banner) {
        return BannerResponse.builder()
                .id(banner.getId())
                .title(banner.getTitle())
                .imageUrl(banner.getImageUrl())
                .position(banner.getPosition())
                .active(banner.getActive())
                .startsAt(banner.getStartsAt())
                .endsAt(banner.getEndsAt())
                .sortOrder(banner.getSortOrder())
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .build();
    }
}
