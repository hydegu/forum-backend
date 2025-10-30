package com.example.forum.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.forum.entity.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostCommentRepo extends BaseMapper<PostComment> {

    @Select("""
            <script>
            SELECT c.*, u.username AS author_name, u.avatar_url AS author_avatar
            FROM post_comments c
            LEFT JOIN users u ON u.id = c.user_id
            WHERE c.post_id = #{postId}
              AND (c.parent_id IS NULL OR c.parent_id = 0)
              AND (c.deleted IS NULL OR c.deleted = 0)
            ORDER BY c.created_at DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    @Results(id = "PostCommentWithAuthor", value = {
            @Result(column = "author_name", property = "authorName"),
            @Result(column = "author_avatar", property = "authorAvatar")
    })
    List<PostComment> selectRootComments(@Param("postId") Integer postId,
                                         @Param("offset") long offset,
                                         @Param("limit") long limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM post_comments
            WHERE post_id = #{postId}
              AND (parent_id IS NULL OR parent_id = 0)
              AND (deleted IS NULL OR deleted = 0)
            </script>
            """)
    long countRootComments(@Param("postId") Integer postId);

    @Select("""
            <script>
            SELECT c.*, u.username AS author_name, u.avatar_url AS author_avatar
            FROM post_comments c
            LEFT JOIN users u ON u.id = c.user_id
            WHERE c.post_id = #{postId}
              AND c.root_id IN
              <foreach collection="rootIds" item="rid" open="(" separator="," close=")">
                #{rid}
              </foreach>
              and c.parent_id IS NOT NULL
              AND (c.deleted IS NULL OR c.deleted = 0)
            ORDER BY c.created_at ASC
            </script>
            """)
    @Results(value = {
            @Result(column = "author_name", property = "authorName"),
            @Result(column = "author_avatar", property = "authorAvatar")
    })
    List<PostComment> selectRepliesByRootIds(@Param("postId") Integer postId,
                                             @Param("rootIds") List<Integer> rootIds);

    @Update("""
            UPDATE post_comments
            SET deleted = 1,
                updated_at = #{updatedAt}
            WHERE post_id = #{postId}
              AND (root_id = #{rootId} OR id = #{rootId})
              AND (deleted IS NULL OR deleted = 0)
            """)
    int softDeleteThread(@Param("postId") Integer postId,
                         @Param("rootId") Integer rootId,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
