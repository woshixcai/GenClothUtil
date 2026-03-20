package com.UiUtil.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@TableName("cloth_order")
public class ClothOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;
    private String orderNo;

    /** 1=销售  2=退货 */
    private Integer orderType;

    private BigDecimal totalAmount;
    private String remark;

    /** 退货时关联的原销售单 ID */
    private Long origOrderId;

    private Long operatorId;
    private Date createdTime;

    /** 非数据库字段：查询时回填明细 */
    @TableField(exist = false)
    private List<ClothOrderItem> items;

    /** 非数据库字段：操作人用户名 */
    @TableField(exist = false)
    private String operatorName;
}
