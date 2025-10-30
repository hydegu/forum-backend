package com.example.forum.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.forum.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenRepo extends BaseMapper<RefreshToken> {
}
