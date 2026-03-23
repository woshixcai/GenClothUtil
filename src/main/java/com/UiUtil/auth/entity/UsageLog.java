package com.UiUtil.auth.entity;

/**
 * AI Token 使用日志实体，对应 usage_log 表，记录每次调用大模型的接口类型、消耗 Token 数及所属店铺。
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("usage_log")
public class UsageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shopId;
    private String action;
    private Integer tokenUsed;
    private Date createdTime;
}
