package com.UiUtil.auth.entity;

/**
 * 系统角色实体，对应 sys_role 表，目前预设 SUPER_ADMIN / SHOP_ADMIN / SHOP_USER 三级角色。
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码，如 ADMIN / USER */
    private String roleCode;

    private String roleName;

    /** 1正常 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableLogic
    private Integer isDeleted;
}
