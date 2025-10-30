package com.example.forum.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostListResponse {
    /**
     * 当前页的帖子记录
     */
    private List<PostSummaryView> records;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 附加信息（可用于筛选、统计等）
     */
    private Map<String, Object> extra;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 是否为最后一页
     */
    private Boolean end;
}
