package com.example.forum.post.controller;

import com.example.forum.post.dto.CategoryRequest;
import com.example.forum.post.service.CategoryService;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.vo.CategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public PageResponse<CategoryResponse> pageCategories(@RequestParam(defaultValue = "1") Integer page,
                                                         @RequestParam(defaultValue = "10") Integer size) {
        log.info("查询分类列表 page={}, size={}", page, size);
        return categoryService.pageCategories(page, size);
    }

    @GetMapping("/{categoryId}")
    public CategoryResponse getCategory(@PathVariable Integer categoryId) {
        log.info("获取分类详情 id={}", categoryId);
        return categoryService.getCategory(categoryId);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        log.info("创建分类 name={}", request.getName());
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Integer categoryId,
                                                           @Valid @RequestBody CategoryRequest request) {
        log.info("更新分类 id={}", categoryId);
        CategoryResponse response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Integer categoryId) {
        log.info("删除分类 id={}", categoryId);
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}
