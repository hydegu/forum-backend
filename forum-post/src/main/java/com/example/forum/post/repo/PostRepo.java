package com.example.forum.post.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.forum.post.entity.Post;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PostRepo extends BaseMapper<Post> {
    @Select("""
            <script>
                SELECT
                  p.id, p.title, p.subtitle, p.author_id, p.status, p.heat,
                  p.images, p.created_at, p.updated_at,
                  p.view_count, p.like_count, p.comment_count, p.pinned, p.category_id,
                  c.name AS category_name
                FROM posts p
                LEFT JOIN post_categories c ON c.id = p.category_id
                <where>
                  <if test="authorId != null">
                    AND p.author_id = #{authorId}
                  </if>
                  <if test="status != null and status != ''">
                    AND p.status = #{status}
                  </if>
                  <if test="q != null and q != ''">
                    AND (
                      p.title LIKE CONCAT('%', #{q}, '%')
                      OR p.subtitle LIKE CONCAT('%', #{q}, '%')
                    )
                  </if>
                  <if test="categoryId != null">
                    AND p.category_id = #{categoryId}
                  </if>
                </where>
                ORDER BY p.pinned DESC, p.created_at DESC, p.id DESC
              </script>
            """)
    @Results(id = "PostSummaryMap", value = {
            @Result(column = "images", property = "images",
                    typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    })
    Page<Post> selectPageSummaryWithAuthor(
            Page<Post> page,
            @Param("authorId") Integer authorId,
            @Param("status") String status,
            @Param("q") String q,
            @Param("categoryId") Integer categoryId);

    @Select("""
            <script>
                SELECT
                  p.*,
                  c.name AS category_name
                FROM posts p
                LEFT JOIN post_categories c ON c.id = p.category_id
                <where>
                  <if test="authorId != null">
                    AND p.author_id = #{authorId}
                  </if>
                  <if test="status != null and status != ''">
                    AND p.status = #{status}
                  </if>
                  <if test="q != null and q != ''">
                    AND (
                      p.title LIKE CONCAT('%', #{q}, '%')
                      OR p.subtitle LIKE CONCAT('%', #{q}, '%')
                      OR p.content LIKE CONCAT('%', #{q}, '%')
                    )
                  </if>
                  <if test="categoryId != null">
                    AND p.category_id = #{categoryId}
                  </if>
                </where>
                ORDER BY p.pinned DESC, p.created_at DESC, p.id DESC
              </script>
            """)
    @Results(id = "PostWithImagesMap", value = {
            @Result(column = "images", property = "images",
                    typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    })
    Page<Post> selectPageWithAuthorAndCategory(
            Page<Post> page,
            @Param("authorId") Integer authorId,
            @Param("status") String status,
            @Param("q") String q,
            @Param("categoryId") Integer categoryId);

    @Select("""
            SELECT 
              p.*,
              c.name AS category_name
            FROM posts p
            LEFT JOIN post_categories c ON c.id = p.category_id
            WHERE p.id = #{postId}
            LIMIT 1
            """)
    @ResultMap("PostWithImagesMap")
    Post selectByIdWithAuthor(@Param("postId") Integer postId);

    @Update("""
            UPDATE posts
            SET comment_count = GREATEST(COALESCE(comment_count, 0) + #{delta}, 0)
            WHERE id = #{postId}
            """)
    int updateCommentCount(@Param("postId") Integer postId,
                           @Param("delta") int delta);

    @Update("""
            UPDATE posts
            SET
                view_count = GREATEST(COALESCE(view_count, 0) + #{viewsDelta}, 0),
                like_count = GREATEST(COALESCE(like_count, 0) + #{likesDelta}, 0),
                comment_count = GREATEST(COALESCE(comment_count, 0) + #{commentsDelta}, 0)
            WHERE id = #{postId}
            """)
    int incrementMetrics(@Param("postId") Integer postId,
                        @Param("viewsDelta") int viewsDelta,
                        @Param("likesDelta") int likesDelta,
                        @Param("commentsDelta") int commentsDelta);

}
