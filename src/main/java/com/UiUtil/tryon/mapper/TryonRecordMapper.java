package com.UiUtil.tryon.mapper;

/**
 * 换装记录数据访问层，继承 MyBatis-Plus BaseMapper 提供基础 CRUD 操作。
 */
import com.UiUtil.tryon.entity.TryonRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TryonRecordMapper extends BaseMapper<TryonRecord> {}
