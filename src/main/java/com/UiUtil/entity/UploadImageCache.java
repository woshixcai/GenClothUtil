package com.UiUtil.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@TableName("upload_image_cache") // 对应数据库表名
public class UploadImageCache {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 图片MD5值（32位唯一标识）
     */
    @TableField("image_md5")
    private String imageMd5;

    /**
     * 火山TOS图片访问URL
     */
    @TableField("tos_url")
    private String tosUrl;

    /**
     * 创建时间（插入时自动填充）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间（插入/更新时自动填充）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * URL过期时间（NULL表示永久有效）
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;
}