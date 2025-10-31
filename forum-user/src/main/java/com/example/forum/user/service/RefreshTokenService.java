package com.example.forum.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.forum.user.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService extends IService<RefreshToken> {

    RefreshToken createRefreshToken(Integer userId, String username);

    Optional<RefreshToken> findValidToken(String token);

    void revokeToken(String token);

    void revokeTokensByUser(Integer userId);
}
