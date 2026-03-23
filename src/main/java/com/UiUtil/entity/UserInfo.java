package com.UiUtil.entity;

/**
 * 用户基础信息实体，对应 user_info 表，存储用户的扩展信息（如头像、昵称等）。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("user_info")
public class UserInfo {
    /**
     * 用户唯一ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String userId;
    /**
     * 登录手机号
     */
    private String phone;
    /**
     * 默认穿搭风格
     */
    private String defaultStyle;
    /**
     * 避坑配饰
     */
    private String avoidAccessories;
    /**
     * 偏好颜色
     */
    private String favoriteColor;
    /**
     * 创建时间
     */
    private Date createTime;
}
