package com.example.forum.post.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.forum.post.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryRepo extends BaseMapper<Category> {
}
