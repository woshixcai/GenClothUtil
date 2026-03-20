package com.UiUtil.auth.mapper;

import com.UiUtil.auth.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND is_deleted = 0 LIMIT 1")
    SysUser findByUsername(String username);

    @Update("UPDATE sys_user SET total_token_used = total_token_used + #{delta} WHERE id = #{userId}")
    void addTotalToken(@Param("userId") Long userId, @Param("delta") int delta);

    @Update("UPDATE sys_user SET used_today = #{newUsed}, quota_date = #{today} " +
            "WHERE id = #{userId} " +
            "AND (quota_date IS NULL OR quota_date != #{today} OR used_today = #{expectUsed})")
    int tryConsumeQuota(@Param("userId") Long userId,
                        @Param("newUsed") int newUsed,
                        @Param("today") java.sql.Date today,
                        @Param("expectUsed") int expectUsed);
}
