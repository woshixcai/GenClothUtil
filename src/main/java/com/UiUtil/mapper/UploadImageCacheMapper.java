package com.UiUtil.mapper;

/**
 * 图片上传缓存数据访问层，继承 MyBatis-Plus BaseMapper 提供基础 CRUD 操作。
 */
import com.UiUtil.entity.UploadImageCache;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadImageCacheMapper extends BaseMapper<UploadImageCache> {
}
