package com.UiUtil.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("sys_shop")
public class SysShop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shopName;
    private Integer status;
    private Date createTime;
    @TableLogic
    private Integer isDeleted;
}
