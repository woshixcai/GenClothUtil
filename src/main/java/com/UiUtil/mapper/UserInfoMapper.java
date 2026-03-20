package com.UiUtil.mapper;

import com.UiUtil.entity.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
    /**
     * 根据用户ID查询
     */
    @Select("SELECT * FROM user_info WHERE user_id = #{userId}")
    UserInfo selectByUserId(String userId);
}