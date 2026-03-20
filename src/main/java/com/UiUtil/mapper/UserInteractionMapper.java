package com.UiUtil.mapper;

import com.UiUtil.entity.UserInteraction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserInteractionMapper extends BaseMapper<UserInteraction> {
    /**
     * 查询用户最近N条交互记录
     */
    @Select("SELECT * FROM user_interaction WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{limit}")
    List<UserInteraction> selectRecentByUserId(String userId, Integer limit);
}
