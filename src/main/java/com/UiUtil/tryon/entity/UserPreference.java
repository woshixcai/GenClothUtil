package com.UiUtil.tryon.entity;

/**
 * 用户偏好实体，对应 user_preference 表，以 JSON 格式存储用户不喜欢的风格标签，
 * 供下次生图时构建个性化负面提示词。
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user_preference")
public class UserPreference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tagWeights;
    private Date updatedTime;
}
