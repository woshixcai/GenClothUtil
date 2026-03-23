package com.UiUtil.entity;

/**
 * 用户与生图记录的交互实体，对应 user_interaction 表，
 * 记录用户对换装结果的点赞、不喜欢等行为，用于偏好学习数据来源。
 */
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