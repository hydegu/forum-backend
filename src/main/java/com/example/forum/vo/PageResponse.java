package com.example.forum.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 通用分页响应，兼容多种字段命名。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> records;
    private Long total;
    private Integer page;
    private Integer size;
    private Long totalPages;
    private Map<String, Object> extra;

    public static <T> PageResponse<T> of(List<T> records,
                                         long total,
                                         int page,
                                         int size,
                                         long totalPages,
                                         Map<String, Object> extra) {
        return new PageResponse<>(
                records,
                total,
                page,
                size,
                totalPages,
                extra
        );
    }
}
