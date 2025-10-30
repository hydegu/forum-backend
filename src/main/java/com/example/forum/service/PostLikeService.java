package com.example.forum.service;

/**
 * 帖子点赞服务接口
 * 负责处理帖子的点赞、取消点赞和点赞状态查询
 */
public interface PostLikeService {

    /**
     * 点赞帖子
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean likePost(Integer postId, Integer userId);

    /**
     * 取消点赞
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean unlikePost(Integer postId, Integer userId);

    /**
     * 检查用户是否已点赞帖子
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    boolean isPostLikedByUser(Integer postId, Integer userId);
}
