package com.UiUtil.inventory.mapper;

/**
 * 服装 SKU 数据访问层，继承 MyBatis-Plus BaseMapper 提供基础 CRUD 操作。
 */
import com.UiUtil.inventory.entity.ClothSku;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClothSkuMapper extends BaseMapper<ClothSku> {}
