package com.UiUtil.shared.util;

/**
 * 阿里云 DashScope 视觉语言大模型工具类，封装 qwen-vl-plus 多模态接口调用，
 * 支持图片识别并返回文本内容及 Token 消耗量（通过反射兼容不同 SDK 版本）。
 */
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

@Component
public class AliyunUtils {

    static {
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
    }

    /** 配置文件兜底值，优先级低于环境变量 */
    @Value("${aliyun.dashscope.api-key:}")
    private String dashscopeApiKeyConfig;

    /**
     * 图像识别，使用 qwen2-vl-7b-instruct（比 qwen-vl-plus 快约 3-5 倍）。
     * 建议前端传 512px 以内的压缩图，进一步提速。
     */
    public static class QwenTextUsageResult {
        private final String text;
        private final Integer tokenUsed;

        public QwenTextUsageResult(String text, Integer tokenUsed) {
            this.text = text;
            this.tokenUsed = tokenUsed;
        }

        public String getText() { return text; }
        public Integer getTokenUsed() { return tokenUsed; }
    }

    public String qWenVLPlus(MultipartFile file, String text) throws ApiException, NoApiKeyException, UploadFileException {
        return qWenVLPlusWithUsage(file, text).getText();
    }

    /**
     * 带 token 用量的多模态调用结果：
     * - text：模型输出文本
     * - tokenUsed：尝试从 result.usage 中提取 totalTokens/outputTokens
     *
     * 注意：dashscope 返回结构可能会随 SDK 版本变化，因此 tokenUsed 的读取使用反射做兼容。
     */
    public QwenTextUsageResult qWenVLPlusWithUsage(MultipartFile file, String text) throws ApiException, NoApiKeyException, UploadFileException {
        try {
            String b64 = fileToBase64(file);
            MultiModalConversation conv = new MultiModalConversation();
            MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                    .content(Arrays.asList(
                            new HashMap<String, Object>() {{ put("image", b64); }},
                            new HashMap<String, Object>() {{ put("text",  text); }}
                    )).build();
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(resolveApiKey())
                    .model("qwen-vl-plus")
                    .messages(Arrays.asList(userMessage))
                    .maxTokens(200)
                    .build();
            MultiModalConversationResult result = conv.call(param);

            String outText = result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();

            Integer tokenUsed = 0;
            try {
                java.lang.reflect.Method usageMethod = result.getClass().getMethod("getUsage");
                Object usageObj = usageMethod.invoke(result);
                if (usageObj != null) {
                    tokenUsed = extractIntFromUsage(usageObj, "getTotalTokens", "getOutputTokens", "getInputTokens");
                }
            } catch (Exception ignore) {
                // SDK 字段可能变化：兜底 tokenUsed=0
            }

            return new QwenTextUsageResult(outText, tokenUsed);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static Integer extractIntFromUsage(Object usageObj, String... methodNames) {
        if (usageObj == null || methodNames == null) return 0;
        for (String mn : methodNames) {
            try {
                java.lang.reflect.Method m = usageObj.getClass().getMethod(mn);
                Object val = m.invoke(usageObj);
                if (val instanceof Number) {
                    long l = ((Number) val).longValue();
                    if (l > 0) return (int) l;
                }
            } catch (Exception ignore) {
                // try next method
            }
        }
        return 0;
    }

    /**
     * Key 解析优先级：环境变量 → 配置文件 → 抛异常
     */
    private static String resolve(String envName, String configVal, String desc) {
        String env = System.getenv(envName);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        if (configVal != null && !configVal.trim().isEmpty()) {
            return configVal.trim();
        }
        throw new RuntimeException(
                "缺少配置：" + desc + "。请设置环境变量 " + envName + " 或在 application.yml 中配置。");
    }

    private String resolveApiKey() {
        return resolve("DASHSCOPE_API_KEY", dashscopeApiKeyConfig,
                "阿里云DashScope API Key（aliyun.dashscope.api-key）");
    }

    private static String fileToBase64(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("上传的文件为空，请检查！");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("上传的文件不是图片类型，请上传 jpg/png/webp 格式！");
        }
        String imageFormat = contentType.split("/")[1];
        byte[] bytes = file.getBytes();
        return String.format("data:image/%s;base64,%s",
                imageFormat,
                Base64.encodeBase64String(bytes));
    }
}
