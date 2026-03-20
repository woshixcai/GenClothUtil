package com.UiUtil.uitl;

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

    /*static {Constants.baseHttpApiUrl="https://dashscope-intl.aliyuncs.com/api/v1";}*/

    /*static {
        Constants.baseHttpApiUrl="https://dashscope.aliyuncs.com";}*/
    static {
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
    }

    @Value("${aliyun.dashscope.api-key:}")
    private String dashscopeApiKey;

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
                    .model("qwen-vl-plus")  // 此处以qwen3.5-plus为例，可按需更换模型名称。模型列表：https://help.aliyun.com/zh/model-studio/models
                    .messages(Arrays.asList(userMessage))
                    .build();
            MultiModalConversationResult result = conv.call(param);
            return result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private String resolveApiKey() {
        if (dashscopeApiKey != null && !dashscopeApiKey.isBlank()) {
            return dashscopeApiKey.trim();
        }
        String env = System.getenv("DASHSCOPE_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        throw new RuntimeException("缺少DashScope API Key，请配置 aliyun.dashscope.api-key 或设置环境变量 DASHSCOPE_API_KEY");
    }

    private static String fileToBase64(MultipartFile file) throws IOException {
        // 1. 空值校验（避免空指针）
        if (file == null || file.isEmpty()) {
            throw new IOException("上传的文件为空，请检查！");
        }

        // 2. 获取文件ContentType，自动适配图片格式（jpeg/png/webp等）
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("上传的文件不是图片类型，请上传 jpg/png/webp 格式！");
        }
        // 提取图片格式（如 image/jpeg → jpeg）
        String imageFormat = contentType.split("/")[1];

        // 3. 读取 MultipartFile 字节流（核心修正：用 getInputStream() 而非 FileInputStream）
        byte[] bytes = file.getBytes();

        // 4. 拼接带格式前缀的 Base64 字符串（qwen-vl 必须带前缀）
        return String.format("data:image/%s;base64,%s",
                imageFormat,
                Base64.encodeBase64String(bytes));
    }
}
