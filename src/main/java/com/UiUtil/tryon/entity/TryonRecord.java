package com.UiUtil.tryon.entity;

/**
 * 换装生图记录实体，对应 tryon_record 表，存储任务状态（pending/done/failed）、
 * 生成图片 URL 及本次消耗的 Token 数量。
 */
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
    /** 0=失败  1=成功  2=生成中/排队 */
    private Integer status;
    private Date createdTime;
    /** 以下三字段由异步任务回填 */
    private String uploadSecond;
    private String generateSecond;
    private String totalSecond;
    @TableLogic
    private Integer isDeleted;
}
