package com.UiUtil.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("cloth_order_item")
public class ClothOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long itemId;
    private Long skuId;

    /** 快照：商品名 */
    private String itemName;

    /** 快照：SKU 规格描述（颜色/尺码） */
    private String skuDesc;

    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal costPrice;
}
