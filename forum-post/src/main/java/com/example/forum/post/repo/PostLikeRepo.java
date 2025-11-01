package com.example.forum.post.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.forum.post.entity.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PostLikeRepo extends BaseMapper<PostLike> {

    @Select("""
            SELECT * FROM post_likes
            WHERE post_id = #{postId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    PostLike findByPostAndUser(@Param("postId") Integer postId,
                               @Param("userId") Integer userId);

    @Select("""
            <script>
            SELECT post_id
            FROM post_likes
            WHERE user_id = #{userId}
              AND post_id IN
              <foreach collection="postIds" item="postId" separator="," open="(" close=")">
                #{postId}
              </foreach>
            </script>
            """)
    List<Integer> findLikedPostIds(@Param("userId") Integer userId,
                                   @Param("postIds") Collection<Integer> postIds);
}
