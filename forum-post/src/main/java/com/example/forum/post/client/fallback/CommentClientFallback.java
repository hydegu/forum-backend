package com.example.forum.post.client.fallback;

import com.example.forum.common.dto.Result;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.client.CommentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 评论服务 Feign 客户端降级处理
 * 当评论服务不可用时，返回空评论列表，不影响帖子核心功能
 */
@Slf4j
@Component
public class CommentClientFallback implements CommentClient {

    @Override
    public Result<PageResponse<Object>> getComments(Integer postId, Integer page, Integer size) {
        log.warn("评论服务调用失败，触发降级逻辑 - getComments: postId={}, page={}, size={}", postId, page, size);

        // 返回空评论列表，让帖子可以正常显示
        PageResponse<Object> emptyPage = new PageResponse<>();
        emptyPage.setRecords(Collections.emptyList());
        emptyPage.setTotal(0L);
        emptyPage.setPage(page);
        emptyPage.setSize(size);
        emptyPage.setTotalPages(0L);

        return Result.success(emptyPage);
    }
}