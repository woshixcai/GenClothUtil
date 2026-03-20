package com.UiUtil.inventory.mapper;

import com.UiUtil.inventory.entity.ClothOrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClothOrderItemMapper extends BaseMapper<ClothOrderItem> {

    @Select("<script>" +
            "SELECT * FROM cloth_order_item WHERE order_id IN " +
            "<foreach item='id' collection='orderIds' open='(' separator=',' close=')'>" +
            "  #{id}" +
            "</foreach>" +
            "</script>")
    List<ClothOrderItem> selectByOrderIds(@Param("orderIds") List<Long> orderIds);
}
