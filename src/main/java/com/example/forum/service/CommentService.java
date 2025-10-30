package com.example.forum.service;

import com.example.forum.dto.PostCommentCreateRequest;
import com.example.forum.entity.AppUser;
import com.example.forum.entity.PostComment;
import com.example.forum.vo.CommentTreeNode;
import com.example.forum.vo.PageResponse;
import org.springframework.stereotype.Service;

@Service
public interface CommentService {

    PageResponse<CommentTreeNode> pageComments(Integer postId, int page, int size);

    PostComment addComment(Integer postId, Integer userId, PostCommentCreateRequest request);

    void deleteComment(Integer postId, Integer commentId, AppUser operator);
}
