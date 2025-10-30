package com.example.forum.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.forum.dto.CategoryRequest;
import com.example.forum.entity.Category;
import com.example.forum.vo.CategoryResponse;
import com.example.forum.vo.PageResponse;
import org.springframework.stereotype.Service;

@Service
public interface CategoryService extends IService<Category> {

    PageResponse<CategoryResponse> pageCategories(int page, int size);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Integer categoryId, CategoryRequest request);

    CategoryResponse getCategory(Integer categoryId);

    void deleteCategory(Integer categoryId);
}
