package com.UiUtil.auth.mapper;

/**
 * 权限数据访问层，支持按用户 ID 关联查询其拥有的权限列表。
 */
import com.UiUtil.auth.entity.SysPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 根据 userId 联表查询该用户拥有的所有权限。
     * 链路：sys_user_role → sys_role → sys_role_permission → sys_permission
     */
    @Select("SELECT DISTINCT p.* " +
            "FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.perm_id " +
            "INNER JOIN sys_role r  ON rp.role_id = r.id  AND r.status = 1 AND r.is_deleted = 0 " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.is_deleted = 0")
    List<SysPermission> findPermsByUserId(@Param("userId") Long userId);
}
