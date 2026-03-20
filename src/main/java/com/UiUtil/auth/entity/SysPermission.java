package com.UiUtil.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码，如 image:generate */
    private String permCode;

    private String permName;

    /** 接口路径，如 /TestController/** */
    private String url;

    /** HTTP 方法，NULL 表示不限 */
    private String method;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableLogic
    private Integer isDeleted;
}
