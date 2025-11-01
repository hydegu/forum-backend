package com.example.forum.post.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.forum.post.dto.PostCreateRequest;
import com.example.forum.post.entity.Post;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.vo.AdminPostDetailView;
import com.example.forum.post.vo.AdminPostSummary;
import com.example.forum.post.vo.PostDetailView;
import com.example.forum.post.vo.PostListResponse;
import com.example.forum.post.vo.PostSummaryView;
import com.example.forum.post.vo.TrendingPostView;

import java.util.List;

public interface PostService extends IService<Post> {
    Page<Post> pageWithAuthorName(Integer current, Integer size, String status, String q, Integer categoryId);
    PostListResponse pageWithSummary(Integer current,
                                     Integer pageSize,
                                     Wrapper<Post> wrapper,
                                     String status,
                                     String q,
                                     Integer categoryId,
                                     Integer currentUserId);

    List<TrendingPostView> getTrendingPostViews(int limit);

    PostDetailView getPostDetail(Integer postId, Integer currentUserId);

    PageResponse<PostSummaryView> pageMyPosts(Integer userId, String status, int page, int size);

    Post createPost(PostCreateRequest request, Integer authorId);

    boolean likePost(Integer postId, Integer userId);

    boolean unlikePost(Integer postId, Integer userId);

    boolean isPostLikedByUser(Integer postId, Integer userId);

    PageResponse<AdminPostSummary> pageAdminPosts(String status,
                                                  int page,
                                                  int size,
                                                  String keyword,
                                                  Integer categoryId);

    AdminPostSummary approvePost(Integer postId);

    AdminPostSummary rejectPost(Integer postId);

    AdminPostDetailView getAdminPost(Integer postId, boolean includeComments);

    void deletePostAsAdmin(Integer postId);
}
