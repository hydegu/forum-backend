package com.example.forum.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.forum.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRepo extends BaseMapper<AppUser> {
}
