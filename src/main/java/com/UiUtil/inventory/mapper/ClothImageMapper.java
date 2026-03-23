package com.UiUtil.inventory.mapper;

/**
 * 服装图片数据访问层，支持按商品 ID 批量查询图片记录。
 */
import com.UiUtil.inventory.entity.ClothImage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ClothImageMapper extends BaseMapper<ClothImage> {

    /** 批量查询主图（is_main=1），用于列表展示 */
    @Select("<script>SELECT item_id, tos_url FROM cloth_image WHERE is_main=1 AND item_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " ORDER BY sort_order ASC</script>")
    List<ClothImage> selectMainByItemIds(@org.apache.ibatis.annotations.Param("ids") Collection<Long> ids);
}
