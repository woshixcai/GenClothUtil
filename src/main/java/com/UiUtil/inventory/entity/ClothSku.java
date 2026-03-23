package com.UiUtil.inventory.entity;

/**
 * 服装 SKU 实体，对应 cloth_sku 表，记录每个商品的颜色、尺码及当前库存数量。
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("cloth_sku")
public class ClothSku {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itemId;
    private String color;
    private String size;
    private Integer stockQty;
    private Date updatedTime;
}
