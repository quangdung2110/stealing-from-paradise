package com.flashsale.productservice.service;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.product.ProductResponse;
import com.flashsale.productservice.dto.product.SellerProductCard;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public ApiResponse<ProductResponse> getProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        return ApiResponse.success(productMapper.toProductResponse(product));
    }

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<SellerProductCard>> getSellerProducts(UserDetailsImpl user, Pageable pageable) {
        // ADMIN sees all products across all sellers (full catalog management).
        // SELLER sees only their own products.
        boolean isAdmin = "ADMIN".equals(user.getRole());
        Page<Product> products = isAdmin
            ? productRepository.findAllByDeletedAtIsNull(pageable)
            : productRepository.findBySellerIdAndDeletedAtIsNull(user.getId(), pageable);

        List<SellerProductCard> cards = products.getContent().stream()
                .map(productMapper::toSellerProductCard)
                .collect(Collectors.toList());

        PageResponse<SellerProductCard> pageResponse = PageResponse.<SellerProductCard>builder()
                .content(cards)
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .last(products.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<ProductResponse>> listPublicProducts(
            UUID categoryId, Long sellerId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Page<Product> page = productRepository.searchPublic(categoryId, sellerId, minPrice, maxPrice, pageable);

        List<ProductResponse> content = page.getContent().stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());

        PageResponse<ProductResponse> body = PageResponse.<ProductResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();

        return ApiResponse.success(body);
    }
}
