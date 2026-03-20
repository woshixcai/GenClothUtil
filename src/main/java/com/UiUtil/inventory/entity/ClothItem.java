package com.UiUtil.inventory.entity;

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
}
