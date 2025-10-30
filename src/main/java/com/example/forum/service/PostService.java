package com.example.forum.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.forum.dto.PostCreateRequest;
import com.example.forum.entity.Post;
import com.example.forum.vo.AdminPostDetailView;
import com.example.forum.vo.AdminPostSummary;
import com.example.forum.vo.PageResponse;
import com.example.forum.vo.PostDetailView;
import com.example.forum.vo.PostListResponse;
import com.example.forum.vo.PostSummaryView;
import com.example.forum.vo.TrendingPostView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
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
