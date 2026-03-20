package com.UiUtil.shared.util;

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

    public String qWenVLPlus(MultipartFile file, String text) throws ApiException, NoApiKeyException, UploadFileException {
        try {
            String string = fileToBase64(file);
            MultiModalConversation conv = new MultiModalConversation();
            MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                    .content(Arrays.asList(new HashMap<String, Object>() {{
                                               put("image", string);
                                           }},
                            new HashMap<String, Object>() {{
                                put("text", text);
                            }})).build();
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(resolveApiKey())
                    .model("qwen-vl-plus")
                    .messages(Arrays.asList(userMessage))
                    .build();
            MultiModalConversationResult result = conv.call(param);
            return result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
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
