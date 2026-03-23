package com.UiUtil.auth.mapper;

/**
 * 用户-角色关联数据访问层，继承 MyBatis-Plus BaseMapper 提供基础 CRUD 操作。
 */
import com.UiUtil.auth.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {}
