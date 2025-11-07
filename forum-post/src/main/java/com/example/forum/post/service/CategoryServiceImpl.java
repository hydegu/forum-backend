package com.example.forum.post.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.forum.post.dto.CategoryRequest;
import com.example.forum.post.entity.Category;
import com.example.forum.common.exception.ApiException;
import com.example.forum.post.repo.CategoryRepo;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.vo.CategoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryRepo, Category> implements CategoryService {

    @Override
    public PageResponse<CategoryResponse> pageCategories(int page, int size) {
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<Category> pageResult = this.page(
                Page.of(current, pageSize),
                Wrappers.<Category>lambdaQuery()
                        .orderByAsc(Category::getName)
                        .orderByAsc(Category::getId)
        );
        List<CategoryResponse> records = pageResult.getRecords()
                .stream()
                .map(this::toCategoryResponse)
                .toList();
        return PageResponse.of(
                records,
                pageResult.getTotal(),
                current,
                pageSize,
                pageResult.getPages(),
                Collections.emptyMap()
        );
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setPostCount(0L);
        LocalDateTime now = LocalDateTime.now();
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        boolean saved = this.save(category);
        if (!saved) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create category");
        }
        Category persisted = this.getById(category.getId());
        return toCategoryResponse(Objects.requireNonNullElse(persisted, category));
    }

    @Override
    public CategoryResponse updateCategory(Integer categoryId, CategoryRequest request) {
        Category existing = this.getById(categoryId);
        if (existing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Category not found");
        }
        existing.setName(request.getName());
        existing.setUpdatedAt(LocalDateTime.now());
        boolean updated = this.updateById(existing);
        if (!updated) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update category");
        }
        Category refreshed = this.getById(categoryId);
        return toCategoryResponse(Objects.requireNonNullElse(refreshed, existing));
    }

    @Override
    public CategoryResponse getCategory(Integer categoryId) {
        Category category = this.getById(categoryId);
        if (category == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Category not found");
        }
        return toCategoryResponse(category);
    }

    @Override
    public void deleteCategory(Integer categoryId) {
        Category category = this.getById(categoryId);
        if (category == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Category not found");
        }
        if (category.getPostCount() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot delete category with related posts");
        }
        boolean removed = this.removeById(categoryId);
        if (!removed) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete category");
        }
    }

    private CategoryResponse toCategoryResponse(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getPostCount(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
