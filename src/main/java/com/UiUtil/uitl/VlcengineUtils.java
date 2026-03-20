package com.UiUtil.uitl;

import com.UiUtil.Result.HuoShanResult;
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

    @Value("${volc.ark.api-key:}")
    private String apiKey;

    @Value("${volc.ark.model-name:doubao-seedream-5-0-260128}")
    private String modelName;

    /**
     * 生成试衣图片核心方法
     * @param demoImage 样例图片
     * @param closeImage 服装图片
     * @param prompt 生图提示词
     * @return 生成的图片URL列表
     * @throws Exception 生图异常
     */
    public HuoShanResult generateTryonImage(String demoImage,String closeImage, String prompt) throws Exception {
        HuoShanResult result = new HuoShanResult();
        List<String> imageUrls = new ArrayList<>();
        AtomicLong tokenUsage = new AtomicLong(0L);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("缺少火山ARK API Key，请配置 volc.ark.api-key 或设置环境变量");
        }

        // 1. 初始化连接池和ArkService
        ConnectionPool connectionPool = new ConnectionPool(10, 3, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .apiKey(apiKey.trim())
                .build();

        // 2. 配置批量生成参数
        GenerateImagesRequest.SequentialImageGenerationOptions sequentialOptions =
                new GenerateImagesRequest.SequentialImageGenerationOptions();
        sequentialOptions.setMaxImages(1); // 试衣场景默认生成1张

        // 3. 构建生图请求
        GenerateImagesRequest generateRequest = GenerateImagesRequest.builder()
                .model(modelName)
                .prompt(prompt)
                //.image(demoImage+","+closeImage)
                .image(closeImage)
                .image(demoImage)
                .responseFormat(ResponseFormat.Url)
                .size("2560x1440") // 适配试衣图比例
                .sequentialImageGeneration("auto")
                .sequentialImageGenerationOptions(sequentialOptions)
                .stream(true)
                .watermark(false) // 商用关闭水印
                .build();

        // 4. 流式调用生图接口
        service.streamGenerateImages(generateRequest)
                .doOnError(Throwable::printStackTrace)
                .blockingForEach(choice -> {
                    if (choice == null) return;

                    // 处理部分失败
                    if ("image_generation.partial_failed".equals(choice.getType())) {
                        if (choice.getError() != null) {
                            String errorMsg = "生图失败：" + choice.getError().getMessage();
                            if ("InternalServiceError".equals(choice.getError().getCode())) {
                                throw new RuntimeException(errorMsg);
                            }
                        }
                    }

                    // 收集成功生成的图片URL
                    if ("image_generation.partial_succeeded".equals(choice.getType())) {
                        if (choice.getUrl() != null && !choice.getUrl().isEmpty()) {
                            imageUrls.add(choice.getUrl());
                        }
                    }

                    // 统计Token消耗（适配你提供的Usage类）
                    if ("image_generation.completed".equals(choice.getType())) {
                        ImageGenStreamEvent.Usage usage = choice.getUsage(); // 改为实际的Usage类
                        if (usage != null) {
                            // 优先取totalTokens，无则取outputTokens（兜底）
                            if (usage.getTotalTokens() > 0) {
                                tokenUsage.set(usage.getTotalTokens());
                            } else if (usage.getOutputTokens() > 0) {
                                tokenUsage.set(usage.getOutputTokens());
                            }
                            // 可选：记录生成的图片数量
                            System.out.println("本次生成图片数量：" + usage.getGeneratedImages());
                        }
                    }
                });

        // 5. 关闭资源
        service.shutdownExecutor();

        if (imageUrls.isEmpty()) {
            throw new RuntimeException("未生成任何图片");
        }
        result.setSuccess(true);
        result.setImageUrls(imageUrls);
        result.setTokenUsage(tokenUsage.get());
        return result;
    }


}