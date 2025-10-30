package com.example.forum.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.forum.entity.UserFollow;
import com.example.forum.vo.FollowingView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface UserFollowRepo extends BaseMapper<UserFollow> {

    @Select("""
            <script>
            SELECT
              uf.id,
              uf.follower_id,
              uf.followee_id,
              uf.created_at,
              u.id   AS target_id,
              u.username,
              u.avatar_url,
              u.bio
            FROM user_follows uf
            JOIN users u ON u.id = uf.followee_id
            WHERE uf.follower_id = #{followerId}
            <if test="q != null and q != ''">
              AND (
                u.username LIKE CONCAT('%', #{q}, '%')
                OR IFNULL(u.bio, '') LIKE CONCAT('%', #{q}, '%')
              )
            </if>
            ORDER BY uf.created_at DESC
            </script>
            """)
    @Results(id = "FollowingViewMap", value = {
            @Result(column = "target_id", property = "id"),
            @Result(column = "username", property = "username"),
            @Result(column = "avatar_url", property = "avatar"),
            @Result(column = "bio", property = "bio"),
            @Result(column = "created_at", property = "followedAt")
    })
    Page<FollowingView> pageFollowings(Page<FollowingView> page,
                                       @Param("followerId") Integer followerId,
                                       @Param("q") String q);

    @Select("""
            SELECT * FROM user_follows
            WHERE follower_id = #{followerId}
              AND followee_id = #{followeeId}
            LIMIT 1
            """)
    UserFollow findRelation(@Param("followerId") Integer followerId,
                            @Param("followeeId") Integer followeeId);

    @Select("""
            <script>
            SELECT followee_id
            FROM user_follows
            WHERE follower_id = #{followerId}
              AND followee_id IN
              <foreach collection="followeeIds" item="followeeId" separator="," open="(" close=")">
                #{followeeId}
              </foreach>
            </script>
            """)
    List<Integer> findFolloweeIds(@Param("followerId") Integer followerId,
                                  @Param("followeeIds") Collection<Integer> followeeIds);
}
