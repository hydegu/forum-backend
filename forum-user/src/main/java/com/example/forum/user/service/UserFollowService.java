package com.example.forum.user.service;

import com.example.forum.user.entity.UserFollow;
import com.example.forum.user.vo.FollowingView;
import com.example.forum.common.vo.PageResponse;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserFollowService  extends IService<UserFollow> {

    boolean follow(Integer followerId, Integer followeeId);

    boolean unfollow(Integer followerId, Integer followeeId);

    boolean isFollowing(Integer followerId, Integer followeeId);

    PageResponse<FollowingView> pageFollowings(Integer followerId, int page, int size, String keyword);
}
