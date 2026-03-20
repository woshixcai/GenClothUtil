package com.UiUtil.Result;

import lombok.Data;

import java.util.List;

@Data
public class HuoShanResult {

    //总请求时长秒
    private Double second;
    // 生成的图片URL列表
    private List<String> imageUrls;
    // Token消耗数量
    private Long tokenUsage;
    // 错误信息（成功时为null）
    private String errorMsg;
    // 是否成功
    private boolean success;
}
