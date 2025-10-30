package com.example.forum.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户关注关系列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowingView {
    private Integer id;
    private String username;
    private String avatar;
    private String bio;
    private LocalDateTime followedAt;
}
