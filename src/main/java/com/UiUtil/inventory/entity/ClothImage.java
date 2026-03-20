package com.UiUtil.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("cloth_image")
public class ClothImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itemId;
    private String tosUrl;
    private Integer isMain;
    private Integer sortOrder;
    private Date createdTime;
}
