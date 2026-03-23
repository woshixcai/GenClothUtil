package com.UiUtil.tryon.entity;

/**
 * 用户换装反馈实体，对应 tryon_feedback 表，记录用户对生图结果不满意的原因标签（如"模特太假"）。
 */
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
