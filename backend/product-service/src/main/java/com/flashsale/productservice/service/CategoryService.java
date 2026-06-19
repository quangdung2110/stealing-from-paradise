package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.category.CategoryBreadcrumb;
import com.flashsale.productservice.dto.category.CategoryRequest;
import com.flashsale.productservice.dto.category.CategoryResponse;
import com.flashsale.productservice.entity.Category;
import com.flashsale.productservice.repository.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional(readOnly = true)
    public ApiResponse<List<CategoryResponse>> getCategoryTree() {
        List<Category> rootCategories = categoryRepository.findByParentIdIsNullAndDeletedAtIsNull();
        List<CategoryResponse> tree = rootCategories.stream()
                .filter(Category::getIsActive)
                .map(this::toTreeNode)
                .collect(Collectors.toList());
        return ApiResponse.success(tree);
    }

    @Transactional(readOnly = true)
    public ApiResponse<CategoryResponse> getCategoryDetail(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Category not found"));

        CategoryResponse response = toDetailResponse(category);
        return ApiResponse.success(response);
    }

    @Transactional
    public ApiResponse<CategoryResponse> createCategory(CategoryRequest request, UserDetailsImpl user) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(request.getName());
        }
        if (categoryRepository.existsBySlug(slug)) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Category with slug already exists");
        }

        Category category = Category.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .parentId(request.getParentId())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        category = categoryRepository.save(category);
        emitEvent(com.flashsale.commonlib.event.KafkaTopics.CATEGORY_UPDATED, category.getId().toString(),
                Map.of("categoryId", category.getId(), "action", "created"));
        return ApiResponse.success(toDetailResponse(category));
    }

    @Transactional
    public ApiResponse<CategoryResponse> updateCategory(UUID categoryId, CategoryRequest request, UserDetailsImpl user) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Category not found"));

        if (request.getName() != null) {
            category.setName(request.getName());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getImageUrl() != null) {
            category.setImageUrl(request.getImageUrl());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getParentId() != null) {
            if (request.getParentId().equals(categoryId)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Category cannot be its own parent");
            }
            category.setParentId(request.getParentId());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }
        if (request.getSlug() != null && !request.getSlug().equals(category.getSlug())) {
            if (categoryRepository.existsBySlugAndIdNot(request.getSlug(), categoryId)) {
                throw new AppException(ErrorCode.ALREADY_EXISTS, "Slug already exists");
            }
            category.setSlug(request.getSlug());
        } else if (request.getName() != null) {
            category.setSlug(generateSlug(request.getName()));
        }

        category = categoryRepository.save(category);

        if (!category.getIsActive()) {
            deactivateChildren(categoryId);
        }

        emitEvent(com.flashsale.commonlib.event.KafkaTopics.CATEGORY_UPDATED, category.getId().toString(),
                Map.of("categoryId", category.getId(), "action", "updated"));
        return ApiResponse.success(toDetailResponse(category));
    }

    @Transactional
    public ApiResponse<Void> deleteCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Category not found"));

        category.setDeletedAt(LocalDateTime.now());
        categoryRepository.save(category);

        emitEvent(com.flashsale.commonlib.event.KafkaTopics.CATEGORY_UPDATED, category.getId().toString(),
                Map.of("categoryId", category.getId(), "action", "deleted"));
        deactivateChildren(categoryId);

        return ApiResponse.success(null);
    }

    @Transactional(readOnly = true)
    public void validateLeafCategory(UUID categoryId) {
        List<Category> children = categoryRepository.findByParentIdAndDeletedAtIsNull(categoryId);
        if (!children.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot create product in a parent category. Choose a leaf category.");
        }
    }

    private CategoryResponse toTreeNode(Category category) {
        List<Category> children = categoryRepository.findByParentIdAndDeletedAtIsNull(category.getId());
        List<CategoryResponse> childResponses = children.stream()
                .filter(Category::getIsActive)
                .map(this::toTreeNode)
                .collect(Collectors.toList());

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .parentId(category.getParentId())
                .children(childResponses)
                .productCount(categoryRepository.countProductsByCategoryId(category.getId()))
                .build();
    }

    private CategoryResponse toDetailResponse(Category category) {
        List<CategoryBreadcrumb> breadcrumb = buildBreadcrumb(category);

        List<Category> children = categoryRepository.findByParentIdAndDeletedAtIsNull(category.getId());
        List<CategoryResponse> childResponses = children.stream()
                .map(child -> CategoryResponse.builder()
                        .id(child.getId())
                        .name(child.getName())
                        .slug(child.getSlug())
                        .description(child.getDescription())
                        .imageUrl(child.getImageUrl())
                        .sortOrder(child.getSortOrder())
                        .isActive(child.getIsActive())
                        .parentId(child.getParentId())
                        .productCount(categoryRepository.countProductsByCategoryId(child.getId()))
                        .build())
                .collect(Collectors.toList());

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .parentId(category.getParentId())
                .children(childResponses)
                .productCount(categoryRepository.countProductsByCategoryId(category.getId()))
                .breadcrumb(breadcrumb)
                .build();
    }

    private List<CategoryBreadcrumb> buildBreadcrumb(Category category) {
        List<CategoryBreadcrumb> breadcrumb = new ArrayList<>();
        Category current = category;
        while (current != null) {
            breadcrumb.add(0, CategoryBreadcrumb.builder()
                    .id(current.getId())
                    .name(current.getName())
                    .slug(current.getSlug())
                    .build());
            if (current.getParentId() != null) {
                current = categoryRepository.findById(current.getParentId())
                        .filter(c -> c.getDeletedAt() == null)
                        .orElse(null);
            } else {
                current = null;
            }
        }
        return breadcrumb;
    }

    private void deactivateChildren(UUID parentId) {
        List<Category> children = categoryRepository.findByParentIdAndDeletedAtIsNull(parentId);
        for (Category child : children) {
            child.setIsActive(false);
            categoryRepository.save(child);
            deactivateChildren(child.getId());
        }
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private void emitEvent(String topic, String key, Map<String, Object> payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, value);
        } catch (Exception e) {
            log.error("Failed to emit Kafka event: topic={}, key={}", topic, key, e);
        }
    }
}
