package com.UiUtil.auth.mapper;

import com.UiUtil.auth.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND is_deleted = 0 LIMIT 1")
    SysUser findByUsername(String username);
}
