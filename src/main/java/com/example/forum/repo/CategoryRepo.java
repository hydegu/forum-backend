package com.example.forum.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.forum.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryRepo extends BaseMapper<Category> {
}
