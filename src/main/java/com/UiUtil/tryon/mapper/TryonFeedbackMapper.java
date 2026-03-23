package com.UiUtil.tryon.mapper;

/**
 * 换装反馈数据访问层，继承 MyBatis-Plus BaseMapper 提供基础 CRUD 操作。
 */
import com.UiUtil.tryon.entity.TryonFeedback;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TryonFeedbackMapper extends BaseMapper<TryonFeedback> {}
