package com.UiUtil.inventory.entity;

/**
 * 服装图片实体，对应 cloth_image 表，存储每件商品关联的 TOS 图片 URL。
 */
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
