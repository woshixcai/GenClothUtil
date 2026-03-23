package com.UiUtil.shared.result;

/**
 * 火山引擎 ARK 图片生成结果封装类，包含任务 ID、生成图片 URL 列表及 Token 消耗量。
 */
import lombok.Data;

import java.util.List;

@Data
public class HuoShanResult {

    // 总耗时（秒）：上传 + 生图的完整链路
    private Double totalSecond;
    // 图片上传TOS耗时（秒）
    private Double uploadSecond;
    // 调用大模型生图耗时（秒）
    private Double generateSecond;
    // 生成的图片URL列表
    private List<String> imageUrls;
    // Token消耗数量
    private Long tokenUsage;
    // 错误信息（成功时为null）
    private String errorMsg;
    // 是否成功
    private boolean success;

    /** 将毫秒数转为保留1位小数的秒数 */
    public static double toSeconds(long millis) {
        return Math.round(millis / 1000.0 * 10) / 10.0;
    }
}
