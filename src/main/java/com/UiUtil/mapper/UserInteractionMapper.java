package com.UiUtil.mapper;

/**
 * 用户交互记录数据访问层，支持按换装记录 ID 和交互类型（like/dislike）查询。
 */
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
