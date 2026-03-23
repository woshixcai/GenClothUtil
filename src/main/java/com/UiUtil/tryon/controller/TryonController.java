package com.UiUtil.tryon.controller;

/**
 * 换装接口：提交生图任务（异步，返回 taskId）、轮询任务状态、提交用户反馈，
 * 以及查询当前用户的换装历史记录。
 */
import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.shared.result.ApiResult;
import com.UiUtil.tryon.service.FeedbackService;
import com.UiUtil.tryon.service.TryonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/tryon")
public class TryonController {

    @Autowired TryonService tryonService;
    @Autowired FeedbackService feedbackService;

    /** 提交换装任务，立即返回 taskId，无需等待生成完成 */
    @RequirePermission("image:recommend")
    @PostMapping("/submit")
    public ApiResult<Map<String, Object>> submit(
            @RequestParam(required = false) List<MultipartFile> clothesFiles,
            @RequestParam(required = false) List<MultipartFile> referenceFiles,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String season) throws Exception {
        Map<String, Object> result = tryonService.submitAsync(clothesFiles, referenceFiles, style, scene, season);
        if (result.containsKey("error")) {
            return ApiResult.fail((String) result.get("error"));
        }
        return ApiResult.ok(result);
    }

    /** 轮询换装结果，返回 status: pending / done / failed */
    @RequirePermission("image:recommend")
    @GetMapping("/result/{taskId}")
    public ApiResult<Map<String, Object>> result(@PathVariable Long taskId) {
        return ApiResult.ok(tryonService.getResult(taskId));
    }

    /** 保留旧接口兼容性（同步版，不推荐） */
    @RequirePermission("image:recommend")
    @PostMapping("/recommend")
    public ApiResult<Map<String, Object>> recommend(
            @RequestParam(required = false) List<MultipartFile> clothesFiles,
            @RequestParam(required = false) List<MultipartFile> referenceFiles,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String season) throws Exception {
        // 复用 submit + 阻塞等待结果（最多 60s）
        Map<String, Object> sub = tryonService.submitAsync(clothesFiles, referenceFiles, style, scene, season);
        if (sub.containsKey("error")) return ApiResult.fail((String) sub.get("error"));
        Long taskId = ((Number) sub.get("taskId")).longValue();
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);
            Map<String, Object> r = tryonService.getResult(taskId);
            if (!"pending".equals(r.get("status"))) return ApiResult.ok(r);
        }
        return ApiResult.fail("生成超时，请稍后重试");
    }

    @RequirePermission("tryon:feedback")
    @PostMapping("/feedback")
    public ApiResult<Void> feedback(@RequestParam Long recordId,
                                     @RequestParam(required = false) List<String> tagCodes,
                                     @RequestParam(required = false) String extraText) {
        feedbackService.submitFeedback(recordId, tagCodes, extraText);
        return ApiResult.ok();
    }
}
