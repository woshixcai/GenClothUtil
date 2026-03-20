package com.UiUtil.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 加密后的密码，序列化时不输出 */
    @TableField(value = "password")
    private String password;

    private String phone;

    /** 1正常 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer isDeleted;

    /** 所属店铺 ID */
    private Long shopId;

    /** 每日调用配额，-1 表示不限制 */
    private Integer dailyQuota;

    /** 今日已用次数 */
    private Integer usedToday;

    /** 配额计数日期（用于判断是否需要重置） */
    private Date quotaDate;

    /** 是否可查看成本：1=是，0=否 */
    private Integer canSeeCost;

    /** 累计使用 token 数 */
    private Long totalTokenUsed;

    /** 是否可使用 AI 生图：1=是（默认）  0=否 */
    private Integer canUseAi;
}
