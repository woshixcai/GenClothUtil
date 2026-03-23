package com.UiUtil.auth.entity;

/**
 * 角色-权限关联实体，对应 sys_role_permission 表，维护角色与权限的多对多关系。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long permId;
}
