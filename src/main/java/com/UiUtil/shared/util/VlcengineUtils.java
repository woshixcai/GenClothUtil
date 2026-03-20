package com.UiUtil.shared.util;

import com.UiUtil.shared.result.HuoShanResult;
import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImageGenStreamEvent;
import com.volcengine.ark.runtime.model.images.generation.ResponseFormat;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 火山引擎生图核心服务
 */
@Service
public class VlcengineUtils {

    /** 配置文件兜底值，优先级低于环境变量 */
    @Value("${volc.ark.api-key:}")
    private String apiKeyConfig;

    @Value("${volc.ark.model-name:doubao-seedream-5-0-260128}")
    private String modelName;

    /**
     * 生成试衣图片核心方法
     */
    public HuoShanResult generateTryonImage(String demoImage, String closeImage, String prompt) throws Exception {
        HuoShanResult result = new HuoShanResult();
        List<String> imageUrls = new ArrayList<>();
        AtomicLong tokenUsage = new AtomicLong(0L);

        String resolvedApiKey = resolve("VOLC_ARK_API_KEY", apiKeyConfig, "火山ARK API Key（volc.ark.api-key）");

        ConnectionPool connectionPool = new ConnectionPool(10, 3, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .apiKey(resolvedApiKey)
                .build();

        GenerateImagesRequest.SequentialImageGenerationOptions sequentialOptions =
                new GenerateImagesRequest.SequentialImageGenerationOptions();
        sequentialOptions.setMaxImages(1);

        GenerateImagesRequest generateRequest = GenerateImagesRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .image(closeImage)
                .image(demoImage)
                .responseFormat(ResponseFormat.Url)
                .size("2560x1440")
                .sequentialImageGeneration("auto")
                .sequentialImageGenerationOptions(sequentialOptions)
                .stream(true)
                .watermark(false)
                .build();

        service.streamGenerateImages(generateRequest)
                .doOnError(Throwable::printStackTrace)
                .blockingForEach(choice -> {
                    if (choice == null) return;

                    if ("image_generation.partial_failed".equals(choice.getType())) {
                        if (choice.getError() != null) {
                            String errorMsg = "生图失败：" + choice.getError().getMessage();
                            if ("InternalServiceError".equals(choice.getError().getCode())) {
                                throw new RuntimeException(errorMsg);
                            }
                        }
                    }

                    if ("image_generation.partial_succeeded".equals(choice.getType())) {
                        if (choice.getUrl() != null && !choice.getUrl().isEmpty()) {
                            imageUrls.add(choice.getUrl());
                        }
                    }

                    if ("image_generation.completed".equals(choice.getType())) {
                        ImageGenStreamEvent.Usage usage = choice.getUsage();
                        if (usage != null) {
                            if (usage.getTotalTokens() > 0) {
                                tokenUsage.set(usage.getTotalTokens());
                            } else if (usage.getOutputTokens() > 0) {
                                tokenUsage.set(usage.getOutputTokens());
                            }
                            System.out.println("本次生成图片数量：" + usage.getGeneratedImages());
                        }
                    }
                });

        service.shutdownExecutor();

        if (imageUrls.isEmpty()) {
            throw new RuntimeException("未生成任何图片");
        }
        result.setSuccess(true);
        result.setImageUrls(imageUrls);
        result.setTokenUsage(tokenUsage.get());
        return result;
    }

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
}
