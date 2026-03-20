package com.UiUtil.tryon.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("tryon_record")
public class TryonRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shopId;
    private String resultUrls;
    private String promptUsed;
    private String prefSnapshot;
    private String style;
    private String scene;
    private String season;
    private Integer tokenUsed;
    private Integer status;
    private Date createdTime;
    @TableLogic
    private Integer isDeleted;
}
