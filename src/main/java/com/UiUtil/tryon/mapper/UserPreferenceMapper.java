package com.UiUtil.tryon.mapper;

/**
 * 用户偏好数据访问层，继承 MyBatis-Plus BaseMapper 提供基础 CRUD 操作。
 */
import com.UiUtil.tryon.entity.UserPreference;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {}
