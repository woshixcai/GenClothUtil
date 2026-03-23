package com.UiUtil.inventory.entity;

/**
 * 销售订单明细实体，对应 cloth_order_item 表，记录每个 SKU 的数量、单价及所属店铺。
 */
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

    /** 所属店铺ID（用于订单明细按店铺查询/统计） */
    private Long shopId;

    /** 快照：商品名 */
    private String itemName;

    /** 快照：SKU 规格描述（颜色/尺码） */
    private String skuDesc;

    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal costPrice;
}
