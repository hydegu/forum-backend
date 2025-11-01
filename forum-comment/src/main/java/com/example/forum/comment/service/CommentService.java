package com.example.forum.comment.service;

import com.example.forum.comment.dto.PostCommentCreateRequest;
import com.example.forum.comment.entity.PostComment;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.comment.vo.CommentTreeNode;

public interface CommentService {

    PageResponse<CommentTreeNode> pageComments(Integer postId, int page, int size);

    PostComment addComment(Integer postId, Integer userId, PostCommentCreateRequest request);

    void deleteComment(Integer postId, Integer commentId, Integer operatorId, String operatorRole);
}
