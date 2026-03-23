package com.UiUtil.inventory.entity;

/**
 * 服装商品主体实体，对应 cloth_item 表，包含编号、名称、分类、进价、售价及所属店铺等核心字段。
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cloth_item")
public class ClothItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String itemNo;       // 商品编号，格式：yyyyMMddHHmmss + 3位序号
    private String itemName;
    private String category;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private String description;
    private Integer status;
    private Long createdBy;
    private Date createdTime;
    private Date updatedTime;
    @TableLogic
    private Integer isDeleted;

    /** 主图 URL，查询时动态填充，非数据库字段 */
    @TableField(exist = false)
    private String mainImageUrl;

    /** 主 SKU（id 最小），用于列表批量加库存、开单预填，非数据库字段 */
    @TableField(exist = false)
    private Long primarySkuId;

    /** 该商品下所有 SKU 库存合计，非数据库字段 */
    @TableField(exist = false)
    private Integer stockQty;
}
