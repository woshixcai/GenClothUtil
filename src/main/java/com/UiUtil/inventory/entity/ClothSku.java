package com.UiUtil.inventory.entity;

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
