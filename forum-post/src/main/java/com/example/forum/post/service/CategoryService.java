package com.example.forum.post.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.forum.post.dto.CategoryRequest;
import com.example.forum.post.entity.Category;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.vo.CategoryResponse;

public interface CategoryService extends IService<Category> {

    PageResponse<CategoryResponse> pageCategories(int page, int size);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Integer categoryId, CategoryRequest request);

    CategoryResponse getCategory(Integer categoryId);

    void deleteCategory(Integer categoryId);
}
