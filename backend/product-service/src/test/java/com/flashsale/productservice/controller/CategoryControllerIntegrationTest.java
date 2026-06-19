package com.flashsale.productservice.controller;

import com.flashsale.productservice.config.IntegrationTestConfig;
import com.flashsale.productservice.entity.Category;
import com.flashsale.productservice.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
@Transactional
class CategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category electronics;
    private Category clothing;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();

        electronics = categoryRepository.save(Category.builder()
                .name("Electronics")
                .slug("electronics")
                .description("Electronic devices")
                .sortOrder(1)
                .isActive(true)
                .build());

        clothing = categoryRepository.save(Category.builder()
                .name("Clothing")
                .slug("clothing")
                .description("Apparel and fashion")
                .sortOrder(2)
                .isActive(true)
                .build());

        categoryRepository.save(Category.builder()
                .name("Inactive Category")
                .slug("inactive")
                .description("Should not appear")
                .sortOrder(3)
                .isActive(false)
                .build());

        categoryRepository.save(Category.builder()
                .name("Laptops")
                .slug("laptops")
                .description("Portable computers")
                .sortOrder(1)
                .isActive(true)
                .parentId(electronics.getId())
                .build());
    }

    @Nested
    @DisplayName("GET /categories")
    class GetCategoryTree {

        @Test
        @DisplayName("returns 200 with active root categories in tree form")
        void returnsActiveRootCategories() throws Exception {
            mockMvc.perform(get("/v1/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].name").value("Electronics"))
                    .andExpect(jsonPath("$.data[1].name").value("Clothing"));
        }

        @Test
        @DisplayName("includes children in tree structure")
        void includesChildrenInTree() throws Exception {
            mockMvc.perform(get("/v1/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].children").isArray())
                    .andExpect(jsonPath("$.data[0].children[0].name").value("Laptops"))
                    .andExpect(jsonPath("$.data[1].children").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /categories/{categoryId}")
    class GetCategoryDetail {

        @Test
        @DisplayName("returns 200 with category detail for existing category")
        void returnsCategoryDetail() throws Exception {
            mockMvc.perform(get("/v1/categories/{id}", electronics.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Electronics"))
                    .andExpect(jsonPath("$.data.slug").value("electronics"))
                    .andExpect(jsonPath("$.data.description").value("Electronic devices"))
                    .andExpect(jsonPath("$.data.children").isArray());
        }

        @Test
        @DisplayName("returns 404 for non-existent category")
        void returns404ForMissingCategory() throws Exception {
            mockMvc.perform(get("/v1/categories/{id}", "00000000-0000-0000-0000-000000000000"))
                    .andExpect(status().isNotFound());
        }
    }
}
