package com.UiUtil.shared.annotation;

import java.lang.annotation.*;

/**
 * 标注在 Controller 方法上，声明该接口需要的权限编码。
 * 拦截器会对比当前用户的权限列表，无权限则返回 403。
 *
 * 用法：@RequirePermission("image:generate")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /** 权限编码，对应 sys_permission.perm_code */
    String value();
}
