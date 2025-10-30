package com.example.forum.service;

import com.example.forum.entity.UserFollow;
import com.example.forum.vo.FollowingView;
import com.example.forum.vo.PageResponse;
import com.baomidou.mybatisplus.extension.service.IService;

import org.springframework.stereotype.Service;

@Service
public interface UserFollowService  extends IService<UserFollow> {

    boolean follow(Integer followerId, Integer followeeId);

    boolean unfollow(Integer followerId, Integer followeeId);

    boolean isFollowing(Integer followerId, Integer followeeId);

    PageResponse<FollowingView> pageFollowings(Integer followerId, int page, int size, String keyword);
}
