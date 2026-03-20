package com.UiUtil.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_interaction")
public class UserInteraction {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 图片唯一ID
     */
    private String imageId;
    /**
     * 图片识别结果
     */
    private String recognizeResult;
    /**
     * 用户修改要求
     */
    private String modifyRequire;
    /**
     * 生成图片的Prompt
     */
    private String generatedPrompt;
    /**
     * 生成图片URL
     */
    private String imageUrl;
    /**
     * 是否满意：1-满意，0-不满意
     */
    private Integer satisfied;
    /**
     * 创建时间
     */
    private Date createTime;
}