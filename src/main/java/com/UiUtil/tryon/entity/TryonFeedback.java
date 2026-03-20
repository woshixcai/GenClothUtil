package com.UiUtil.tryon.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("tryon_feedback")
public class TryonFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private Long userId;
    private String tagCodes;
    private String extraText;
    private Date createdTime;
}
